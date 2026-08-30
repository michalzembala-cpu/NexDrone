package com.nexplay.dronepreflight.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Odbiera DataItem-y z telefonu i zapisuje do lokalnego cache-u. */
class NexDroneWearListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != "/nexdrone/snapshot") continue
            val map = DataMapItem.fromDataItem(item).dataMap
            val cache = WearSnapshotCache(applicationContext)
            cache.write(
                WearSnapshotCache.Snapshot(
                    verdict = map.getString("verdict") ?: "—",
                    tempC = map.getString("temp") ?: "—",
                    windMs = map.getString("wind") ?: "—",
                    kp = map.getString("kp") ?: "—",
                    location = map.getString("location") ?: "Brak danych",
                    updatedAt = map.getLong("updated_at"),
                )
            )
        }
    }
}
