package com.jingyen.notes

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.icu.text.BreakIterator
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.collections.HashMap

data class TextStyle(
    var bold: Boolean,
    var italic: Boolean,
    var underline: Boolean,
    var strikethrough: Boolean,
    var list: Boolean)

data class DecodedNote(
    var id: Int,
    var meta: Meta,
    var title: String,
    var text: String,
    var spansData: List<SpanData>)

data class FastDecodedNote(
    var id: Int,
    var meta: Meta,
    var title: String,
    var text: String)

@kotlinx.serialization.Serializable
data class Meta(var appVersion: Int, var modifiedTime: Long, var createdTime: Long, val color: Int, var protection: Boolean)

@kotlinx.serialization.Serializable
data class SpanData(var spanType: Int, var start: Int, var end: Int)

object Backend {
    private lateinit var androidSqlDriver: AndroidSqliteDriver
    private lateinit var notesQueries: NotesQueries
    var mutableNotes = MutableStateFlow<List<FastDecodedNote>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun init(applicationContext: Context) {
        if (!this::androidSqlDriver.isInitialized||!this::notesQueries.isInitialized) {
            androidSqlDriver = AndroidSqliteDriver(schema = Database.Schema, context = applicationContext, name = "items.db")
            notesQueries = Database(androidSqlDriver).notesQueries
        }
    }

    fun getAllFast(applicationContext: Context) {
        init(applicationContext)
        scope.launch {
            notesQueries.selectAll().asFlow().mapToList().collect { values ->
                mutableNotes.value = values.map { value -> FastDecodedNote(value.id, Json.decodeFromString(value.meta), value.title, value.text) }
            }
        }
    }

    fun get(id: Int, applicationContext: Context): DecodedNote {
        init(applicationContext)
        val note = notesQueries.select(id).executeAsOne()
        return DecodedNote(note.id, Json.decodeFromString(note.meta), note.title, note.text, Json.decodeFromString(note.spansData))
    }

    fun insert(id: Int, meta: Meta, title: String, text: String, spansData: List<SpanData>) {
        scope.launch {
            withContext(NonCancellable) {
                notesQueries.insert(id, Json.encodeToString(meta), title, text, Json.encodeToString(spansData))
            }
        }
    }

    fun highestId(applicationContext: Context): Int {
        init(applicationContext)
        return try { notesQueries.highestId().executeAsOne()+1 } catch (ex: NullPointerException) { 1 }
    }

    fun delete(id: Int) {
        scope.launch { withContext(NonCancellable) { notesQueries.delete(id) } }
    }
}

object WordCounter {
    private lateinit var breakIterator: BreakIterator
    private lateinit var breakIteratorPre24: java.text.BreakIterator

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            breakIterator = BreakIterator.getWordInstance()
        } else {
            breakIteratorPre24 = java.text.BreakIterator.getWordInstance()
        }
    }

    fun countWords(text: String): Int {
        val wordCounts: MutableMap<String, WordCount> = HashMap()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            breakIterator.setText(text)
            var wordBoundaryIndex: Int = breakIterator.first()
            var prevIndex = 0
            while (wordBoundaryIndex != BreakIterator.DONE) {
                val word = text.substring(prevIndex, wordBoundaryIndex).lowercase()
                if (isWord(word)) {
                    var wordCount = wordCounts[word]
                    if (wordCount == null) {
                        wordCount = WordCount()
                        wordCount.word = word
                    }
                    wordCount.count++
                    wordCounts[word] = wordCount
                }
                prevIndex = wordBoundaryIndex
                wordBoundaryIndex = breakIterator.next()
            }
        } else {
            breakIteratorPre24.setText(text)
            var wordBoundaryIndex: Int = breakIteratorPre24.first()
            var prevIndex = 0
            while (wordBoundaryIndex != java.text.BreakIterator.DONE) {
                val word = text.substring(prevIndex, wordBoundaryIndex).lowercase()
                if (isWord(word)) {
                    var wordCount = wordCounts[word]
                    if (wordCount == null) {
                        wordCount = WordCount()
                        wordCount.word = word
                    }
                    wordCount.count++
                    wordCounts[word] = wordCount
                }
                prevIndex = wordBoundaryIndex
                wordBoundaryIndex = breakIteratorPre24.next()
            }
        }
        return wordCounts.size
    }

    private fun isWord(word: String): Boolean {
        return if (word.length == 1) {
            Character.isLetterOrDigit(word[0])
        } else "" != word.trim { it <= ' ' }
    }

    class WordCount {
        var word: String? = null
        var count = 0
    }
}