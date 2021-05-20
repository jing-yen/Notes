package com.jingyen.notes

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
    var processing = false
    var done = false
    fun getAll(notesQueries: NotesQueries): List<Note> {
        processing = false
        done = false
        return notesQueries.selectAll().executeAsList()
    }
    fun insertOrUpdate(notesQueries: NotesQueries, id: Int, title: String, text: String, spansData: String, color: Int) {
        GlobalScope.launch {
            processing = true
            if (id==0)
                notesQueries.insert(1, Clock.System.now().toEpochMilliseconds(), title, text, spansData, color)
            else
                notesQueries.update(id, 1, Clock.System.now().toEpochMilliseconds(), title, text, spansData, color)
            done = true
        }
    }
    fun delete(notesQueries: NotesQueries, id: Int) {
        GlobalScope.launch {
            processing = true
            notesQueries.delete(id)
            done = true
        }
    }
}