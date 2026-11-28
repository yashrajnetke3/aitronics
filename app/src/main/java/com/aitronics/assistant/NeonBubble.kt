package com.aitronics.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.*
import kotlin.math.*

/** The hero and the floating indicator use the same vector artwork at different sizes. */
class NeonOrb(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Path()
    private val turquoise = Color.rgb(108,255,224)
    private val blue = Color.rgb(72,155,255)
    private val violet = Color.rgb(154,111,255)
    private fun radial(x: Float,y: Float,r: Float,vararg colors: Int) = RadialGradient(x,y,r,colors,null,Shader.TileMode.CLAMP)
    private val halo = radial(0f,0f,1f,0x654b6fff,0x294784af,Color.TRANSPARENT)
    private val core = radial(-.30f,-.38f,1.65f,0xff97ffe4.toInt(),0xff2875b4.toInt(),0xff473286.toInt(),0xff101321.toInt())
    private val ribbon = LinearGradient(-.8f,-.5f,.8f,.6f,intArrayOf(turquoise,blue,violet,turquoise),null,Shader.TileMode.CLAMP)
    private val shine = radial(-.29f,-.45f,.57f,0xb8efffff.toInt(),0x4059edff,Color.TRANSPARENT)
    var awake = false
        set(value) { if(field != value) { field = value; invalidate() } }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = if(awake) (SystemClock.uptimeMillis()%3600L)/3600.0*2*PI else 0.0
        canvas.save()
        canvas.translate(width/2f,height/2f)
        val radius = min(width,height)*.48f
        canvas.scale(radius,radius)
        fun disc(shader: Shader,r: Float) {
            paint.style=Paint.Style.FILL; paint.shader=shader; paint.alpha=255
            canvas.drawCircle(0f,0f,r,paint)
        }
        disc(halo,1f)
        val scale = (.96+.035*sin(t)).toFloat(); canvas.scale(scale,scale)
        disc(core,.77f)
        canvas.save()
        line.reset(); line.addCircle(0f,0f,.77f,Path.Direction.CW); canvas.clipPath(line)
        paint.shader=ribbon; paint.style=Paint.Style.STROKE
        for(i in 0..26) {
            val a=(i-13)/13f
            line.reset()
            for(k in 0..70) {
                val x=k/35f-1f
                val y=(a*.60 + .25*sin(x*3.4 + a*2.1+t*.22) + .16*cos(x*4.2-a*1.7)).toFloat()
                if(k==0) line.moveTo(x,y) else line.lineTo(x,y)
            }
            paint.alpha=(205-abs(i-13)*7); paint.strokeWidth=if(i%5==0) .024f else .009f
            canvas.drawPath(line,paint)
        }
        disc(shine,.77f); canvas.restore()
        paint.shader=ribbon; paint.style=Paint.Style.STROKE; paint.strokeWidth=.017f; paint.alpha=200
        canvas.drawCircle(0f,0f,.77f,paint)
        canvas.restore()
        if(awake && isShown) postInvalidateDelayed(33)
    }
}

object NeonBubble {
    var enabled by mutableStateOf(true); private set
    var pinned by mutableStateOf(true); private set
    var awake by mutableStateOf(false); private set
    private var inApp = false
    private var context: Context? = null
    var listening by mutableStateOf(false); private set
    @Volatile var voiceLevel = 0f; private set
    @Volatile var measuring = false; private set
    fun meter(samples: ShortArray,count: Int) {
        if(!measuring) return
        var sum = 0.0
        for(i in 0 until count step 4) { val v=samples[i].toDouble(); sum+=v*v }
        voiceLevel=(sqrt(sum/((count+3)/4))/4200.0).toFloat().coerceIn(0f,1f)
    }
    private var overlay: View? = null
    val overOtherApps: Boolean get() = overlay?.isAttachedToWindow == true
    val hasWakePanel: Boolean get() = overlay is WakePanel
    private val handler = Handler(Looper.getMainLooper())
    private val settle = Runnable { awake=false; measuring=false; voiceLevel=0f; refresh() }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) { refresh() }
    }
    fun load(c: Context) {
        enabled=BackgroundWake.prefs(c).getBoolean("neonBubble",true)
        pinned=BackgroundWake.prefs(c).getBoolean("pinNeonBubble",true)
    }
    fun configure(c: Context,show: Boolean,pin: Boolean) {
        enabled=show; pinned=pin
        BackgroundWake.prefs(c).edit().putBoolean("neonBubble",show).putBoolean("pinNeonBubble",pin).apply()
        if(!show) { handler.removeCallbacks(settle); awake=false; measuring=false; voiceLevel=0f }
        refresh()
    }
    fun foreground(value: Boolean) { inApp=value; refresh() }
    fun attach(c: Context) {
        if(context!=null) return
        context=c.applicationContext; load(c)
        c.applicationContext.registerReceiver(screen,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        })
        refresh()
    }
    fun wake(duration: Long = 10000L) {
        if(!enabled) return
        listening=true; measuring=true; awake=true
        handler.removeCallbacks(settle); handler.postDelayed(settle,duration); refresh()
    }
    fun received() {
        if(!awake) return
        listening=false; measuring=false; voiceLevel=0f
        handler.removeCallbacks(settle); handler.postDelayed(settle,1600); refresh()
    }
    fun detach() {
        handler.removeCallbacks(settle); awake=false; measuring=false; voiceLevel=0f
        context?.let { remove(it); it.unregisterReceiver(screen) }; context=null
    }
    private fun remove(c: Context) {
        overlay?.let { runCatching { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(it) } }; overlay=null
    }
    private fun refresh() {
        val c=context ?: return
        val locked=(c.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
        val interactive=(c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
        if(inApp || !enabled || (!awake && !pinned) || locked || !interactive || !Settings.canDrawOverlays(c)) { remove(c); return }
        val existing=overlay
        if(existing is WakePanel && awake) { existing.showState(listening); return }
        if(existing is NeonOrb && !awake) return
        remove(c)
        val dm=c.resources.displayMetrics
        fun dp(v:Int)=(dm.density*v).roundToInt()
        val width=if(awake) min(dp(360),dm.widthPixels-dp(24)).coerceAtLeast(1) else dp(62)
        val height=if(awake) WindowManager.LayoutParams.WRAP_CONTENT else dp(62)
        val params=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
                gravity=if(awake) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.END
                x=if(awake) 0 else dp(6); y=if(awake) dp(24) else dp(105); alpha=.78f
            }
        val view=if(awake) WakePanel(c).apply { showState(listening) } else NeonOrb(c)
        runCatching { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view,params) }.onSuccess { overlay=view }

    }
}
