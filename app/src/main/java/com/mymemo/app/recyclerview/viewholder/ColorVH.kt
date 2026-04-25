package com.mymemo.app.recyclerview.viewholder

import androidx.recyclerview.widget.RecyclerView
import com.mymemo.app.databinding.RecyclerColorBinding
import com.mymemo.app.miscellaneous.Operations
import com.mymemo.app.recyclerview.ItemListener
import com.mymemo.app.room.Color

class ColorVH(private val binding: RecyclerColorBinding, listener: ItemListener) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.root.setOnClickListener {
            listener.onClick(adapterPosition)
        }
    }

    fun bind(color: Color) {
        val value = Operations.extractColor(color, binding.root.context)
        binding.root.setCardBackgroundColor(value)
    }
}




