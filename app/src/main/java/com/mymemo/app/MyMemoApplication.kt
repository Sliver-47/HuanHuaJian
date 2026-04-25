package com.mymemo.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.mymemo.app.preferences.Preferences
import com.mymemo.app.preferences.Theme
import java.util.concurrent.TimeUnit

class MyMemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val preferences = Preferences.getInstance(this)
        preferences.theme.observeForever { theme ->
            when (theme) {
                Theme.dark -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Theme.light -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Theme.followSystem -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        // 延迟启动自动备份工作，加快应用启动速度
        Thread {
            try {
                Thread.sleep(1000)
                val request = PeriodicWorkRequest.Builder(AutoBackupWorker::class.java, 12, TimeUnit.HOURS).build()
                WorkManager.getInstance(this).enqueueUniquePeriodicWork("Auto Backup", ExistingPeriodicWorkPolicy.KEEP, request)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }.start()
    }
}




