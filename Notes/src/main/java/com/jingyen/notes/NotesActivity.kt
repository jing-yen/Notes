package com.jingyen.notes

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.*
import android.text.style.StrikethroughSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.jingyen.notes.databinding.ActivityNotesBinding
import com.squareup.sqldelight.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NotesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotesBinding
    private lateinit var imm: InputMethodManager
    private var showKeyboard = true
    private var color = 2
    private var textstyle = TextStyle(bold = false, italic = false, underline = false, strikethrough = false, list = false)

    private lateinit var androidSqlDriver: AndroidSqliteDriver
    private lateinit var notesQueries: NotesQueries
    private var note: Note? = null
    private var id = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidSqlDriver = AndroidSqliteDriver(schema = Database.Schema, context = applicationContext, name = "items.db")

        GlobalScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                notesQueries = Database(androidSqlDriver).notesQueries
                if (intent.extras!=null) {
                    id = intent.extras!!.getInt("id", 0)
                    if (id!=0) note = notesQueries.select(id).executeAsOne()
                }
            }
            if (note!=null) {
                binding.title.setText(note!!.title)
                binding.text.setText(note!!.text)
                color = note!!.color
                setColor(binding.color) //for speed!!
                val spannable = binding.text.text as Spannable
                val spansData = Json.decodeFromString<List<SpanData>>(note!!.spansData)
                for (span in spansData) {
                    val classType: Any = when (span.spanType) {
                        1 -> BoldSpan()
                        2 -> ItalicSpan()
                        3 -> RealUnderlineSpan()
                        4 -> StrikethroughSpan()
                        else -> ListSpan(15, 15)
                    }
                    spannable.setSpan(classType, span.start, span.end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                blank(binding.blank)
            } else setColor(binding.color)
        }

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.text.activity = this
        binding.text.notesActivity = this
        binding.root.post { binding.color.layoutParams.height = binding.red.width }

        binding.text.onFocusChangeListener = View.OnFocusChangeListener { _, b -> binding.imebar.visibility = if (b) View.VISIBLE else View.GONE; if (!b) binding.showime.visibility = View.GONE }
        binding.title.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.body.translationY!=0f) pickColor(binding.pickcolor)
            }
        })
        binding.text.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.body.translationY!=0f) pickColor(binding.pickcolor)
                if (start+count>start+before) {
                    applyStyle((s as Spannable), start + before, start + count, 1, textstyle.bold)
                    applyStyle(s, start + before, start + count, 2, textstyle.italic)
                    applyStyle(s, start + before, start + count, 3, textstyle.underline)
                    applyStyle(s, start + before, start + count, 4, textstyle.strikethrough)
                    if (s[start+before] =='\n') applyStyle(s, start + count, start + count, 5, textstyle.list)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (showKeyboard) blank(binding.blank)
    }

    fun stylize(v: View) {
        val (styleType, styleBool) = when (v) {
            binding.bold -> { textstyle.bold = !textstyle.bold; Pair(1, textstyle.bold) }
            binding.italic -> { textstyle.italic = !textstyle.italic; Pair(2, textstyle.italic) }
            binding.underline -> { textstyle.underline = !textstyle.underline; Pair(3, textstyle.underline) }
            binding.strikethrough -> { textstyle.strikethrough = !textstyle.strikethrough; Pair(4, textstyle.strikethrough) }
            else -> { textstyle.list = !textstyle.list; Pair(5, textstyle.list) }
        }
        v.setBackgroundColor(if (styleBool) Color.parseColor("#19000000") else Color.TRANSPARENT)
        if (binding.text.hasSelection() || styleType==5) {
            val start = binding.text.selectionStart
            val end = binding.text.selectionEnd
            val spannable = binding.text.text as Spannable
            applyStyle(spannable, start, end, styleType, styleBool)
        }
    }

    private fun applyStyle(spannable: Spannable, start: Int, end: Int, styleType: Int, styleBool: Boolean) {
        val styleClass: Any = when (styleType) {
            1 -> BoldSpan()
            2 -> ItalicSpan()
            3 -> RealUnderlineSpan()
            4 -> StrikethroughSpan()
            else -> ListSpan(15, 25)
        }
        if (styleBool) {
            var newStart = start
            var newEnd = end
            if (styleType==5) {
                while (newStart > 0) if (spannable[--newStart]=='\n') {newStart++; break}
                binding.text.text!!.insert(newStart, "\u200b")
                while (newEnd < spannable.length - 1) if (spannable[++newEnd]=='\n') break
                val existingListSpan = spannable.getSpans(newStart, newEnd, ListSpan::class.java)
                for (listSpan in existingListSpan) spannable.removeSpan(listSpan)
            } else {
                if (start > 0) {
                    val frontStyle = spannable.getSpans(start - 1, start, styleClass::class.java)
                    for (style in frontStyle) {
                        newStart = spannable.getSpanStart(style)
                        spannable.removeSpan(style)
                    }
                }
                if (end < spannable.length - 1) {
                    val backStyle = spannable.getSpans(end, end + 1, styleClass::class.java)
                    for (style in backStyle) {
                        newEnd = spannable.getSpanEnd(style)
                        spannable.removeSpan(style)
                    }
                }
            }
            spannable.setSpan(styleClass, newStart, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (styleType == 5) binding.text.setSelection(start+1, end+1)
        } else {
            val styleSpans = spannable.getSpans(start, end, styleClass::class.java)
            for (styleSpan in styleSpans) {
                val spanStart = spannable.getSpanStart(styleSpan)
                val spanEnd = spannable.getSpanEnd(styleSpan)
                spannable.removeSpan(styleSpan)
                if (styleClass !is ListSpan) {
                    if (spanStart < start) spannable.setSpan(styleClass, spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (spanEnd > end) spannable.setSpan(styleClass, end, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    fun checkStyle(start: Int, end: Int) {
        val spannable = binding.text.text as Spannable
        val allSpans: Array<Any> = spannable.getSpans(start, end, Any::class.java)
        textstyle.bold = false
        textstyle.italic = false
        textstyle.underline = false
        textstyle.strikethrough = false
        textstyle.list = false

        for (span in allSpans) {
            if (spannable.getSpanStart(span) <= start && spannable.getSpanEnd(span) >= end)
                when (span) {
                    is BoldSpan -> textstyle.bold = true
                    is ItalicSpan -> textstyle.italic = true
                    is RealUnderlineSpan -> textstyle.underline = true
                    is StrikethroughSpan -> textstyle.strikethrough = true
                    is ListSpan -> textstyle.list = true
                }
        }
        binding.bold.setBackgroundColor(if (textstyle.bold) Color.parseColor("#19000000") else Color.TRANSPARENT)
        binding.italic.setBackgroundColor(if (textstyle.italic) Color.parseColor("#19000000") else Color.TRANSPARENT)
        binding.underline.setBackgroundColor(if (textstyle.underline) Color.parseColor("#19000000") else Color.TRANSPARENT)
        binding.strikethrough.setBackgroundColor(if (textstyle.strikethrough) Color.parseColor("#19000000") else Color.TRANSPARENT)
        binding.bullet.setBackgroundColor(if (textstyle.list) Color.parseColor("#19000000") else Color.TRANSPARENT)
    }

    fun blank(v: View) {
        binding.text.clearFocus()
        v.requestFocus()
        binding.text.requestFocus()
        binding.text.setSelection(binding.text.text!!.length)
        imm.showSoftInput(binding.text, InputMethodManager.SHOW_IMPLICIT)
    }

    fun pickColor(v: View) {
        if (v != binding.color) {
            binding.body.animate()
                .translationY(if (binding.body.translationY == 0f) binding.color.height.toFloat() else 0f)
                .duration = 150
        }
    }

    fun setColor(v: View) {
        color = when (v) {
            binding.red -> 1; binding.yellow -> 2; binding.green -> 3; binding.blue -> 4; binding.purple -> 5; binding.grey -> 6; else -> color }
        val colorCode = when (color) {
            1 -> Pair(R.color.redBackground, R.color.redBackgroundDark)
            2 -> Pair(R.color.yellowBackground, R.color.yellowBackgroundDark)
            3 -> Pair(R.color.greenBackground, R.color.greenBackgroundDark)
            4 ->  Pair(R.color.blueBackground, R.color.blueBackgroundDark)
            5 ->  Pair(R.color.purpleBackground, R.color.purpleBackgroundDark)
            else ->  Pair(R.color.greyBackground, R.color.greyBackgroundDark) }
        binding.mainplus.setBackgroundColor(resources.getColor(colorCode.second, this.theme))
        binding.parent.setBackgroundColor(resources.getColor(colorCode.first, this.theme))
        binding.main.setBackgroundColor(resources.getColor(colorCode.first, this.theme))
        binding.body.setBackgroundColor(resources.getColor(colorCode.first, this.theme))
        window.navigationBarColor = resources.getColor(colorCode.first, this.theme)
        pickColor(v)
    }

    fun hideImeBar(v: View) {
        binding.imebar.visibility = View.GONE
        binding.showime.visibility = View.VISIBLE
    }

    fun showImeBar(v: View) {
        binding.showime.visibility = View.GONE
        binding.imebar.visibility = View.VISIBLE
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
            binding.imebar.visibility = View.GONE
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        } else showKeyboard = false
        //Save
        val title = binding.title.text.toString()
        val text = binding.text.text.toString()
        if (binding.title.text.isNotBlank() || binding.text.text!!.isNotBlank()) {
            val spannable = binding.text.text as Spannable
            val styleSpans = spannable.getSpans(0, binding.text.text!!.length, ParcelableSpan::class.java)
            val spansData = mutableListOf<SpanData>()
            for (span in styleSpans) {
                val classType = when (span) {
                    is BoldSpan -> 1
                    is ItalicSpan -> 2
                    is RealUnderlineSpan -> 3
                    is StrikethroughSpan -> 4
                    else -> 0
                }
                if (classType!=0) spansData.add(SpanData(classType, spannable.getSpanStart(span), spannable.getSpanEnd(span)))
            }
            Backend.insertOrUpdate(id, title, text, Json.encodeToString(spansData), color)
        } else if (id!=0) {
            Backend.delete(id)
        }
    }
}