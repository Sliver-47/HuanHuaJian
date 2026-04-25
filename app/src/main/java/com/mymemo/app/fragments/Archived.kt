package com.mymemo.app.fragments

import com.mymemo.app.R

class Archived : NotallyFragment() {

    override fun getBackground() = R.drawable.archive

    override fun getObservable() = model.archivedNotes
}




