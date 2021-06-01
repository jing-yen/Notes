package com.jingyen.notes

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.Clock

data class TextStyle(
    var bold: Boolean,
    var italic: Boolean,
    var underline: Boolean,
    var strikethrough: Boolean,
    var list: Boolean)

@kotlinx.serialization.Serializable
data class SpanData(var spanType: Int, var start: Int, var end: Int)

object Backend {
    private lateinit var androidSqlDriver: AndroidSqliteDriver
    private lateinit var notesQueries: NotesQueries
    var mutableNotes = MutableStateFlow<List<Note>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getAll(applicationContext: Context) {
        androidSqlDriver = AndroidSqliteDriver(schema = Database.Schema, context = applicationContext, name = "items.db")
        notesQueries = Database(androidSqlDriver).notesQueries
        scope.launch {
            notesQueries.selectAll().asFlow().mapToList().collect { values -> mutableNotes.value = values }
        }
    }

    fun get(id: Int): Note {
        return notesQueries.select(id).executeAsOne()
    }

    fun insert(id: Int, createdTime: Long, title: String, text: String, spansData: String, color: Int) {
        scope.launch {
            withContext(NonCancellable) {
                notesQueries.insert(id, 1, Clock.System.now().toEpochMilliseconds(), createdTime, title, text, spansData, color)
            }
        }
    }

    fun highestId(): Int {
        return try { notesQueries.highestId().executeAsOne()+1 ?: 1 } catch (ex: NullPointerException) { 1 }
    }

    fun delete(id: Int) {
        scope.launch { withContext(NonCancellable) { notesQueries.delete(id) } }
    }
}