package com.jingyen.notes

import android.text.Editable
import android.text.Spannable
import android.text.style.StrikethroughSpan

class Spans {
    fun check(editable: Editable, start: Int, end: Int): TextStyle {
        val textstyle = TextStyle(bold = false, italic = false, underline = false, strikethrough = false, list = false)
        val spans: Array<Any> = editable.getSpans(start, end, Any::class.java)
        spans.forEach { span ->
            if (editable.getSpanStart(span)<=start && editable.getSpanEnd(span)>=end) {
                when (span) {
                    is BoldSpan -> textstyle.bold = true
                    is ItalicSpan -> textstyle.italic = true
                    is RealUnderlineSpan -> textstyle.underline = true
                    is StrikethroughSpan -> textstyle.strikethrough = true
                    is ListSpan -> textstyle.list = true
                }
            }
        }
        return textstyle
    }

    fun apply(editable: Editable, start: Int, end: Int, type: Int, bool: Boolean) {
        val styleClass: Any = when (type) {
            1 -> BoldSpan()
            2 -> ItalicSpan()
            3 -> RealUnderlineSpan()
            4 -> StrikethroughSpan()
            else -> ListSpan(15, 25)
        }
        if (bool) {
            var newStart = start
            var newEnd = end
            var selStart = start
            var selEnd = end
            if (type==5) {
                while (newStart>0) if (editable[--newStart]=='\n') {newStart++; break}
                if (!(newStart<editable.length && editable[newStart]!='\ufeff')) { editable.insert(newStart, "\ufeff"); newEnd++; selStart++; selEnd++ }
                while (newEnd<editable.length-1) { if (editable[newEnd]=='\n') break; newEnd++ }
                val existingListSpan = editable.getSpans(newStart, newEnd, ListSpan::class.java)
                for (listSpan in existingListSpan) editable.removeSpan(listSpan)
            } else {
                val spans = editable.getSpans(if (start>0) start-1 else start, if (end<editable.length) end+1 else end, styleClass::class.java)
                for (span in spans) {
                    if (editable.getSpanStart(span)<newStart) newStart = editable.getSpanStart(span)
                    if (editable.getSpanEnd(span)>newEnd) newEnd = editable.getSpanEnd(span)
                    editable.removeSpan(span)
                }
            }
            editable.setSpan(styleClass, newStart, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            val spans = editable.getSpans(start, end, styleClass::class.java)
            for (span in spans) {
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                editable.removeSpan(span)
                if (type!=5) {
                    if (spanStart < start) editable.setSpan(styleClass, spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (spanEnd > end) editable.setSpan(styleClass, end, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    if (editable[spanStart]=='\ufeff') editable.delete(spanStart, spanStart+1)
                }
            }
        }
    }
}