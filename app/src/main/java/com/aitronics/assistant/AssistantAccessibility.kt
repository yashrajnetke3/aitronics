package com.aitronics.assistant
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class AssistantAccessibility : AccessibilityService() {
    companion object {
        var active: AssistantAccessibility? = null
            private set
    }
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if(active === this) active = null; super.onDestroy() }
    fun screenText(): String {
        val root = rootInActiveWindow ?: return "No accessible screen."
        val output = mutableListOf<String>()
        var visited = 0
        var restrictedScreen = false
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (restrictedScreen || depth > 30 || visited++ >= 500 || !node.isVisibleToUser) return
            if (PrivateContent.protectedNode(node)) { restrictedScreen = true; return }
            val label = node.text?.toString() ?: node.contentDescription?.toString()
            if (PrivateContent.restricted(label)) { restrictedScreen = true; return }
            if (!label.isNullOrBlank() && !PrivateContent.omitPreview(label)) output.add(label.take(200))
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it,depth+1) }
        }
        walk(root,0)
        if (restrictedScreen) return "Sensitive screen. Use the open app directly."
        return output.distinct().joinToString("\n").take(3000).ifBlank { "No readable screen text." }
    }
    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        var count = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
            if(depth > 25 || count++ > 400 || !n.isVisibleToUser || n.isPassword) return false
            if(n.isScrollable && n.performAction(if(forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
            for(i in 0 until n.childCount) if(n.getChild(i)?.let { walk(it,depth+1) } == true) return true
            return false
        }
        return walk(root,0)
    }
}

