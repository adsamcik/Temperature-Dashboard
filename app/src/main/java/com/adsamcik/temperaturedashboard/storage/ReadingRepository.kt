package com.adsamcik.temperaturedashboard.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ReadingRepository(private val readingDao: ReadingDao) {
    suspend fun saveReadings(readings: List<TemperatureReading>) = withContext(Dispatchers.IO) {
        readingDao.insertReadings(readings)
    }

    suspend fun getLatestReading(mac: String): TemperatureReading? = withContext(Dispatchers.IO) {
        readingDao.getLatestReading(mac)
    }

    fun getReadingsSince(mac: String, since: Long): Flow<List<TemperatureReading>> {
        return readingDao.getReadingsSince(mac, since)
    }

    fun getAllReadings(mac: String): Flow<List<TemperatureReading>> {
        return readingDao.getAllReadings(mac)
    }
}
