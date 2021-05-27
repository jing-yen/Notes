package com.jingyen.notes

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

class SuperEditText : AppCompatEditText {
    var activity: Activity? = null
    var notesActivity: NotesActivity? = null
    private var textChanged = false
    private var textIncreased = false

    constructor(context: Context?) : super(context!!) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {}

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        textChanged = true
        if (lengthAfter>lengthBefore) textIncreased = true
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (!textChanged) notesActivity?.checkSelection(selStart, selEnd)
        if (!textIncreased) notesActivity?.checkStyle(selStart, selEnd)
        textChanged = false
        textIncreased = false
        super.onSelectionChanged(selStart, selEnd)
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (activity != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
            val state = keyDispatcherState
            if (state != null) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    state.startTracking(event, this)
                    return true
                } else if (event.action == KeyEvent.ACTION_UP && !event.isCanceled && state.isTracking(event)) {
                    activity!!.onBackPressed()
                    return true
                }
            }
        }
        return super.dispatchKeyEventPreIme(event)
    }
}