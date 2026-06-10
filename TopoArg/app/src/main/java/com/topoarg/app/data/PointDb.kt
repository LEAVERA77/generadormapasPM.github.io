package com.topoarg.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PointDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "topoarg.db"
        private const val DB_VERSION = 2
        private const val TABLE = "points"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                altitude REAL NOT NULL,
                accuracy REAL NOT NULL,
                vertical_accuracy REAL NOT NULL DEFAULT 0,
                samples INTEGER NOT NULL DEFAULT 1,
                std_horizontal REAL NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL,
                fix_quality INTEGER NOT NULL DEFAULT -1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN fix_quality INTEGER NOT NULL DEFAULT -1")
        }
    }

    fun insert(p: SurveyPoint): Long {
        val cv = ContentValues().apply {
            put("name", p.name)
            put("description", p.description)
            put("lat", p.lat)
            put("lon", p.lon)
            put("altitude", p.altitude)
            put("accuracy", p.accuracy)
            put("vertical_accuracy", p.verticalAccuracy)
            put("samples", p.samples)
            put("std_horizontal", p.stdHorizontal)
            put("timestamp", p.timestamp)
            put("fix_quality", p.fixQuality)
        }
        return writableDatabase.insert(TABLE, null, cv)
    }

    fun update(id: Long, name: String, description: String) {
        val cv = ContentValues().apply {
            put("name", name)
            put("description", description)
        }
        writableDatabase.update(TABLE, cv, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    fun deleteAll() {
        writableDatabase.delete(TABLE, null, null)
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    fun getAll(): List<SurveyPoint> {
        val list = mutableListOf<SurveyPoint>()
        readableDatabase.query(
            TABLE, null, null, null, null, null, "id DESC"
        ).use { c ->
            while (c.moveToNext()) list.add(c.toPoint())
        }
        return list
    }

    private fun Cursor.toPoint() = SurveyPoint(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        description = getString(getColumnIndexOrThrow("description")),
        lat = getDouble(getColumnIndexOrThrow("lat")),
        lon = getDouble(getColumnIndexOrThrow("lon")),
        altitude = getDouble(getColumnIndexOrThrow("altitude")),
        accuracy = getFloat(getColumnIndexOrThrow("accuracy")),
        verticalAccuracy = getFloat(getColumnIndexOrThrow("vertical_accuracy")),
        samples = getInt(getColumnIndexOrThrow("samples")),
        stdHorizontal = getDouble(getColumnIndexOrThrow("std_horizontal")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        fixQuality = getInt(getColumnIndexOrThrow("fix_quality"))
    )
}
