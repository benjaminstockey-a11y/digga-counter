package com.example.diggacounter.listening

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.diggacounter.data.AppDatabase
import com.example.diggacounter.data.PersonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles a tap on one of the "War <Name>" notification action buttons: books 50 cents
 * to that person and dismisses the prompt notification. */
class DiggaAttributionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (personId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = PersonRepository(AppDatabase.get(context).personDao())
                repository.registerDigga(personId)
            } finally {
                if (notificationId >= 0) {
                    context.getSystemService(NotificationManager::class.java)
                        ?.cancel(notificationId)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
