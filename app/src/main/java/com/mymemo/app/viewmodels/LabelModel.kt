package com.mymemo.app.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mymemo.app.room.Label
import com.mymemo.app.room.MyMemoDatabase

class LabelModel(app: Application) : AndroidViewModel(app) {

    private val database = MyMemoDatabase.getDatabase(app)
    private val labelDao = database.getLabelDao()
    val labels = labelDao.getAll()

    fun insertLabel(label: Label, onComplete: (success: Boolean) -> Unit) =
        executeAsyncWithCallback({ labelDao.insert(label) }, onComplete)
}




