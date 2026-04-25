package com.mymemo.app.activities

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.getSpans
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFade
import com.mymemo.app.R
import com.mymemo.app.databinding.ActivityMymemoBinding
import com.mymemo.app.databinding.DialogProgressBinding
import com.mymemo.app.databinding.DialogReminderBinding
import com.mymemo.app.image.ImageError
import com.mymemo.app.miscellaneous.Constants
import com.mymemo.app.miscellaneous.Operations
import com.mymemo.app.miscellaneous.add
import com.mymemo.app.preferences.TextSize
import com.mymemo.app.recyclerview.adapter.AudioAdapter
import com.mymemo.app.recyclerview.adapter.ErrorAdapter
import com.mymemo.app.recyclerview.adapter.PreviewImageAdapter
import com.mymemo.app.room.Audio
import com.mymemo.app.room.Folder
import com.mymemo.app.room.Frequency
import com.mymemo.app.room.Image
import com.mymemo.app.room.Reminder
import com.mymemo.app.room.Type
import com.mymemo.app.viewmodels.MyMemoModel
import com.mymemo.app.widget.WidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar

abstract class MyMemoActivity(private val type: Type) : AppCompatActivity() {

    internal lateinit var binding: ActivityMymemoBinding
    internal val model: MyMemoModel by viewModels()

    // ActivityResultLauncher
    private val alarmPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (manager.canScheduleExactAlarms()) {
                displayReminderDialog()
            }
        }
    }

    private val recordAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            model.addAudio()
        }
    }

    private val addImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uri = data?.data
            val clipData = data?.clipData
            if (uri != null) {
                val uris = arrayOf(uri)
                model.addImages(uris)
            } else if (clipData != null) {
                val uris = Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                model.addImages(uris)
            }
        }
    }

    private val selectLabelsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val list = data?.getSerializableExtra(SelectLabels.SELECTED_LABELS) as? ArrayList<String>
            if (list != null && list != model.labels.value) {
                model.labels.value = list
            }
        }
    }

    private val viewImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val list = data?.getSerializableExtra(ViewImage.DELETED_IMAGES) as? ArrayList<Image>
            if (!list.isNullOrEmpty()) {
                model.deleteImages(list)
            }
        }
    }

    private val playAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val audio = data?.getSerializableExtra(PlayAudio.AUDIO) as? Audio
            if (audio != null) {
                model.deleteAudio(audio)
            }
        }
    }

    override fun finish() {
        // 记录调用栈，查看是谁调用了 finish()
        val stackTrace = Throwable().stackTrace
        val sb = StringBuilder()
        sb.append("=== finish() called ===\n")
        for (i in 0 until minOf(10, stackTrace.size)) {
            sb.append(stackTrace[i].toString()).append("\n")
        }
        sb.append("=====================\n")
        Log.e("MyMemoActivity", sb.toString())
        
        // 直接调用 super.finish()，让 Activity 正常销毁
        // 保存操作在 onPause 或 onStop 中执行
        super.finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("id", model.id)
    }

    override fun onPause() {
        super.onPause()
        // 在 Activity 暂停时保存笔记
        lifecycleScope.launch(Dispatchers.IO) {
            model.saveNote()
            WidgetProvider.sendBroadcast(application, model.id)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.type = type
        initialiseBinding()
        setContentView(binding.root)

        // 先显示 UI，避免应用跳转到后台
        binding.ScrollView.visibility = View.VISIBLE

        // 调用 model.setState()，等待数据加载完成后执行 UI 操作
        lifecycleScope.launch(Dispatchers.IO) {
            if (model.isFirstInstance) {
                val persistedId = savedInstanceState?.getLong("id")
                val selectedId = intent.getLongExtra(Constants.SelectedBaseNote, 0L)
                val id = persistedId ?: selectedId
                model.setState(id)

                if (model.isNewNote && intent.action == Intent.ACTION_SEND) {
                    withContext(Dispatchers.Main) {
                        handleSharedNote()
                    }
                }

                model.isFirstInstance = false
            }

            withContext(Dispatchers.Main) {
                setupToolbar()
                setupListeners()
                setupObservers()
                setStateFromModel()
                configureUI()
            }
        }
    }




    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkAlarmPermission()
                }
            }
            REQUEST_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    recordAudio()
                } else handleRejection()
            }
        }
    }


    abstract fun configureUI()

    open fun setupListeners() {
        binding.EnterTitle.doAfterTextChanged { text ->
            model.title = requireNotNull(text).trim().toString()
        }
    }

    open fun setStateFromModel() {
        val formatter = DateFormat.getDateInstance(DateFormat.FULL)
        binding.DateCreated.text = formatter.format(model.timestamp)

        binding.EnterTitle.setText(model.title)
    }


    private fun handleSharedNote() {
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val string = intent.getStringExtra(Intent.EXTRA_TEXT)
        val charSequence = intent.getCharSequenceExtra(Operations.extraCharSequence)
        val body = charSequence ?: string

        if (body != null) {
            model.body = Editable.Factory.getInstance().newEditable(body)
        }
        if (title != null) {
            model.title = title
        }
    }


    @RequiresApi(24)
    private fun checkAudioPermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(permission)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.please_grant_notally_audio)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.continue_) { _, _ ->
                        requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
                    }
                    .show()
            } else requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
        } else recordAudio()
    }

    private fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (manager.canScheduleExactAlarms()) {
                displayReminderDialog()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.please_grant_notally_alarm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.continue_) { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        intent.data = Uri.parse("package:$packageName")
                        alarmPermissionLauncher.launch(intent)
                    }
                    .show()
            }
        } else displayReminderDialog()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(permission)) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.please_grant_notally_notification)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.continue_) { _, _ ->
                            requestPermissions(arrayOf(permission), REQUEST_NOTIFICATION_PERMISSION)
                        }
                        .show()
                } else requestPermissions(arrayOf(permission), REQUEST_NOTIFICATION_PERMISSION)
            } else checkAlarmPermission()
        } else checkAlarmPermission()
    }


    private fun recordAudio() {
        if (model.audioRoot != null) {
            val intent = Intent(this, RecordAudio::class.java)
            recordAudioLauncher.launch(intent)
        } else Toast.makeText(this, R.string.insert_an_sd_card_audio, Toast.LENGTH_LONG).show()
    }

    private fun handleRejection() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.to_record_audio)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            }
            .show()
    }

    private fun selectImages() {
        if (model.imageRoot != null) {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            addImagesLauncher.launch(intent)
        } else Toast.makeText(this, R.string.insert_an_sd_card_images, Toast.LENGTH_LONG).show()
    }


    private fun share() {
        val body = when (type) {
            Type.NOTE -> model.body
            Type.LIST -> Operations.getBody(model.items)
        }
        Operations.shareNote(this, model.title, body)
    }

    private fun label() {
        val intent = Intent(this, SelectLabels::class.java)
        intent.putStringArrayListExtra(SelectLabels.SELECTED_LABELS, model.labels.value)
        selectLabelsLauncher.launch(intent)
    }

    private fun delete() {
        model.folder = Folder.DELETED
        finish()
    }

    private fun restore() {
        model.folder = Folder.NOTES
        finish()
    }

    private fun archive() {
        model.folder = Folder.ARCHIVED
        finish()
    }

    private fun deleteForever() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_note_forever)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    model.deleteBaseNote()
                    super.finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }


    private fun setupColor() {
        model.color.observe(this, Observer { color ->
            val colorInt = Operations.extractColor(color, this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    window.statusBarColor = colorInt
                } else {
                    window.statusBarColor = colorInt
                }
            }
            binding.root.setBackgroundColor(colorInt)
            binding.RecyclerView.setBackgroundColor(colorInt)
            binding.Toolbar.backgroundTintList = ColorStateList.valueOf(colorInt)
        })
    }


    private fun setupImages() {
        val adapter = PreviewImageAdapter(model.imageRoot) { position ->
            val intent = Intent(this, ViewImage::class.java)
            intent.putExtra(ViewImage.POSITION, position)
            intent.putExtra(Constants.SelectedBaseNote, model.id)
            viewImagesLauncher.launch(intent)
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.ImagePreview.scrollToPosition(positionStart)
            }
        })

        binding.ImagePreview.setHasFixedSize(true)
        binding.ImagePreview.adapter = adapter
        binding.ImagePreview.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        PagerSnapHelper().attachToRecyclerView(binding.ImagePreview)

        model.images.observe(this, Observer { list ->
            adapter.submitList(list)
            binding.ImagePreview.isVisible = list.isNotEmpty()
        })

        val dialogBinding = DialogProgressBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.adding_images)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        model.addingImages.observe(this, Observer { progress ->
            if (progress.inProgress) {
                dialog.show()
                dialogBinding.ProgressBar.max = progress.total
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    dialogBinding.ProgressBar.setProgress(progress.current, true)
                } else dialogBinding.ProgressBar.progress = progress.current
                dialogBinding.Count.text = getString(R.string.count, progress.current, progress.total)
            } else dialog.dismiss()
        })

        model.eventBus.observe(this, Observer { event ->
            event.handle { errors -> displayImageErrors(errors) }
        })
    }

    private fun displayImageErrors(errors: List<ImageError>) {
        val recyclerView = RecyclerView(this)
        recyclerView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        recyclerView.adapter = ErrorAdapter(errors)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            recyclerView.scrollIndicators = View.SCROLL_INDICATOR_TOP or View.SCROLL_INDICATOR_BOTTOM
        }

        val title = resources.getQuantityString(R.plurals.cant_add_images, errors.size, errors.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(recyclerView)
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun setupAudios() {
        val adapter = AudioAdapter { position: Int ->
            if (position != -1) {
                val audio = model.audios.value[position]
                val intent = Intent(this, PlayAudio::class.java)
                intent.putExtra(PlayAudio.AUDIO, audio)
                playAudioLauncher.launch(intent)
            }
        }
        binding.AudioRecyclerView.adapter = adapter

        model.audios.observe(this, Observer { list ->
            adapter.submitList(list)
            binding.AudioHeader.isVisible = list.isNotEmpty()
            binding.AudioRecyclerView.isVisible = list.isNotEmpty()
        })
    }


    private fun setupReminder() {
        val padding = (resources.displayMetrics.density * 16).toInt()
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        model.reminder.observe(this, Observer { reminder ->
            if (reminder != null) {
                val date = formatter.format(reminder.timestamp)
                binding.Reminder.text = when (reminder.frequency) {
                    Frequency.ONCE -> date
                    Frequency.DAILY -> getString(R.string.repeats_daily, date)
                    Frequency.WEEKDAY -> getString(R.string.repeats_weekday, date)
                    Frequency.WEEKLY -> getString(R.string.repeats_weekly, date)
                    Frequency.MONTHLY -> getString(R.string.repeats_monthly, date)
                    Frequency.YEARLY -> getString(R.string.repeats_yearly, date)
                }
                binding.Reminder.visibility = View.VISIBLE
                binding.DateCreated.updatePadding(bottom = 0)
            } else {
                binding.Reminder.visibility = View.GONE
                binding.DateCreated.updatePadding(bottom = padding)
            }
        })

        binding.Reminder.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setItems(R.array.reminder) { _, which ->
                    when (which) {
                        0 -> displayReminderDialog()
                        1 -> model.deleteReminder()
                    }
                }
                .show()
        }
    }

    private fun displayReminderDialog() {
        val dialogBinding = DialogReminderBinding.inflate(layoutInflater)

        var selectedYear = -1
        var selectedMonth = -1
        var selectedDay = -1
        var selectedHour = -1
        var selectedMinute = -1

        val dateFormatter = DateFormat.getDateInstance(DateFormat.FULL)
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

        val dateListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            selectedYear = year
            selectedMonth = month
            selectedDay = dayOfMonth
            val calendar = Calendar.getInstance()
            calendar.clear()
            calendar.set(selectedYear, selectedMonth, selectedDay)
            dialogBinding.Date.text = dateFormatter.format(calendar.timeInMillis)
        }
        val timeListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            selectedHour = hourOfDay
            selectedMinute = minute
            val calendar = Calendar.getInstance()
            calendar.clear()
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            calendar.set(Calendar.MINUTE, selectedMinute)
            dialogBinding.Time.text = timeFormatter.format(calendar.timeInMillis)
        }
        dialogBinding.Date.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = if (selectedYear != -1) selectedYear else calendar.get(Calendar.YEAR)
            val month = if (selectedMonth != -1) selectedMonth else calendar.get(Calendar.MONTH)
            val day = if (selectedDay != -1) selectedDay else calendar.get(Calendar.DAY_OF_MONTH)
            val dialog = DatePickerDialog(this, dateListener, year, month, day)
            dialog.datePicker.minDate = calendar.timeInMillis
            dialog.show()
        }
        dialogBinding.Time.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = if (selectedHour != -1) selectedHour else calendar.get(Calendar.HOUR_OF_DAY)
            val minute = if (selectedMinute != -1) selectedMinute else calendar.get(Calendar.MINUTE)
            TimePickerDialog(this, timeListener, hour, minute, false).show()
        }

        // 加载现有提醒设置（如果有）
        var selectedFrequency = 0
        val labels = resources.getStringArray(R.array.frequencies)
        val existingReminder = model.reminder.value
        if (existingReminder != null) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = existingReminder.timestamp
            selectedYear = calendar.get(Calendar.YEAR)
            selectedMonth = calendar.get(Calendar.MONTH)
            selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
            selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
            selectedMinute = calendar.get(Calendar.MINUTE)
            
            // 设置日期和时间显示
            dialogBinding.Date.text = dateFormatter.format(existingReminder.timestamp)
            dialogBinding.Time.text = timeFormatter.format(existingReminder.timestamp)
            
            // 设置频率
            selectedFrequency = Frequency.entries.indexOf(existingReminder.frequency)
        }
        
        dialogBinding.Frequency.text = labels[selectedFrequency]
        dialogBinding.Frequency.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_frequency)
                .setSingleChoiceItems(labels, selectedFrequency) { dialog, which ->
                    selectedFrequency = which
                    dialogBinding.Frequency.text = labels[selectedFrequency]
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.set_reminder)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                if (selectedYear != -1 && selectedHour != -1) {
                    val frequency = Frequency.entries[selectedFrequency]
                    val calendar = Calendar.getInstance()
                    calendar.clear()
                    calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
                    val reminder = Reminder(calendar.timeInMillis, frequency)
                    model.setReminder(reminder)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }


    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }

        val menu = binding.Toolbar.menu
        val pin = menu.add(0, 0, 0, R.string.pin)
        pin.setIcon(R.drawable.pin)
        pin.setOnMenuItemClickListener { pin(it) }
        bindPinned(pin)

        val share = menu.add(0, 0, 0, R.string.share)
        share.setOnMenuItemClickListener { share(); true }

        val labels = menu.add(0, 0, 0, R.string.labels)
        labels.setOnMenuItemClickListener { label(); true }

        val addImages = menu.add(0, 0, 0, R.string.add_images)
        addImages.setOnMenuItemClickListener { selectImages(); true }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val recordAudio = menu.add(0, 0, 0, R.string.record_audio)
            recordAudio.setOnMenuItemClickListener { checkAudioPermission(); true }
        }

        val setReminder = menu.add(0, 0, 0, R.string.set_reminder)
        setReminder.setOnMenuItemClickListener { checkNotificationPermission(); true }

        when (model.folder) {
            Folder.NOTES -> {
                val delete = menu.add(0, 0, 0, R.string.delete)
                delete.setOnMenuItemClickListener { delete(); true }

                val archive = menu.add(0, 0, 0, R.string.archive)
                archive.setOnMenuItemClickListener { archive(); true }
            }
            Folder.DELETED -> {
                val restore = menu.add(0, 0, 0, R.string.restore)
                restore.setOnMenuItemClickListener { restore(); true }

                val deleteForever = menu.add(0, 0, 0, R.string.delete_forever)
                deleteForever.setOnMenuItemClickListener { deleteForever(); true }
            }
            Folder.ARCHIVED -> {
                val delete = menu.add(0, 0, 0, R.string.delete)
                delete.setOnMenuItemClickListener { delete(); true }

                val unarchive = menu.add(0, 0, 0, R.string.unarchive)
                unarchive.setOnMenuItemClickListener { restore(); true }
            }
        }
    }

    private fun initialiseBinding() {
        binding = ActivityMymemoBinding.inflate(layoutInflater)
        when (type) {
            Type.NOTE -> {
                binding.AddItem.visibility = View.GONE
                binding.RecyclerView.visibility = View.GONE
            }
            Type.LIST -> {
                binding.EnterBody.visibility = View.GONE
            }
        }

        val title = TextSize.getEditTitleSize(model.textSize)
        val date = TextSize.getDisplayBodySize(model.textSize)
        val body = TextSize.getEditBodySize(model.textSize)

        binding.EnterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
        binding.DateCreated.setTextSize(TypedValue.COMPLEX_UNIT_SP, date)
        binding.Reminder.setTextSize(TypedValue.COMPLEX_UNIT_SP, date)
        binding.EnterBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

        binding.root.isSaveFromParentEnabled = false
    }

    private fun setupObservers() {
        model.labels.observe(this, Observer { labels ->
            Operations.bindLabels(binding.LabelGroup, labels, TextSize.getDisplayBodySize(model.textSize))
        })

        setupColor()
        setupImages()
        setupAudios()
        setupReminder()
    }

    private fun bindPinned(item: MenuItem) {
        val icon: Int
        val title: Int
        if (model.pinned) {
            icon = R.drawable.unpin
            title = R.string.unpin
        } else {
            icon = R.drawable.pin
            title = R.string.pin
        }
        item.setTitle(title)
        item.setIcon(icon)
    }

    private fun pin(item: MenuItem): Boolean {
        model.pinned = !model.pinned
        bindPinned(item)
        return true
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 32
        private const val REQUEST_AUDIO_PERMISSION = 36
    }
}
