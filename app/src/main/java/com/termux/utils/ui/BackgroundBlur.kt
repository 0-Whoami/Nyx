package com.termux.utils.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.LinearLayout
import com.termux.utils.data.TermuxConstants
import java.io.File

class BackgroundBlur(context: Context, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet) {
    private val enable =
        File(TermuxConstants.EXTRA_NORMAL_BACKGROUND).exists()

    private fun updateBlurBackground(c: Canvas) {
        if (!enable) return
        val location = IntArray(2)
        getLocationOnScreen(location)
        val blurBitmap =
            Drawable.createFromPath(TermuxConstants.EXTRA_BLUR_BACKGROUND)
                ?.apply { setBounds(0, 0, 450, 450) }
        c.save()
        c.translate((-location[0]).toFloat(), (-location[1]).toFloat())
        blurBitmap?.draw(c)
        c.restore()
    }

    override fun draw(canvas: Canvas) {
        updateBlurBackground(canvas)
        super.draw(canvas)
    }

}
