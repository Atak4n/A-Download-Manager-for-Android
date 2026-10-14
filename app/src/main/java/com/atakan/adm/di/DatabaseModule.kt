package com.atakan.adm.di

import android.content.Context
import androidx.room.Room
import com.atakan.adm.data.db.AdmDatabase
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.data.db.SegmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AdmDatabase =
        Room.databaseBuilder(context, AdmDatabase::class.java, AdmDatabase.NAME)
            .build()

    @Provides
    fun provideDownloadDao(db: AdmDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideSegmentDao(db: AdmDatabase): SegmentDao = db.segmentDao()
}
