package com.aitronics.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo as Node

object ScreenCommands {
    private val messaging = setOf("com.whatsapp","com.whatsapp.w4b","org.telegram.messenger","org.telegram.messenger.web")
    private val namedMessage = Regex("""(?:write (?:a )?(?:message|massage) to|message) (.+?) saying (.+)""",RegexOption.IGNORE_CASE)
    private val openChat = Regex("""open (?:chat with|conversation with) (.+)""",RegexOption.IGNORE_CASE)
    private val draft = Regex("""(?:write|type|draft|dictate) (?:a |the )?(?:message|massage)(?: saying| that says)?[ :,]+(.+)""",RegexOption.IGNORE_CASE)
    private val scopedScroll = Regex("""scroll (down|up) in (WhatsApp|Telegram)""", RegexOption.IGNORE_CASE)
    private val closeApp = Regex("""(?:close|exit|leave) (?:the |this )?(.+)""",RegexOption.IGNORE_CASE)
    private data class SendReview(val pkg: String,val title: String,val text: String,val expiry: Long)
    private var review: SendReview? = null

    fun handles(text: String): Boolean {
        val recipient = namedMessage.matchEntire(text)?.groupValues?.get(1)
        val namedChat = recipient != null && !recipient.matches(Regex("""[+0-9 ()-]+"""))
        return text.lowercase() in setOf("scroll up","scroll down","read draft","send message","confirm send","cancel send") ||
            scopedScroll.matches(text) || closeApp.matches(text) || namedChat || draft.matches(text) || openChat.matches(text)
    }
    fun followUp(text: String) = handles(text) && text.lowercase() !in setOf("send message","confirm send")
    private fun nodes(root: Node): List<Node> {
        val result = mutableListOf<Node>()
        val queue = ArrayDeque<Node>(); queue.add(root)
        while(queue.isNotEmpty() && result.size < 600) {
            val n = queue.removeFirst()
            if(PrivateContent.protectedNode(n)) continue
            if(n.isVisibleToUser) result.add(n)
            for(i in 0 until n.childCount) if(queue.size < 600) n.getChild(i)?.let(queue::addLast)
        }
        return result
    }
    private fun id(n: Node) = n.viewIdResourceName.orEmpty().substringAfterLast('/')
    private fun title(nodes: List<Node>, pkg: String): String? {
        val ids = if(pkg.startsWith("com.whatsapp")) setOf("conversation_contact_name","conversation_contact_name_text") else setOf("action_bar_title","chat_title")
        return nodes.filter { id(it) in ids }.mapNotNull { it.text?.toString() }.filter { it.isNotBlank() }.distinct().singleOrNull()
    }
    fun run(service: AssistantAccessibility, command: String): String {
        val key = command.lowercase()
        if (key == "cancel send") { review = null; return "Sending cancelled." }
        val root = service.rootInActiveWindow ?: return "No accessible app is visible."
        val pkg = root.packageName?.toString().orEmpty()
        val locked = service.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (locked.isDeviceLocked) { review = null; return "Unlock your phone first." }
        val close = closeApp.matchEntire(command)
        if (close != null) {
            val name = close.groupValues[1].trim()
            val pm = service.packageManager
            val matched = name.equals("app",true) || name.equals("current app",true) ||
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString().equals(name,true) }.getOrDefault(false) ||
                (name.equals("camera",true) && pm.queryIntentActivities(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),0).any { it.activityInfo.packageName == pkg })
            if (!matched) return "$name is not the visible app."
            review = null
            return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) "Returned to Home." else "Android could not leave the app."
        }
        if (key.startsWith("scroll ")) {
            val target = scopedScroll.matchEntire(command)?.groupValues?.get(2)
            if (target != null) {
                val correct = pkg in messaging && (target.equals("WhatsApp",true) == pkg.startsWith("com.whatsapp"))
                if(!correct) return "Open $target first."
            }
            return if(service.scroll(key.startsWith("scroll down"))) "Scrolled." else "No accessible scrolling target."
        }
        if(pkg !in messaging) return "Open WhatsApp or Telegram first."
        val all = nodes(root)
        val chat = openChat.matchEntire(command)
        if (chat != null) {
            review = null
            val target = chat.groupValues[1].trim()
            if (all.any { it.isEditable && id(it) in setOf("entry","message_edit_text","chat_input") }) return "Go to the chat list first."
            val matches = all.filter { it.text?.toString()?.equals(target,true) == true }
            if(matches.size != 1) return "Choose the chat named $target on screen."
            var row: Node? = matches.single()
            repeat(4) {
                val current = row ?: return@repeat
                if(current.isClickable && current.isEnabled && current.performAction(Node.ACTION_CLICK)) return "Opened $target."
                row = current.parent
            }
            return "Tap $target to open that chat."
        }
        val editor = all.filter { it.isEditable && it.isEnabled && !it.isPassword &&
            (id(it) in setOf("entry","message_edit_text","chat_input") || (pkg.startsWith("org.telegram") &&
                listOf(it.hintText,it.contentDescription,it.text).any { label -> label?.toString()?.equals("Message",true)==true })) }.singleOrNull()
            ?: return "Open a conversation with a visible message field. If your app hides that field, use its native composer."
        val body = editor.text?.toString().orEmpty().takeUnless { editor.isShowingHintText }.orEmpty()
        val named = namedMessage.matchEntire(command)
        if (named != null && title(all,pkg)?.equals(named.groupValues[1],true) != true)
            return "Open the chat with ${named.groupValues[1]} and verify the recipient, then say write message followed by the text."
        val dictation = named?.groupValues?.get(2) ?: draft.matchEntire(command)?.groupValues?.get(1)
        if(dictation != null) {
            review = null
            if(body.isNotBlank()) return "A draft already exists. Read or clear it before dictating another."
            val args = Bundle().apply { putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,dictation) }
            return if(editor.performAction(Node.ACTION_SET_TEXT,args)) "Draft entered. You can now read the draft." else "The app declined dictation."
        }
        if(body.isBlank()) { review=null; return "The draft is empty." }
        if(key == "read draft") return body
        val recipient = title(all,pkg) ?: return "Review the visible recipient and tap Send in the messaging app."
        if(key == "send message") {
            review = SendReview(pkg,recipient,body,SystemClock.elapsedRealtime()+45000)
            return "Send to $recipient? Draft: $body. Use the confirm send command to approve."
        }
        val approved = review; review=null
        if(approved == null || approved.pkg != pkg || approved.title != recipient || approved.text != body || SystemClock.elapsedRealtime() > approved.expiry)
            return "Please review the current draft with the send message command first."
        val send = all.filter { it.isClickable && it.isEnabled && (id(it) in setOf("send","send_button") || it.contentDescription?.toString()?.equals("Send",true)==true) }.singleOrNull()
        return if(send?.performAction(Node.ACTION_CLICK)==true) "Pressed Send in the chat." else "Tap the messaging app’s Send button."
    }
}
