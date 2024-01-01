package com.termux.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Environment
import android.util.AttributeSet
import android.view.ViewPropertyAnimator
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toDrawable
import java.io.File

class BackgroundBlur(context: Context, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet) {
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBlurBackground()
    }

    private fun updateBlurBackground() {
        val file =
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/wallpaperBlur.jpeg")
        if (file.exists()) {
            val location = IntArray(2)
            getLocationOnScreen(location)
            val width = width / 2
            val height = height / 2
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply {
                strokeWidth = 1.5f
                color = Color.WHITE
            }
            val path = Path().apply {
                addRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(), 7.5f, 7.5f, Path.Direction.CW
                )
            }
            val canvas = Canvas(bitmap)
            canvas.clipPath(path)


            val out = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
            canvas.save()
            canvas.translate(-location[0].toFloat() / 2, -location[1].toFloat() / 2)
            canvas.drawBitmap(out, 0f, 0f, null)
            canvas.restore()
            out.recycle()

            paint.style = Paint.Style.STROKE
            canvas.drawPath(path, paint)
            background = bitmap.toDrawable(resources)
        }
    }

    override fun animate(): ViewPropertyAnimator {
        updateBlurBackground()
        return super.animate()

    }
}
