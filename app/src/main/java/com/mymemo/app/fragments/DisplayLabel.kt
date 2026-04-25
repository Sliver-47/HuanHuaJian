package com.mymemo.app.fragments

import androidx.lifecycle.LiveData
import com.mymemo.app.R
import com.mymemo.app.miscellaneous.Constants
import com.mymemo.app.room.Item

class DisplayLabel : NotallyFragment() {

    override fun getBackground() = R.drawable.label

    override fun getObservable(): LiveData<List<Item>> {
        val label = requireNotNull(requireArguments().getString(Constants.SelectedLabel))
        return model.getNotesByLabel(label)
    }
}




