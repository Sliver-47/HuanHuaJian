package com.mymemo.app.fragments

import android.view.Menu
import android.view.MenuInflater
import androidx.navigation.fragment.findNavController
import com.mymemo.app.R
import com.mymemo.app.miscellaneous.add

class Notes : NotallyFragment() {

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(R.string.search, R.drawable.search) { findNavController().navigate(R.id.NotesToSearch) }
    }


    override fun getObservable() = model.baseNotes

    override fun getBackground() = R.drawable.notebook
}




