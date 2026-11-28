package com.aitronics.assistant

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

internal object PrivateContent {
    private val secretLabel = Regex("""\b(?:otp|pin|password|passcode|cvv|cvc|verification code|security code|one[ -]time (?:password|code))\b""", RegexOption.IGNORE_CASE)
    private val code = Regex("""(?<!\d)\d{4,8}(?!\d)""")
    fun restricted(text: CharSequence?): Boolean = text != null && secretLabel.containsMatchIn(text)
    fun omitPreview(text: String): Boolean = restricted(text) || code.containsMatchIn(text)
    fun protectedNode(node: AccessibilityNodeInfo): Boolean =
        node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive) ||
            restricted(node.hintText) || restricted(node.viewIdResourceName?.substringAfterLast('/')?.replace('_', ' '))
}
