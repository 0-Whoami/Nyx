package com.termux.shared.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.LinearLayout
import com.termux.shared.termux.TermuxConstants
import java.io.File
import kotlin.math.sqrt

class BackgroundBlur(context: Context, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet) {
    private val enable =
        File(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_NORMAL_BACKGROUND).exists()
    private var notResized = true
    private var wid = 0
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (notResized) {
            notResized = false
            wid = (w / sqrt(2f)).toInt()
            x = (w - wid) / 2f
            y = x
            layoutParams.width = wid
            layoutParams.height = wid
        }
    }

    private fun updateBlurBackground(c: Canvas) {
        if (!enable) return
        val location = IntArray(2)
        getLocationOnScreen(location)
        val blurBitmap =
            Drawable.createFromPath(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_BLUR_BACKGROUND)
                ?.apply { setBounds(0, 0, 450, 450) }
        c.save()
        c.translate((-location[0]).toFloat(), (-location[1]).toFloat())
        blurBitmap?.draw(c)
        c.restore()
//        val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
//        val paint = Paint().apply {
//            strokeWidth = 1.5f
//            color = Color.WHITE
//        }
//        val path = Path().apply {
//            addRoundRect(
//                0f,
//                0f,
//                measuredWidth.toFloat(),
//                measuredHeight.toFloat(),
//                15f,
//                15f,
//                Path.Direction.CW
//            )
//        }
//        val canvas = Canvas(bitmap)
//        canvas.clipPath(path)
//
//
//
//        paint.style = Paint.Style.STROKE
//        canvas.drawPath(path, paint)
//        // background = BitmapDrawable(resources, bitmap)
    }

    override fun draw(canvas: Canvas) {
        updateBlurBackground(canvas)
        super.draw(canvas)
    }

}
