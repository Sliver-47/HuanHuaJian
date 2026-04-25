package com.mymemo.app.recyclerview.viewholder

import androidx.recyclerview.widget.RecyclerView
import com.mymemo.app.databinding.ErrorBinding
import com.mymemo.app.image.ImageError

class ErrorVH(private val binding: ErrorBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(error: ImageError) {
        binding.Name.text = error.name
        binding.Description.text = error.description
    }
}




