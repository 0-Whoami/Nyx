package com.termux.utils.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.LinearLayout
import com.termux.utils.data.EXTRA_BLUR_BACKGROUND
import com.termux.utils.data.EXTRA_NORMAL_BACKGROUND
import java.io.File


class blur(context: Context, attributeSet: AttributeSet?) : LinearLayout(context, attributeSet) {
    private val enable = File(EXTRA_NORMAL_BACKGROUND).exists()
    private val location by lazy { IntArray(2) }
    private val blurBitmap by lazy {
        Drawable.createFromPath(EXTRA_BLUR_BACKGROUND)?.apply { setBounds(0, 0, 450, 450) }
    }

    private fun updateBlurBackground(c: Canvas) {
        if (!enable) return
        getLocationOnScreen(location)
        c.save()
        c.translate((-location[0]).toFloat(), (-location[1]).toFloat())
        blurBitmap?.draw(c)
        c.restore()
    }

    override fun isOpaque(): Boolean = true
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (MotionEvent.ACTION_SCROLL == event.action &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            var delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL).toInt()
            delta = if (delta > 0)
                width + 10
            else
                width - 10
            layoutParams = LayoutParams(delta, delta)
        }
        return true
    }


    override fun draw(canvas: Canvas) {
        updateBlurBackground(canvas)
        super.draw(canvas)
    }

}
