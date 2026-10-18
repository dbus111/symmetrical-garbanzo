package com.example.tomatogarden

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class FocusRecord(
    val sessionId: String,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val durationMinutes: Int,
)

class FocusDatabase(context: Context) : SQLiteOpenHelper(context, "focus_records.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE focus_records (
                session_id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                completed_at INTEGER NOT NULL,
                duration_minutes INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX focus_records_completed_at ON focus_records(completed_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(completion: FocusCompletion) {
        val values = ContentValues().apply {
            put("session_id", completion.sessionId)
            put("started_at", completion.startedAtMs)
            put("completed_at", completion.completedAtMs)
            put("duration_minutes", completion.durationMinutes)
        }
        writableDatabase.insertWithOnConflict(
            "focus_records", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun all(): List<FocusRecord> {
        val records = mutableListOf<FocusRecord>()
        readableDatabase.rawQuery(
            "SELECT session_id, started_at, completed_at, duration_minutes " +
                "FROM focus_records ORDER BY completed_at DESC", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                records += FocusRecord(
                    cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3)
                )
            }
        }
        return records
    }
}
