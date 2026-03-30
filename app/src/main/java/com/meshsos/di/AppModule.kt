package com.meshsos.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.meshsos.data.db.MeshDatabase
import com.meshsos.data.db.dao.MeshEventDao
import com.meshsos.data.db.dao.PendingPacketDao
import com.meshsos.data.db.dao.ProcessedIdDao
import com.meshsos.data.transport.BleGattTransport
import com.meshsos.data.transport.NearbyConnectionsTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mesh_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Device identity ───────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("deviceId")
    fun provideDeviceId(@ApplicationContext context: Context): String {
        val prefs = context.getSharedPreferences("mesh_identity", Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: run {
            val newId = UUID.randomUUID().toString().take(8).uppercase()
            prefs.edit().putString("device_id", newId).apply()
            newId
        }
    }

    @Provides
    @Singleton
    @Named("deviceName")
    fun provideDeviceName(@ApplicationContext context: Context, @Named("deviceId") deviceId: String): String {
        return "Device-${android.os.Build.MODEL.take(6)}-${deviceId.take(3)}"
    }

    // ── Server config ─────────────────────────────────────────────────────────
    // Replace with your actual server URL before building

    @Provides
    @Singleton
    @Named("serverBaseUrl")
    fun provideServerBaseUrl(): String = "https://sosmesh.onrender.com/"

    // ── Database ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMeshDatabase(@ApplicationContext context: Context): MeshDatabase =
        Room.databaseBuilder(context, MeshDatabase::class.java, "mesh_sos.db")
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun providePendingPacketDao(db: MeshDatabase): PendingPacketDao = db.pendingPacketDao()

    @Provides
    @Singleton
    fun provideProcessedIdDao(db: MeshDatabase): ProcessedIdDao = db.processedIdDao()

    @Provides
    @Singleton
    fun provideMeshEventDao(db: MeshDatabase): MeshEventDao = db.meshEventDao()

    // ── Transports ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideNearbyTransport(
        @ApplicationContext context: Context,
        @Named("deviceId") deviceId: String,
        @Named("deviceName") deviceName: String
    ): NearbyConnectionsTransport = NearbyConnectionsTransport(context, deviceId, deviceName)

    @Provides
    @Singleton
    fun provideBleGattTransport(
        @ApplicationContext context: Context
    ): BleGattTransport = BleGattTransport(context)

    // ── DataStore ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

}
