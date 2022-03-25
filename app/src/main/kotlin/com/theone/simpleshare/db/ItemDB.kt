package com.theone.simpleshare.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.theone.simpleshare.viewmodel.Converters
import com.theone.simpleshare.viewmodel.Item

@Database( entities = [Item::class], version=4 , exportSchema = false)
@TypeConverters(Converters::class)
abstract class ItemDB : RoomDatabase(){
    abstract fun itemDao() :ItemDao
}