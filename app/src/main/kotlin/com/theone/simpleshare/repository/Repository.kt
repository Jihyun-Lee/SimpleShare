package com.theone.simpleshare.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.theone.simpleshare.db.ItemDao
import com.theone.simpleshare.viewmodel.Item
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.components.ActivityComponent
import javax.inject.Inject

class Repository @Inject constructor(private val itemDao: ItemDao) {

    fun insertItem(item : Item) =
        itemDao.insertItem(item)

    fun deleteItem(name :String) =
        itemDao.deleteItem(name)

    fun deleteAll() =
        itemDao.deleteAll()

    //refer to following links https://stackoverflow.com/questions/44428389/livedata-getvalue-returns-null-with-room
    //read value async by query so use observer to read data from LiveData
    fun getItemList() : LiveData<List<Item>> =
        itemDao.getItemList()

}