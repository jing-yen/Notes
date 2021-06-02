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

    fun getAllFast(applicationContext: Context) {
        androidSqlDriver = AndroidSqliteDriver(schema = Database.Schema, context = applicationContext, name = "items.db")
        notesQueries = Database(androidSqlDriver).notesQueries
        scope.launch {
            notesQueries.selectAll().asFlow().mapToList().collect { values ->
                mutableNotes.value = values.map { value -> FastDecodedNote(value.id, Json.decodeFromString(value.meta), value.title, value.text) }
            }
        }
    }

    fun get(id: Int): DecodedNote {
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

    fun highestId(): Int {
        return try {
            notesQueries.highestId().executeAsOne()+1
        } catch (ex: NullPointerException) { 1 }
    }

    fun delete(id: Int) {
        scope.launch { withContext(NonCancellable) { notesQueries.delete(id) } }
    }
}