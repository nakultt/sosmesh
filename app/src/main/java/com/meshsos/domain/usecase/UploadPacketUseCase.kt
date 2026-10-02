package com.meshsos.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.meshsos.data.api.PacketMetadataDto
import com.meshsos.data.api.SosApiServiceFactory
import com.meshsos.data.api.SosPacketDto
import com.meshsos.data.api.UploadRequest
import com.meshsos.data.api.UploadResponse
import com.meshsos.data.db.dao.PendingPacketDao
import com.meshsos.data.db.entity.PendingPacketEntity
import com.meshsos.domain.model.LocationInfo
import com.meshsos.domain.model.SosPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "UploadPacketUseCase"

/** Thrown for 4xx responses: retrying the same request will not help. */
class PermanentUploadException(message: String) : Exception(message)

@Singleton
class UploadPacketUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiServiceFactory: SosApiServiceFactory,
    private val pendingPacketDao: PendingPacketDao,
    @Named("serverBaseUrl") private val serverBaseUrl: String,
    @Named("deviceId") private val localDeviceId: String
) {
    private val api by lazy { apiServiceFactory.create(serverBaseUrl) }
    private val retryLock = Mutex()

    fun hasInternet(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Uploads [packet]. On a transient failure the packet is queued for [retryPending].
     * [relayLocation] is the uploading device's own location (not the victim's).
     */
    suspend fun upload(packet: SosPacket, relayLocation: LocationInfo? = null): Result<UploadResponse> {
        val result = uploadOnce(packet, relayLocation)
        result.onFailure { error ->
            if (error is PermanentUploadException) {
                Log.w(TAG, "Server rejected packet ${packet.id}: ${error.message}")
            } else {
                persistToPendingQueue(packet)
            }
        }
        return result
    }

    private suspend fun uploadOnce(packet: SosPacket, relayLocation: LocationInfo?): Result<UploadResponse> {
        return try {
            val response = api.uploadSos(
                UploadRequest(
                    packet = SosPacketDto(
                        id = packet.id,
                        type = packet.type,
                        senderId = packet.senderId,
                        incident = packet.incident,
                        metadata = PacketMetadataDto(
                            createdAt = packet.metadata.createdAt,
                            ttl = packet.metadata.ttl,
                            maxHops = packet.metadata.maxHops,
                            currentHops = packet.metadata.currentHops,
                            route = packet.metadata.route,
                            batteryLevel = packet.metadata.batteryLevel
                        ),
                        uploaded = packet.uploaded,
                        uploadTimestamp = packet.uploadTimestamp
                    ),
                    relayDeviceId = localDeviceId,
                    relayLocation = relayLocation ?: packet.metadata.route.lastOrNull()?.location
                )
            )
            if (response.success && !response.alertId.isNullOrBlank()) Result.success(response)
            else Result.failure(Exception("Server returned success=false"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val msg = if (!errorBody.isNullOrBlank()) "HTTP ${e.code()}: $errorBody" else "HTTP ${e.code()}"
            if (e.code() in 400..499 && e.code() != 408 && e.code() != 429) {
                Result.failure(PermanentUploadException(msg))
            } else {
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retries every queued, non-expired packet.
     * Returns the packets that were uploaded successfully with their server responses.
     */
    suspend fun retryPending(): List<Pair<SosPacket, UploadResponse>> = retryLock.withLock {
        if (!hasInternet()) return@withLock emptyList()
        val now = Instant.now().epochSecond
        pendingPacketDao.deleteExpired(now)

        val uploaded = mutableListOf<Pair<SosPacket, UploadResponse>>()
        for (entity in pendingPacketDao.getAll()) {
            val packet = SosPacket.fromJson(entity.packetJson)
            if (packet == null || packet.isExpired()) {
                pendingPacketDao.delete(entity.id)
                continue
            }
            uploadOnce(packet, null)
                .onSuccess { response ->
                    pendingPacketDao.delete(entity.id)
                    uploaded += packet to response
                }
                .onFailure { error ->
                    if (error is PermanentUploadException) {
                        pendingPacketDao.delete(entity.id)
                    } else {
                        pendingPacketDao.incrementRetry(entity.id, now)
                    }
                }
        }
        uploaded
    }

    private suspend fun persistToPendingQueue(packet: SosPacket) {
        runCatching {
            pendingPacketDao.insert(
                PendingPacketEntity(
                    id = packet.id,
                    packetJson = packet.toJson(),
                    createdAt = packet.metadata.createdAt,
                    ttl = packet.metadata.ttl
                )
            )
        }.onFailure { Log.w(TAG, "Failed to queue packet ${packet.id}: ${it.message}") }
    }
}
