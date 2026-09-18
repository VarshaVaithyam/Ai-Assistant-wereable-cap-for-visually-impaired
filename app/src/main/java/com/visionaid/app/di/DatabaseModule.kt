package com.visionaid.app.di

import android.content.Context
import androidx.room.Room
import com.visionaid.app.data.local.database.AppDatabase
import com.visionaid.app.data.local.database.ContactDao
import com.visionaid.app.data.local.database.FallLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Room database + DAO providers (spec 15.2). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "visionaid.db"
        )
            .fallbackToDestructiveMigration() // v1: simple schema, acceptable
            .build()

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideFallLogDao(database: AppDatabase): FallLogDao = database.fallLogDao()
}
