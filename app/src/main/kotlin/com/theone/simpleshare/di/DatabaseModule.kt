package com.theone.simpleshare.di

import android.app.Application
import androidx.room.Room
import com.theone.simpleshare.db.ItemDB
import com.theone.simpleshare.db.ItemDao
import com.theone.simpleshare.viewmodel.Converters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {
    @Singleton
    @Provides
    fun provideItemDao(itemDB:ItemDB):ItemDao = itemDB.itemDao()

    @Singleton
    @Provides
    fun provideItemDB(application : Application): ItemDB = Room.databaseBuilder(application, ItemDB::class.java, "item_database")
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .addTypeConverter(Converters())
            .build()
}