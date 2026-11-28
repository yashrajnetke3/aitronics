package com.aitronics.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.*

class SystemAssistant : VoiceInteractionService() {
    companion object { var active: SystemAssistant? = null; private set }
    override fun onReady() { super.onReady(); active = this; BackgroundWake.resumeIfEnabled(this) }
    override fun onShutdown() { if (active === this) active = null; super.onShutdown() }
}
class AssistantSessions : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = Session(this)
    private class Session(val app: Context) : VoiceInteractionSession(app) {
        override fun onShow(args: Bundle?, flags: Int) {
            super.onShow(args, flags)
            val locked = (app.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceLocked
            if (!locked) runCatching {
                startVoiceActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("wakeTicket",args?.getString("wakeTicket")))
            }
            hide()
        }
    }
}
