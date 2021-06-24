package com.jingyen.notes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jingyen.notes.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlin.math.sqrt
import android.content.DialogInterface
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executor


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "SETTINGS")

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var typeface: Typeface
    private lateinit var typefaceBold: Typeface
    private lateinit var imm: InputMethodManager
    private val Int.toPx: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    private var sortBy = 0
    private var unlocked = false
    private var password = ""
    private var notes: MutableList<FastDecodedNote> = mutableListOf()

    private var sensorManager: SensorManager? = null
    private val SHAKE_THRESHOLD_GRAVITY = 2.3f
    private val SHAKE_SLOP_TIME_MS = 500
    private val SHAKE_COUNT_RESET_TIME_MS = 3000
    private var mShakeTimestamp: Long = 0
    private var mShakeCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        typeface = Typeface.createFromAsset(assets,"regular.ttf")
        typefaceBold = Typeface.createFromAsset(assets,"semibold.ttf")

        binding.parent.layoutTransition.setDuration(100)
        window.navigationBarColor = resources.getColor(R.color.background, this.theme)

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            applicationContext.dataStore.data
                .map { preferences -> preferences[intPreferencesKey("SORT")] ?: 0 }
                .collect { value -> sortBy = value }
            applicationContext.dataStore.data
                .map { preferences -> preferences[stringPreferencesKey("PASSWORD")] ?: "" }
                .collect { value -> password = value }
        }

        binding.search.setOnFocusChangeListener { _, b ->
            if (b) {
                binding.head.visibility = View.GONE
                binding.title.visibility = View.GONE
                binding.sort.visibility = View.GONE
                binding.entries.translationY = 0f
                (binding.backlayout as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                binding.back.visibility = View.VISIBLE
                (binding.backlayout as ViewGroup).descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            } else {
                binding.head.visibility = View.VISIBLE
                binding.title.visibility = View.VISIBLE
                binding.sort.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
                binding.entries.translationY = -binding.sortButtons.height.toFloat()
                binding.back.visibility = View.GONE
            }
        }

        binding.search.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.entries.removeAllViews()
                if (start + count > 0) notes.forEach { note ->
                    if ((note.title.contains(s!!, ignoreCase = true) || note.text.contains(s, ignoreCase = true)) && note.meta.protection==unlocked) addEntry(note) }
                else for (note in notes.filter { if (unlocked) it.meta.protection else !it.meta.protection }) addEntry(note)
            }
        })

        Backend.getAllFast(this@MainActivity)
        binding.root.post {
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                Backend.mutableNotes.collect { mutableNotesValue ->
                    binding.nonotesalarm.visibility = if (mutableNotesValue.isEmpty()) View.VISIBLE else View.GONE
                    binding.sort.visibility = if (mutableNotesValue.isNotEmpty()) View.VISIBLE else View.GONE
                    notes = mutableNotesValue.toMutableList()
                    sort(when (sortBy) { 0 -> binding.sortModifiedTime; 1 -> binding.sortCreatedTime; else -> binding.sortColor})
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (binding.back.visibility==View.VISIBLE) stopSearch(binding.back)
    }

    override fun onResume() {
        //sensorManager?.registerListener(sensorListener, sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        super.onResume()
    }

    private fun addEntry(note: FastDecodedNote) {
        val colorCode = when (note.meta.color) {
            0 -> R.color.redPreview
            1 -> R.color.yellowPreview
            2 -> R.color.greenPreview
            3 -> R.color.bluePreview
            4 -> R.color.purplePreview
            else -> R.color.greyPreview }

        val title = AppCompatTextView(this)
        title.isEmojiCompatEnabled = true
        title.text = note.title
        title.typeface = typefaceBold
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        title.setTextColor(resources.getColor(R.color.text, this.theme))

        val text = AppCompatTextView(this)
        text.isEmojiCompatEnabled = true
        text.text = note.text
        text.typeface = typeface
        text.maxLines = 10
        text.ellipsize = TextUtils.TruncateAt.END
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        text.setTextColor(resources.getColor(R.color.text, this.theme))

        val entry = LinearLayout(this)
        entry.orientation = LinearLayout.VERTICAL
        entry.setBackgroundColor(resources.getColor(colorCode, this.theme))
        if (note.title.isNotEmpty()) entry.addView(title)
        if (note.text.isNotEmpty()) entry.addView(text)
        entry.setPadding(16.toPx, 12.toPx, 16.toPx, 12.toPx)

        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(0, 0, 0, 10.toPx)
        entry.layoutParams = layoutParams
        entry.setOnClickListener { goToNotes(note.id, note.meta.minVersion) }
        binding.entries.addView(entry)
    }

    fun newEntry(v: View) {
        goToNotes(-1, BuildConfig.VERSION_CODE)
    }

    fun stopSearch(v: View) {
        binding.search.setText("")
        binding.search.clearFocus()
        binding.entries.requestFocus()
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    fun sort(v: View) {
        binding.entries.removeAllViews()
        sortBy = when (v) { binding.sortModifiedTime -> 0; binding.sortCreatedTime -> 1; else -> 2 }
        when (sortBy) {
            0 -> notes.sortByDescending { it.meta.modifiedTime }
            1 -> notes.sortByDescending { it.meta.createdTime }
            else -> notes.sortBy { it.meta.color }
        }
        for (note in notes.filter { if (unlocked) it.meta.protection else !it.meta.protection }) addEntry(note)
        binding.sortModifiedTime.background = null
        binding.sortCreatedTime.background = null
        binding.sortColor.background = null
        v.background = resources.getDrawable(R.drawable.sortbar, this.theme)

        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            applicationContext.dataStore.edit { settings -> settings[intPreferencesKey("SORT")] = sortBy }
        }

        binding.sortButtons.visibility = View.INVISIBLE
        binding.entries.animate()
            .translationY(-binding.sortButtons.height.toFloat())
            .duration = 100
        binding.sortText.text = when (sortBy) { 0 -> getString(R.string.modifiedTime); 1 -> getString(R.string.createdTime); else -> getString(R.string.colour) }
    }

    fun showSort(v: View) {
        if (binding.sortButtons.visibility == View.INVISIBLE) {
            binding.sortButtons.visibility = View.VISIBLE
            binding.entries.animate()
                .translationY(0f)
                .duration = 100
        }
        else {
            binding.entries.animate()
                .translationY(-binding.sortButtons.height.toFloat())
                .setDuration(100)
                .withEndAction { binding.sortButtons.visibility = View.INVISIBLE }
        }
    }

    private fun goToNotes(id: Int, minVersion: Int) {
        if (minVersion <= BuildConfig.VERSION_CODE) {
            val intent = Intent(this, NotesActivity::class.java)
            if (id > 0) intent.putExtra("id", id)
            startActivity(intent)
        } else {
            MaterialAlertDialogBuilder(this, R.style.GreyMaterialAlertDialog)
                .setTitle("Note is from newer version")
                .setMessage("This note was created from a newer version of the app. You won't be able to open it unless you update this app. Update now?")
                .setPositiveButton("Continue") { dialog, which ->
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
                    catch (e: ActivityNotFoundException) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
                }
                .setNegativeButton("Not Now", null)
                .show()
        }
    }

    fun unlock(v: View) {
        if (unlocked) {
            unlocked = false
            binding.title.animate()
                .alpha(0f)
                .setDuration(100L)
                .withEndAction {
                    binding.title.text = getString(R.string.app_title)
                    binding.title.animate().alpha(1f).duration = 100L
                }
            binding.lockedicon2.visibility = View.VISIBLE
            binding.exittext.visibility = View.GONE
            binding.lockedicon.setImageDrawable(resources.getDrawable(R.drawable.lock, this.theme))
            binding.entries.animate()
                .alpha(0f)
                .setDuration(100L)
                .withEndAction {
                    sort(when (sortBy) { 0 -> binding.sortModifiedTime; 1 -> binding.sortCreatedTime; else -> binding.sortColor})
                    binding.entries.animate().alpha(1f).duration = 100L
                }
        } else {
            lateinit var biometricPrompt: BiometricPrompt
            val executor: Executor = ContextCompat.getMainExecutor(this)
            biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    }
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        unlocked = true
                        binding.title.animate()
                            .alpha(0f)
                            .setDuration(100L)
                            .withEndAction {
                                binding.title.text = "Locked Notes \uD83D\uDD12"
                                binding.title.animate().alpha(1f).duration = 100L
                            }
                        binding.lockedicon2.visibility = View.GONE
                        binding.exittext.visibility = View.VISIBLE
                        binding.lockedicon.setImageDrawable(resources.getDrawable(R.drawable.logout, this@MainActivity.theme))
                        binding.entries.animate()
                            .alpha(0f)
                            .setDuration(100L)
                            .withEndAction {
                                sort(when (sortBy) { 0 -> binding.sortModifiedTime; 1 -> binding.sortCreatedTime; else -> binding.sortColor})
                                binding.entries.animate().alpha(1f).duration = 100L
                            }
                    }
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                })
            val promptInfo: BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Access locked notes")
                .setSubtitle("Log in with your biometrics")
                .setNegativeButtonText("Use PIN")
                .build()
            biometricPrompt.authenticate(promptInfo)
        }
    }

    override fun onBackPressed() {
        if (binding.back.visibility==View.VISIBLE) {
            stopSearch(binding.back)
        } else super.onBackPressed()
    }

    override fun onPause() {
        //sensorManager?.unregisterListener(sensorListener)
        super.onPause()
    }

    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH
            // gForce will be close to 1 when there is no movement.
            val gForce: Float = sqrt(gX * gX + gY * gY + gZ * gZ)
            Log.d("damn", gForce.toString())
            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()
                // ignore shake events too close to each other (500ms)
                if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    return
                }
                // reset the shake count after 3 seconds of no shakes
                if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                    mShakeCount = 0
                }
                mShakeTimestamp = now
                mShakeCount++
                onShake()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    fun onShake() {
        //sensorManager?.unregisterListener(sensorListener)
        Toast.makeText(this, "Shake to create!", Toast.LENGTH_SHORT).show()
        newEntry(binding.avd)
    }
}