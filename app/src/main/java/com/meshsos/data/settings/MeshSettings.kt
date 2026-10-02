package com.meshsos.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** User-configurable mesh behaviour, persisted in DataStore. */
@Singleton
class MeshSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val autoRelayKey = booleanPreferencesKey("auto_relay")

    /** When false, this device still shows and uploads received SOS but does not forward them. */
    val autoRelayEnabled: StateFlow<Boolean> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[autoRelayKey] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    suspend fun setAutoRelayEnabled(enabled: Boolean) {
        dataStore.edit { it[autoRelayKey] = enabled }
    }

    /** Whether the user wants the background relay running (restored after reboot). */
    var serviceEnabled: Boolean
        get() = isServiceEnabled(context)
        set(value) = setServiceEnabled(context, value)

    companion object {
        const val SERVICE_PREFS = "mesh_service"
        const val KEY_SERVICE_ENABLED = "service_enabled"

        /** For components that cannot use injection (e.g. BootReceiver). */
        fun isServiceEnabled(context: Context): Boolean =
            context.getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, true)

        fun setServiceEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        }
    }
}
