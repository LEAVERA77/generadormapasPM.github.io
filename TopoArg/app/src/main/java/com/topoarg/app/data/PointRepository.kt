package com.topoarg.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/** Repositorio simple en memoria respaldado por SQLite. */
object PointRepository {

    private lateinit var db: PointDb
    private val _points = MutableLiveData<List<SurveyPoint>>(emptyList())
    val points: LiveData<List<SurveyPoint>> get() = _points

    fun init(context: Context) {
        db = PointDb(context.applicationContext)
        refresh()
    }

    fun refresh() {
        _points.postValue(db.getAll())
    }

    fun add(point: SurveyPoint): Long {
        val id = db.insert(point)
        refresh()
        return id
    }

    fun update(id: Long, name: String, description: String) {
        db.update(id, name, description)
        refresh()
    }

    fun delete(id: Long) {
        db.delete(id)
        refresh()
    }

    fun deleteAll() {
        db.deleteAll()
        refresh()
    }

    fun count(): Int = db.count()

    /** Nombre sugerido para el siguiente punto: P1, P2, ... */
    fun nextDefaultName(): String = "P${count() + 1}"
}
