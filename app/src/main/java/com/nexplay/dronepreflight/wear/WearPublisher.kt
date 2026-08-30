package com.nexplay.dronepreflight.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nexplay.dronepreflight.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Wysyła snapshot na sparowany zegarek Wear OS przez Data Layer API. */
object WearPublisher {

    private const val PATH = "/nexdrone/snapshot"

    suspend fun publish(context: Context, snap: SettingsStore.WidgetSnapshot) {
        runCatching {
            withContext(Dispatchers.IO) {
                val request = PutDataMapRequest.create(PATH).apply {
                    dataMap.putString("verdict", snap.verdict)
                    dataMap.putString("temp", snap.tempC)
                    dataMap.putString("wind", snap.windMs)
                    dataMap.putString("kp", snap.kp)
                    dataMap.putString("location", snap.location)
                    dataMap.putLong("updated_at", snap.updatedAt)
                    // Timestamp force-update - inaczej ten sam DataMap = nie propaguje się.
                    dataMap.putLong("nonce", System.currentTimeMillis())
                }
                val putReq = request.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(putReq).await()
            }
        }
    }
}
