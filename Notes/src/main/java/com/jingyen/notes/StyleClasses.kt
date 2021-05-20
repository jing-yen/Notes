package com.jingyen.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.LeadingMarginSpan

class BoldSpan: StyleSpan(1)
class ItalicSpan: StyleSpan(2)
class RealUnderlineSpan: UnderlineSpan()
class ListSpan(private val leadWidth: Int, private val gapWidth: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int { return leadWidth + gapWidth }
    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence?, start: Int, end: Int, first: Boolean, l: Layout?) {
        if (first) {
            val orgStyle: Paint.Style = p.style
            p.style = Paint.Style.FILL
            val width: Float = p.measureText("\u2022 ")
            c.drawText("\u2022 ", (leadWidth + x - width / 2) * dir, baseline.toFloat(), p)
            p.style = orgStyle
        }
    }
}