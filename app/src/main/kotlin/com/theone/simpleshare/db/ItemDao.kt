package com.theone.simpleshare.db

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theone.simpleshare.viewmodel.Item
import dagger.Module
import dagger.Provides

@Dao
interface ItemDao {

    @Insert
    fun insertItem(item : Item)

    @Query("DELETE FROM item_table WHERE device_name = :name")
    fun deleteItem( name : String)

    @Query("DELETE FROM item_table")
    fun deleteAll() : Unit

    @Query("SELECT * FROM item_table")
    fun getItemList() : List<Item>
}