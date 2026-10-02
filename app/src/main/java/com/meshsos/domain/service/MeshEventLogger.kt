package com.meshsos.domain.service

import android.util.Log
import com.meshsos.data.db.dao.MeshEventDao
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.domain.model.MeshEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MeshEventLogger"
private const val RETENTION_SECONDS = 24 * 3600L
private const val PURGE_EVERY_N_EVENTS = 50

/**
 * Persists mesh events for the Logs / Debug screens. Writes happen on an app-wide scope,
 * so events are recorded no matter which component (service, view model) produced them.
 */
@Singleton
class MeshEventLogger @Inject constructor(
    private val meshEventDao: MeshEventDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = AtomicInteger(0)

    fun log(
        type: MeshEventType,
        message: String,
        packetId: String? = null,
        deviceId: String? = null
    ) {
        Log.d(TAG, "[$type] $message")
        val event = MeshEventEntity(
            timestamp = Instant.now().epochSecond,
            eventType = type.name,
            message = message,
            packetId = packetId,
            deviceId = deviceId
        )
        scope.launch {
            runCatching {
                meshEventDao.insert(event)
                if (writes.incrementAndGet() % PURGE_EVERY_N_EVENTS == 0) {
                    meshEventDao.deleteOlderThan(Instant.now().epochSecond - RETENTION_SECONDS)
                }
            }.onFailure { Log.w(TAG, "Failed to persist event: ${it.message}") }
        }
    }
}
