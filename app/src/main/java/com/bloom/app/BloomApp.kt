package com.bloom.app

import android.app.Application
import com.bloom.app.data.BloomRepository
import com.bloom.app.notify.Reminders
import com.bloom.app.widget.WidgetUpdater

class BloomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val repo = BloomRepository.get(this)
        // Every data change refreshes widgets and re-plans reminders.
        repo.onChanged = {
            WidgetUpdater.refreshAll(this)
            Reminders.reschedule(this, repo.snapshot())
        }
        WidgetUpdater.scheduleMidnight(this)
        Reminders.createChannels(this)
    }
}
