package com.mymemo.app.activities

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.Patterns
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.text.getSpans
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mymemo.app.LinkMovementMethod
import com.mymemo.app.R
import com.mymemo.app.miscellaneous.add
import com.mymemo.app.miscellaneous.setOnNextAction
import com.mymemo.app.room.Type

class TakeNote : MyMemoActivity(Type.NOTE) {

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction {
            binding.EnterBody.requestFocus()
        }

        setupEditor()

        if (model.isNewNote) {
            binding.EnterBody.requestFocus()
        }
    }


    override fun setupListeners() {
        super.setupListeners()
        binding.EnterBody.doAfterTextChanged { text ->
            model.body = requireNotNull(text)
        }
    }

    override fun setStateFromModel() {
        super.setStateFromModel()
        binding.EnterBody.text = model.body
    }


    private fun setupEditor() {
        setupMovementMethod()

        binding.EnterBody.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                binding.EnterBody.isActionModeOn = true
                // Try block is there because this will crash on MiUI as Xiaomi has a broken ActionMode implementation
                try {
                    if (menu != null) {
                        val bold = menu.add(R.string.bold, 0, 0, R.string.bold)
                        bold.setOnMenuItemClickListener {
                            applySpan(StyleSpan(Typeface.BOLD))
                            mode?.finish()
                            true
                        }

                        val link = menu.add(R.string.link, 1, 1, R.string.link)
                        link.setOnMenuItemClickListener {
                            applySpan(URLSpan(null))
                            mode?.finish()
                            true
                        }

                        val italic = menu.add(R.string.italic, 2, 2, R.string.italic)
                        italic.setOnMenuItemClickListener {
                            applySpan(StyleSpan(Typeface.ITALIC))
                            mode?.finish()
                            true
                        }

                        val monospace = menu.add(R.string.monospace, 3, 3, R.string.monospace)
                        monospace.setOnMenuItemClickListener {
                            applySpan(TypefaceSpan("monospace"))
                            mode?.finish()
                            true
                        }

                        val strikethrough = menu.add(R.string.strikethrough, 4, 4, R.string.strikethrough)
                        strikethrough.setOnMenuItemClickListener {
                            applySpan(StrikethroughSpan())
                            mode?.finish()
                            true
                        }

                        val clearFormatting = menu.add(R.string.clear_formatting, 5, 5, R.string.clear_formatting)
                        clearFormatting.setOnMenuItemClickListener {
                            removeSpans()
                            mode?.finish()
                            true
                        }
                    }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                binding.EnterBody.isActionModeOn = false
            }
        }
    }

    private fun setupMovementMethod() {
        val items = arrayOf(getString(R.string.edit), getString(R.string.open_link))
        val movementMethod = LinkMovementMethod { span ->
            MaterialAlertDialogBuilder(this)
                .setItems(items) { dialog, which ->
                    if (which == 1) {
                        val spanStart = binding.EnterBody.text?.getSpanStart(span)
                        val spanEnd = binding.EnterBody.text?.getSpanEnd(span)

                        ifBothNotNullAndInvalid(spanStart, spanEnd) { start, end ->
                            val text = binding.EnterBody.text?.substring(start, end)
                            if (text != null) {
                                val link = getURLFrom(text)
                                val uri = Uri.parse(link)

                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                try {
                                    startActivity(intent)
                                } catch (exception: Exception) {
                                    Toast.makeText(this, R.string.cant_open_link, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }.show()
        }
        binding.EnterBody.movementMethod = movementMethod
    }


    private fun removeSpans() {
        val selectionEnd = binding.EnterBody.selectionEnd
        val selectionStart = binding.EnterBody.selectionStart

        ifBothNotNullAndInvalid(selectionStart, selectionEnd) { start, end ->
            binding.EnterBody.text?.getSpans<CharacterStyle>(start, end)?.forEach { span ->
                binding.EnterBody.text?.removeSpan(span)
            }
        }
    }

    private fun applySpan(span: Any) {
        val selectionEnd = binding.EnterBody.selectionEnd
        val selectionStart = binding.EnterBody.selectionStart

        ifBothNotNullAndInvalid(selectionStart, selectionEnd) { start, end ->
            binding.EnterBody.text?.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun ifBothNotNullAndInvalid(start: Int?, end: Int?, function: (start: Int, end: Int) -> Unit) {
        if (start != null && start != -1 && end != null && end != -1) {
            function.invoke(start, end)
        }
    }


    companion object {

        fun getURLFrom(text: String): String {
            return when {
                text.matches(Patterns.PHONE.toRegex()) -> "tel:$text"
                text.matches(Patterns.EMAIL_ADDRESS.toRegex()) -> "mailto:$text"
                text.matches(Patterns.DOMAIN_NAME.toRegex()) -> "http://$text"
                else -> text
            }
        }
    }
}
