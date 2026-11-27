package com.aitronics.assistant
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
class AssistantAccessibility : AccessibilityService() {
    companion object {
        var active: AssistantAccessibility? = null
            private set
    }
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if(active === this) active = null; super.onDestroy() }
}
