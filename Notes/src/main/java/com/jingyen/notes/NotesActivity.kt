package com.jingyen.notes

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.jingyen.notes.databinding.ActivityNotesBinding
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

class NotesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotesBinding
    private lateinit var imm: InputMethodManager
    private var showKeyboard = false
    private var id = 0
    private var note = DecodedNote(0, Meta(1, Clock.System.now().toEpochMilliseconds(), Clock.System.now().toEpochMilliseconds(), 1, false), "", "", emptyList())
    private var color = MutableLiveData(1)
    var textstyle = MutableLiveData(TextStyle(bold = false, italic = false, underline = false, strikethrough = false, list = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        id = intent.getIntExtra("id", 0)
        if (id!=0) { CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            note = withContext(Dispatchers.IO) { Backend.get(id) }
            color.value = note.meta.color
            binding.title.setText(note.title)
            binding.text.setText(note.text)
            note.spansData.forEach { span ->
                (binding.text.text as Spannable).setSpan(when (span.spanType) {
                    1 -> BoldSpan(); 2 -> ItalicSpan(); 3 -> RealUnderlineSpan(); 4 -> StrikethroughSpan(); else -> ListSpan(15, 25) },
                    span.start, span.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        } } else {
            showKeyboard = true
            id = Backend.highestId()
        }

        binding.text.notesActivity = this
        binding.root.post {
            binding.color.layoutParams.height = binding.red.width
            binding.pickcolor.setOnClickListener { binding.body.animate().translationY(if (binding.body.translationY==0f) binding.color.height.toFloat() else 0f).duration = 150 }
            binding.showime.setOnClickListener { binding.imebar.visibility = View.VISIBLE; binding.showime.visibility = View.GONE }
            binding.hideime.setOnClickListener { binding.imebar.visibility = View.GONE; binding.showime.visibility = View.VISIBLE }
            binding.blank.setOnClickListener {
                binding.text.requestFocus()
                binding.text.setSelection(binding.text.text!!.length)
                imm.showSoftInput(binding.text, InputMethodManager.SHOW_IMPLICIT) }
            textstyle.observe(this, { value ->
                binding.bold.setBackgroundColor(if (value.bold) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.italic.setBackgroundColor(if (value.italic) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.underline.setBackgroundColor(if (value.underline) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.strikethrough.setBackgroundColor(if (value.strikethrough) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.bullet.setBackgroundColor(if (value.list) Color.parseColor("#19000000") else Color.TRANSPARENT) })
            color.observe(this, { value ->
                val colorCode = when (value) {
                    0 -> Pair(R.color.redBackground, R.color.redBackgroundDark)
                    1 -> Pair(R.color.yellowBackground, R.color.yellowBackgroundDark)
                    2 -> Pair(R.color.greenBackground, R.color.greenBackgroundDark)
                    3 ->  Pair(R.color.blueBackground, R.color.blueBackgroundDark)
                    4 ->  Pair(R.color.purpleBackground, R.color.purpleBackgroundDark)
                    else ->  Pair(R.color.greyBackground, R.color.greyBackgroundDark) }
                binding.mainplus.setBackgroundColor(resources.getColor(colorCode.second, this.theme))
                binding.parent.setBackgroundColor(resources.getColor(colorCode.first, this.theme))
                binding.main.setBackgroundColor(resources.getColor(colorCode.first, this.theme))
                binding.body.setBackgroundColor(resources.getColor(colorCode.first, this.theme))
                window.navigationBarColor = resources.getColor(colorCode.first, this.theme) })
        }
        binding.text.onFocusChangeListener = View.OnFocusChangeListener { _, b -> binding.imebar.visibility = if (b) View.VISIBLE else View.GONE; if (!b) binding.showime.visibility = View.GONE }
        binding.title.addTextChangedListener(CustomTextWatcher())
        binding.text.addTextChangedListener(object: CustomTextWatcher() {
            private lateinit var string: String
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { string = s?.toString() ?: "" }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (start+count>start+before) {
                    Spans().apply(s as Editable, start+before, start+count, 1, textstyle.value!!.bold)
                    Spans().apply(s, start+before, start+count, 2, textstyle.value!!.italic)
                    Spans().apply(s, start+before, start+count, 3, textstyle.value!!.underline)
                    Spans().apply(s, start+before, start+count, 4, textstyle.value!!.strikethrough)
                    if (s[start+count-1]!='\ufeff' && textstyle.value!!.list) Spans().apply(s, start+count, start+count, 5, textstyle.value!!.list)
                } else if (start+before>start+count) {
                    for (i in s!!.indices) { if (s[i]!=string[i] && string[i]=='\ufeff') Spans().apply(s as Editable, i, i+1, 5, false) }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (showKeyboard) binding.root.post { binding.blank.performClick() }
    }

    fun stylize(v: View) {
        val style = textstyle.value!!
        val (type, bool) = when (v) {
            binding.bold -> { style.bold = !style.bold; Pair(1, style.bold) }
            binding.italic -> { style.italic = !style.italic; Pair(2, style.italic) }
            binding.underline -> { style.underline = !style.underline; Pair(3, style.underline) }
            binding.strikethrough -> { style.strikethrough = !style.strikethrough; Pair(4, style.strikethrough) }
            else -> { style.list = !style.list; Pair(5, textstyle.value!!.list) } }
        textstyle.value = style
        if (binding.text.hasSelection() || type==5) Spans().apply(binding.text.text!!, binding.text.selectionStart, binding.text.selectionEnd, type, bool)
    }

    fun checkSelection(start: Int, end: Int) {
        if (start and end < binding.text.length())
            if (binding.text.text!![start]=='\ufeff')
                if (start==end) binding.text.setSelection(start+1)
                else binding.text.setSelection(start+1, end)
    }

    fun setColor(v: View) {
        color.value = when (v) { binding.red -> 0; binding.yellow -> 1; binding.green -> 2; binding.blue -> 3; binding.purple -> 4; else -> 5 }
        binding.pickcolor.performClick()
    }

    override fun onBackPressed() {
        if (binding.text.hasFocus()) {
            binding.text.clearFocus()
            binding.parent.requestFocus()
            imm.hideSoftInputFromWindow(binding.main.windowToken, 0)
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (binding.text.hasFocus()) {
            showKeyboard = true
            binding.text.clearFocus()
            binding.parent.requestFocus()
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        } else showKeyboard = false
        if (binding.title.text!!.isNotBlank() || binding.text.text!!.isNotBlank()) {
            val spannable = binding.text.text as Spannable
            val spansData = mutableListOf<SpanData>()
            val spans = spannable.getSpans(0, binding.text.text!!.length, Any::class.java)
            spans.forEach { span ->
                val type = when (span) { is BoldSpan -> 1; is ItalicSpan -> 2; is RealUnderlineSpan -> 3; is StrikethroughSpan -> 4; is ListSpan -> 5; else -> 0 }
                if (type!=0) spansData.add(SpanData(type, spannable.getSpanStart(span), spannable.getSpanEnd(span)))
            }
            Backend.insert(id, Meta(1, Clock.System.now().toEpochMilliseconds(), note.meta.createdTime, color.value!!, false), binding.title.text.toString(), binding.text.text.toString(), spansData)
        } else if (id!=0) {
            Backend.delete(id)
        }
    }

    open inner class CustomTextWatcher: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { if (binding.body.translationY!=0f) binding.pickcolor.performClick() }
    }
}