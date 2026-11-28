package com.aitronics.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.*

class WakePanel(context: Context) : LinearLayout(context) {
    private fun dp(value: Int) = (resources.displayMetrics.density * value).roundToInt()
    private val stateLabel = TextView(context)
    init {
        orientation=HORIZONTAL
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(10),dp(11),dp(18),dp(11))
        val border=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xff7ff8df.toInt(),0xff6295fd.toInt(),0xffbd8fff.toInt())).apply { cornerRadius=dp(25).toFloat() }
        val fill=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xff10282d.toInt(),0xff191e34.toInt(),0xff262038.toInt())).apply { cornerRadius=dp(24).toFloat() }
        background=LayerDrawable(arrayOf(border,fill)).apply { setLayerInset(1,dp(2),dp(2),dp(2),dp(2)) }
        addView(NeonOrb(context).apply { awake=true }, LayoutParams(dp(58),dp(58)))
        val words=LinearLayout(context).apply {
            orientation=VERTICAL
            setPadding(dp(4),0,dp(6),0)
        }
        words.addView(TextView(context).apply {
            text="AITRONICS"; textSize=11f; letterSpacing=.16f
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            setTextColor(0xff94e9db.toInt())
        })
        words.addView(stateLabel.apply {
            textSize=20f; setTextColor(Color.WHITE)
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        })
        addView(words,LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        addView(Wave(context),LayoutParams(dp(31),dp(30)))
        showState(true)
    }
    fun showState(listening: Boolean) {
        val label=if(listening) "Listening…" else "Command received"
        if(stateLabel.text!=label) stateLabel.text=label
        contentDescription="AITRONICS. $label"
    }
    private class Wave(context: Context):View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND }
        private val colors=intArrayOf(0xff7cf6dc.toInt(),0xff79dbee.toInt(),0xff78baff.toInt(),0xff9996ff.toInt(),0xffba92fa.toInt())
        override fun onDraw(canvas: Canvas) {
            val phase=(SystemClock.uptimeMillis()%1200)/1200.0*2*PI
            for(i in colors.indices) {
                val x=width*(i+.5f)/colors.size
                val half=height*(.09f+.14f*abs(sin(phase+i*.8)).toFloat()+.19f*NeonBubble.voiceLevel)
                paint.strokeWidth=width*.1f; paint.color=colors[i]
                canvas.drawLine(x,height/2f-half,x,height/2f+half,paint)
            }
            if(isShown) postInvalidateDelayed(50)
        }
    }
}
