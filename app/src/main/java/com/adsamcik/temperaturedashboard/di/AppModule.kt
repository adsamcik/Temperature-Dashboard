package com.adsamcik.temperaturedashboard.di

import android.content.Context
import com.adsamcik.temperaturedashboard.storage.DeviceDao
import com.adsamcik.temperaturedashboard.storage.DeviceDatabase
import com.adsamcik.temperaturedashboard.storage.DeviceRepository
import com.adsamcik.temperaturedashboard.storage.ReadingDao
import com.adsamcik.temperaturedashboard.storage.ReadingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DeviceDatabase {
        return DeviceDatabase.getDatabase(context)
    }

    @Provides
    fun provideDeviceDao(database: DeviceDatabase): DeviceDao {
        return database.deviceDao()
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(deviceDao: DeviceDao): DeviceRepository {
        return DeviceRepository(deviceDao)
    }

    @Provides
    fun provideReadingDao(database: DeviceDatabase): ReadingDao {
        return database.readingDao()
    }

    @Provides
    @Singleton
    fun provideReadingRepository(readingDao: ReadingDao): ReadingRepository {
        return ReadingRepository(readingDao)
    }
}
