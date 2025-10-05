package com.security.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACKNOWLEDGE_ALARM" -> {
                Log.d("NotificationAction", "Alarm acknowledged")
                
                // Cancel the notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(0)
                
                // Show acknowledgment message
                Toast.makeText(context, "Alarm acknowledged", Toast.LENGTH_SHORT).show()
                
                // You can add logic here to update Firestore or send acknowledgment to your server
            }
        }
    }
}