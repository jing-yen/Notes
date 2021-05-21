package com.jingyen.notes

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
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

// Not data class
object Backend {

    private lateinit var androidSqlDriver: AndroidSqliteDriver
    private lateinit var notesQueries: NotesQueries
    private lateinit var flow: Flow<List<Note>>
    var mutableNotes: MutableLiveData<List<Note>> = MutableLiveData()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getAll(applicationContext: Context) {
        androidSqlDriver = AndroidSqliteDriver(schema = Database.Schema, context = applicationContext, name = "items.db")
        notesQueries = Database(androidSqlDriver).notesQueries
        flow =  notesQueries.selectAll().asFlow().mapToList()
        scope.launch {
            flow.collect { values -> mutableNotes.postValue(values) }
        }
    }

    fun insertOrUpdate(id: Int, title: String, text: String, spansData: String, color: Int) {
        scope.launch {
            if (id==0) notesQueries.insert(1, Clock.System.now().toEpochMilliseconds(), title, text, spansData, color)
            else notesQueries.update(id, 1, Clock.System.now().toEpochMilliseconds(), title, text, spansData, color)
        }
    }

    fun delete(id: Int) {
        scope.launch { notesQueries.delete(id) }
    }
}