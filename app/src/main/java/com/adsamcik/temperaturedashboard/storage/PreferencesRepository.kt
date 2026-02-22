package com.adsamcik.temperaturedashboard.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adsamcik.temperaturedashboard.data.TemperatureUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {

    private val temperatureUnitKey = stringPreferencesKey("temperature_unit")

    val temperatureUnit: Flow<TemperatureUnit> = context.dataStore.data.map { prefs ->
        val name = prefs[temperatureUnitKey] ?: TemperatureUnit.CELSIUS.name
        TemperatureUnit.valueOf(name)
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { prefs ->
            prefs[temperatureUnitKey] = unit.name
        }
    }
}
