package com.jingyen.notes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import androidx.transition.TransitionManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.text.clearSpans
import androidx.lifecycle.MutableLiveData
import androidx.transition.AutoTransition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.jingyen.notes.databinding.ActivityNotesBinding
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class NotesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotesBinding
    private lateinit var imm: InputMethodManager
    private lateinit var optionsBottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private var showKeyboard = false
    private var focusingText = true
    private var cursorPosition = Pair(0,0)
    private var deleteNote = false
    private var daysSelected = mutableListOf<TextView>()

    private var materialADTheme = R.style.YellowMaterialAlertDialog

    private var id = 0
    private var note = DecodedNote(0, Meta(1, Clock.System.now().toEpochMilliseconds(), Clock.System.now().toEpochMilliseconds(), 1, false), "", "", emptyList())
    private var color = MutableLiveData(1)
    private var selectingAlarm = MutableLiveData(true)
    var textstyle = MutableLiveData(TextStyle(bold = false, italic = false, underline = false, strikethrough = false, list = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        id = intent.getIntExtra("id", 0)
        if (id!=0) { CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            note = withContext(Dispatchers.IO) { Backend.get(id, applicationContext) }
            color.value = note.meta.color
            binding.title.setText(note.title)
            binding.text.setText(note.text)
            note.spansData.forEach { span ->
                (binding.text.text as Spannable).setSpan(when (span.spanType) {
                    1 -> BoldSpan(); 2 -> ItalicSpan(); 3 -> RealUnderlineSpan(); 4 -> StrikethroughSpan(); else -> ListSpan(15, 25) },
                    span.start, span.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            if (note.meta.protection) Toast.makeText(applicationContext, "You should not open this note. It's protected.", Toast.LENGTH_SHORT).show()
            changeOptionsStatus(note.meta.protection)
        } } else {
            showKeyboard = true
            id = Backend.highestId(applicationContext)
        }

        optionsBottomSheetBehavior = BottomSheetBehavior.from(binding.optionsBottomSheet)
        optionsBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    //BottomSheetBehavior.STATE_HIDDEN -> binding.scrim.visibility = View.GONE
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.wordcount.text = getString(R.string.words, WordCounter.countWords(
                            binding.text.text!!.toString()
                        ))
                    }
                    //BottomSheetBehavior.STATE_COLLAPSED ->
                    //BottomSheetBehavior.STATE_DRAGGING ->
                    //BottomSheetBehavior.STATE_SETTLING ->
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                binding.scrim.alpha = (slideOffset+1f)/5f
                if (slideOffset>-0.99f && binding.scrim.visibility==View.GONE) binding.scrim.visibility = View.VISIBLE
                else if (slideOffset<-0.99f && binding.scrim.visibility==View.VISIBLE) binding.scrim.visibility = View.GONE
            }
        })
        optionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        binding.text.notesActivity = this
        binding.root.post {
            binding.colorbar.layoutParams.height = binding.red.width
            TooltipCompat.setTooltipText(binding.color, getString(R.string.colourTip))
            TooltipCompat.setTooltipText(binding.options, getString(R.string.optionsTip))
            binding.color.setOnClickListener {
                binding.body.animate().translationY(if (binding.body.translationY==0f) binding.colorbar.height.toFloat() else 0f).duration = 150
            }
            binding.options.setOnClickListener {
                if (optionsBottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                    hideKeyboard()
                    optionsBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
                } else {
                    showKeyboard()
                    optionsBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
                }
            }
            binding.scrim.setOnClickListener {
                binding.options.performClick()
            }
            binding.showime.setOnClickListener {
                binding.imebar.visibility = View.VISIBLE; binding.showime.visibility = View.GONE
            }
            binding.hideime.setOnClickListener {
                binding.imebar.visibility = View.GONE; binding.showime.visibility = View.VISIBLE
            }
            binding.blank.setOnClickListener {
                binding.text.requestFocus()
                binding.text.setSelection(binding.text.text!!.length)
                imm.showSoftInput(binding.text, InputMethodManager.SHOW_IMPLICIT)
            }

            // Options bottom sheet actions
            binding.optionsBottomSheet.setOnClickListener {  }
            binding.actionlock.setOnClickListener {
                note.meta.protection = !note.meta.protection
                changeOptionsStatus(note.meta.protection)
            }
            binding.actionreminder.setOnClickListener {
                TransitionManager.beginDelayedTransition(binding.optionsBottomSheet, AutoTransition().setDuration(100))
                binding.actions.visibility = View.GONE
                binding.setupreminder.visibility = View.VISIBLE
                binding.alarmtimehour.text = when {
                    LocalTime.now().hour==0 -> "12"
                    LocalTime.now().hour<13 -> "${LocalTime.now().hour}"
                    else -> "${LocalTime.now().hour-12}"
                }
                binding.alarmtimeend.text = if (LocalTime.now().hour>11) "p.m." else "a.m."
                binding.alarmtimeminute.text = String.format("%02d", LocalTime.now().minute)
            }
            binding.actionshare.setOnClickListener {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TITLE, binding.title.text!!.toString())
                    putExtra(Intent.EXTRA_TEXT, binding.text.text!!.toString())
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, null))
            }
            binding.actiondelete.setOnClickListener {
                deleteNote = true
                finish()
            }

            // Options bottom sheet - setting alarm or reminder
            binding.alarmbutton.setOnClickListener { selectingAlarm.value = true }
            binding.reminderbutton.setOnClickListener { selectingAlarm.value = false }
            binding.alarmtime.setOnClickListener {
                var hournow = binding.alarmtimehour.text.toString().toInt()
                if (binding.alarmtimeend.text=="p.m."&&hournow<12) hournow +=12
                else if (binding.alarmtimeend.text=="a.m."&&hournow==12) hournow = 0
                val picker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(hournow)
                    .setMinute(binding.alarmtimeminute.text.toString().toInt())
                    .build()
                picker.addOnPositiveButtonClickListener {
                    binding.alarmtimeminute.text = String.format("%02d", picker.minute)
                    binding.alarmtimehour.text = when {
                        picker.hour==0 -> "12"
                        picker.hour<13 -> "${picker.hour}"
                        else -> "${picker.hour-12}"
                    }
                    binding.alarmtimeend.text = if (picker.hour>11) "p.m." else "a.m."
                    val now = LocalDateTime.now()
                    val samedayduration = Duration.between(now, LocalDateTime.of(now.toLocalDate(), LocalTime.of(picker.hour, picker.minute)))
                    val duration = if (samedayduration.isZero || samedayduration.isNegative) Duration.between(now, LocalDateTime.of(
                        now.plusDays(1L).toLocalDate(), LocalTime.of(picker.hour, picker.minute)))
                        else samedayduration

                    binding.alarmcalc.text =
                        "Happening in ${if(duration.toDays()>0)"${duration.toDays()} days " else ""}"+
                                "${if(duration.toHours()>0)"${duration.toHours()-duration.toDays()*24} hours " else ""}"+
                                "${if(duration.toMinutes()>0)"${duration.toMinutes()-duration.toHours()*60} minutes " else ""}"+
                                "${if(duration.toMinutes()==0L)"less than one minute" else ""}"
                }
                picker.show(supportFragmentManager, "tag");}
            binding.alarmrepeatmodeTv.setOnClickListener {
                val items = arrayOf("Once", "Daily", "Other..")
                MaterialAlertDialogBuilder(this, materialADTheme)
                    .setTitle("Select repeat mode")
                    .setItems(items) { dialog, which ->
                        when (which) {
                            0 -> {
                                binding.alarmrepeatmodeTv.text = "Repeat: Once"
                                binding.daysofweek.visibility = View.GONE
                            }
                            1 -> {
                                binding.alarmrepeatmodeTv.text = "Repeat: Daily"
                                binding.daysofweek.visibility = View.GONE
                            }
                            else -> {
                                binding.alarmrepeatmodeTv.text = "Repeat: Other.."
                                binding.daysofweek.visibility = View.VISIBLE
                            }
                        }
                    }
                    .show()
            }
            binding.alarmringtonemodeTv.setOnClickListener {
                val items = arrayOf("Default ringtone", "From this note", "From another note")
                MaterialAlertDialogBuilder(this, materialADTheme)
                    .setTitle("Select ringtone")
                    .setItems(items) { dialog, which ->
                        when (which) {
                            0 -> binding.alarmringtonemodeTv.text = "Ringtone: Default"
                            1 -> binding.alarmringtonemodeTv.text = "Ringtone: From this note"
                            else -> binding.alarmringtonemodeTv.text = "Ringtone: From another note"
                        }
                    }
                    .show()
            }

            // Observe Stateflows
            textstyle.observe(this, { value ->
                binding.bold.setBackgroundColor(if (value.bold) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.italic.setBackgroundColor(if (value.italic) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.underline.setBackgroundColor(if (value.underline) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.strikethrough.setBackgroundColor(if (value.strikethrough) Color.parseColor("#19000000") else Color.TRANSPARENT)
                binding.bullet.setBackgroundColor(if (value.list) Color.parseColor("#19000000") else Color.TRANSPARENT)
            })

            color.observe(this, { value ->
                val colorCode = getColorValues(value)
                window.navigationBarColor = resources.getColor(colorCode.first.first, this.theme)
                binding.main.setBackgroundColor(resources.getColor(colorCode.first.first, this.theme))
                binding.body.setBackgroundColor(resources.getColor(colorCode.first.first, this.theme))
                binding.parent.setBackgroundColor(resources.getColor(colorCode.first.first, this.theme))
                binding.mainplus.setBackgroundColor(resources.getColor(colorCode.first.second, this.theme))
                binding.optionsBottomSheet.background = resources.getDrawable(colorCode.second.first, this.theme)
                binding.alarmtimehour.setTextColor(resources.getColor(colorCode.first.third, this.theme))
                binding.alarmtimeseparator.setTextColor(resources.getColor(colorCode.first.third, this.theme))
                binding.alarmtimeminute.setTextColor(resources.getColor(colorCode.first.third, this.theme))
                binding.alarmtimespace.setTextColor(resources.getColor(colorCode.first.third, this.theme))
                binding.alarmtimeend.setTextColor(resources.getColor(colorCode.first.third, this.theme))
                binding.alarmdone.setTextColor(resources.getColor(colorCode.first.third, this.theme))
                selectingAlarm.value = selectingAlarm.value
            })

            selectingAlarm.observe(this, { value ->
                val colorCode = getColorValues(color.value!!)
                if (value) {
                    binding.alarmbutton.background = resources.getDrawable(colorCode.second.second, this.theme)
                    binding.alarmbuttonTv.setTextColor(resources.getColor(R.color.background, this.theme))
                    binding.alarmbuttonTv.setCompoundDrawablesWithIntrinsicBounds(resources.getDrawable(R.drawable.alarminverted, this.theme), null, null, null)
                    binding.reminderbutton.background = null
                    binding.reminderbuttonTv.setTextColor(resources.getColor(R.color.text, this.theme))
                    binding.reminderbuttonTv.setCompoundDrawablesWithIntrinsicBounds(resources.getDrawable(R.drawable.calendar, this.theme), null, null, null)
                } else {
                    binding.reminderbutton.background = resources.getDrawable(colorCode.second.second, this.theme)
                    binding.reminderbuttonTv.setTextColor(resources.getColor(R.color.background, this.theme))
                    binding.reminderbuttonTv.setCompoundDrawablesWithIntrinsicBounds(resources.getDrawable(R.drawable.calendarinverted, this.theme), null, null, null)
                    binding.alarmbutton.background = null
                    binding.alarmbuttonTv.setTextColor(resources.getColor(R.color.text, this.theme))
                    binding.alarmbuttonTv.setCompoundDrawablesWithIntrinsicBounds(resources.getDrawable(R.drawable.alarm, this.theme), null, null, null)
                }
            })
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
        if (showKeyboard) binding.root.post { showKeyboard() }
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

    private fun getColorValues(value: Int): Pair<Triple<Int, Int, Int>, Pair<Int, Int>> {
        return when (value) {
            0 -> {
                materialADTheme = R.style.RedMaterialAlertDialog
                Pair(Triple(R.color.redBackground, R.color.redBackgroundDark, R.color.redText), Pair(R.drawable.red_bottom_sheet, R.drawable.red_rounded_button))
            }
            1 -> {
                materialADTheme = R.style.YellowMaterialAlertDialog
                Pair(Triple(R.color.yellowBackground, R.color.yellowBackgroundDark, R.color.yellowText), Pair(R.drawable.yellow_bottom_sheet, R.drawable.yellow_rounded_button))
            }
            2 -> {
                materialADTheme = R.style.GreenMaterialAlertDialog
                Pair(Triple(R.color.greenBackground, R.color.greenBackgroundDark, R.color.greenText), Pair(R.drawable.green_bottom_sheet, R.drawable.green_rounded_button))
            }
            3 ->  {
                materialADTheme = R.style.BlueMaterialAlertDialog
                Pair(Triple(R.color.blueBackground, R.color.blueBackgroundDark, R.color.blueText), Pair(R.drawable.blue_bottom_sheet, R.drawable.blue_rounded_button))
            }
            4 ->  {
                materialADTheme = R.style.PurpleMaterialAlertDialog
                Pair(Triple(R.color.purpleBackground, R.color.purpleBackgroundDark, R.color.purpleText), Pair(R.drawable.purple_bottom_sheet, R.drawable.purple_rounded_button))
            }
            else ->  {
                materialADTheme = R.style.GreyMaterialAlertDialog
                Pair(Triple(R.color.greyBackground, R.color.greyBackgroundDark, R.color.greyText), Pair(R.drawable.grey_bottom_sheet, R.drawable.grey_rounded_button))
            } }
    }

    fun setColor(v: View) {
        color.value = when (v) { binding.red -> 0; binding.yellow -> 1; binding.green -> 2; binding.blue -> 3; binding.purple -> 4; else -> 5 }
        binding.color.performClick()
    }

    fun chooseDay(v: View) {
        val colorCode = getColorValues(color.value!!)
        val days = listOf(binding.sun, binding.mon, binding.tue, binding.wed, binding.thu, binding.fri, binding.sat)
        val tv = v as TextView
        if (daysSelected.contains(tv)) daysSelected.remove(tv) else daysSelected.add(tv)
        days.forEach { textView ->
            val text = SpannableString(textView.text)
            text.clearSpans()
            if (daysSelected.contains(textView)) {
                text.setSpan(UnderlineSpan(), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(BoldSpan(), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                textView.setTextColor(resources.getColor(colorCode.first.third, this.theme))
            } else {
                textView.setTextColor(resources.getColor(R.color.lightText, this.theme))
            }
            textView.text = text
        }
    }

    fun goback(v: View) {
        if (v==binding.alarmdone) {
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Set the alarm to start at approximately 2:00 p.m.
            val calendar: Calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, binding.alarmtimehour.text.toString().toInt())
                set(Calendar.MINUTE, binding.alarmtimeminute.text.toString().toInt())
                set(Calendar.SECOND, 0)
            }

            val data = Bundle()
            data.putString("title", binding.title.text.toString())
            data.putString("text", binding.text.text.toString())
            val intent = Intent(applicationContext, AlarmBroadcastReceiver::class.java)

            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }

        TransitionManager.beginDelayedTransition(binding.optionsBottomSheet, AutoTransition().setDuration(100))
        binding.actions.visibility = View.VISIBLE
        binding.setupreminder.visibility = View.GONE
    }

    private fun changeOptionsStatus(locked: Boolean) {
        binding.actionlockIv.setImageResource(if (locked) R.drawable.unlock else R.drawable.lock)
        binding.actionlockTv.text = if (locked) getString(R.string.unlock) else getString(R.string.lock)
    }

    private fun hideKeyboard(): Boolean {
        when {
            binding.text.hasFocus() -> {
                showKeyboard = true; focusingText = true; cursorPosition = Pair(binding.text.selectionStart, binding.text.selectionEnd)
                binding.text.clearFocus()
                binding.parent.requestFocus()
                imm.hideSoftInputFromWindow(binding.main.windowToken, 0)
            }
            binding.title.hasFocus() -> {
                showKeyboard = true; focusingText = false; cursorPosition = Pair(binding.title.selectionStart, binding.title.selectionEnd)
                binding.title.clearFocus()
                binding.parent.requestFocus()
                imm.hideSoftInputFromWindow(binding.main.windowToken, 0)
            }
            else -> { showKeyboard = false; focusingText = false; cursorPosition = Pair(0,0) }
        }
        return showKeyboard
    }

    private fun showKeyboard() {
        if (showKeyboard) {
            (if (focusingText) binding.text else binding.title).requestFocus()
            (if (focusingText) binding.text else binding.title).setSelection(cursorPosition.first, cursorPosition.second)
            imm.showSoftInput((if (focusingText) binding.text else binding.title), InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onBackPressed() {
        if (!hideKeyboard())
            if (optionsBottomSheetBehavior.state==BottomSheetBehavior.STATE_EXPANDED) binding.options.performClick()
            else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        hideKeyboard()
        if ((binding.title.text!!.isNotBlank() || binding.text.text!!.isNotBlank()) && !deleteNote) {
            val spannable = binding.text.text as Spannable
            val spansData = mutableListOf<SpanData>()
            val spans = spannable.getSpans(0, binding.text.text!!.length, Any::class.java)
            spans.forEach { span ->
                val type = when (span) { is BoldSpan -> 1; is ItalicSpan -> 2; is RealUnderlineSpan -> 3; is StrikethroughSpan -> 4; is ListSpan -> 5; else -> 0 }
                if (type!=0) spansData.add(SpanData(type, spannable.getSpanStart(span), spannable.getSpanEnd(span)))
            }
            Backend.insert(id, Meta(1, Clock.System.now().toEpochMilliseconds(), note.meta.createdTime, color.value!!, note.meta.protection), binding.title.text.toString(), binding.text.text.toString(), spansData)
        } else Backend.delete(id)
    }

    open inner class CustomTextWatcher: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { if (binding.body.translationY!=0f) binding.color.performClick() }
    }
}