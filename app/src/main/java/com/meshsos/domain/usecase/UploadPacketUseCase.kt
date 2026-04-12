package com.meshsos.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.meshsos.data.api.SosApiServiceFactory
import com.meshsos.data.api.UploadRequest
import com.meshsos.data.api.UploadResponse
import com.meshsos.data.db.dao.PendingPacketDao
import com.meshsos.data.db.entity.PendingPacketEntity
import com.meshsos.domain.model.SosPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class UploadPacketUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiServiceFactory: SosApiServiceFactory,
    private val pendingPacketDao: PendingPacketDao,
    @Named("serverBaseUrl") private val serverBaseUrl: String,
    @Named("deviceId") private val localDeviceId: String
) {
    private val api by lazy { apiServiceFactory.create(serverBaseUrl) }

    fun hasInternet(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun upload(packet: SosPacket): Result<UploadResponse> {
        return try {
            val response = api.uploadSos(
                UploadRequest(
                    packet = com.meshsos.data.api.SosPacketDto(
                        id = packet.id,
                        type = packet.type,
                        senderId = packet.senderId,
                        incident = packet.incident,
                        metadata = com.meshsos.data.api.PacketMetadataDto(
                            createdAt = packet.metadata.createdAt,
                            ttl = packet.metadata.ttl,
                            maxHops = packet.metadata.maxHops,
                            currentHops = packet.metadata.currentHops,
                            route = packet.metadata.route.map { it.deviceId },
                            batteryLevel = packet.metadata.batteryLevel
                        ),
                        uploaded = packet.uploaded,
                        uploadTimestamp = packet.uploadTimestamp
                    ),
                    relayDeviceId = localDeviceId,
                    relayLocation = packet.incident.location
                )
            )
            if (response.success) Result.success(response)
            else Result.failure(Exception("Server returned success=false"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val msg = if (!errorBody.isNullOrBlank()) "HTTP ${e.code()}: $errorBody" else e.message()
            persistToPendingQueue(packet)
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            // Persist to queue for retry later
            persistToPendingQueue(packet)
            Result.failure(e)
        }
    }

    /** Retry all pending packets that haven't expired */
    suspend fun retryPending() {
        if (!hasInternet()) return
        val now = Instant.now().epochSecond
        pendingPacketDao.deleteExpired(now)

        val pending = pendingPacketDao.getAll()
        for (entity in pending) {
            val packet = SosPacket.fromJson(entity.packetJson) ?: continue
            if (packet.isExpired()) {
                pendingPacketDao.delete(entity.id)
                continue
            }
            val result = upload(packet)
            if (result.isSuccess) {
                pendingPacketDao.delete(entity.id)
            } else {
                pendingPacketDao.incrementRetry(entity.id, now)
            }
        }
    }

    private suspend fun persistToPendingQueue(packet: SosPacket) {
        pendingPacketDao.insert(
            PendingPacketEntity(
                id = packet.id,
                packetJson = packet.toJson(),
                createdAt = packet.metadata.createdAt,
                ttl = packet.metadata.ttl
            )
        )
    }
}
