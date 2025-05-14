package org.muilab.notigpt.reciever

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.util.postOngoingNotification

class NotiActionReciever : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            val drawerDatabase = DrawerDatabase.getInstance(context)
            val drawerDao = drawerDatabase.drawerDao()
            val notReadNotis = drawerDao.getnotReadNotis()
            when (intent.action) {
                "read_all" -> {
                    notReadNotis.forEachIndexed { idx, _ ->
                        notReadNotis[idx].markAsRead()
                    }
                }
                "pin_all" -> {
                    notReadNotis.forEachIndexed { idx, _ ->
                        notReadNotis[idx].isPinned = true
                    }
                }
            }
            drawerDao.updateList(notReadNotis)
            postOngoingNotification(context)
        }
    }
}