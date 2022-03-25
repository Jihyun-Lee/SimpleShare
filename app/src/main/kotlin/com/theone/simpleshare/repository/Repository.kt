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

    fun insertItem(item : Item){
        itemDao.insertItem(item)
    }

    fun deleteItem(name :String){
        itemDao.deleteItem(name)
    }
    fun deleteAll(){
        itemDao.deleteAll()
    }

    //fun getItemList() : LiveData<List<Item>> {
    fun getItemList() : List<Item> {
//        val list = itemDao.getItemList()
//        if( list == null){
//            Log.d("easy", "easy list empty" )
//            val item = Item(-1,-1,"empty","empty"
//                ,null,-1,-1)
//            val al = ArrayList<Item>()
//            al.add(item)
//            return MutableLiveData<List<Item>>(al)
//        }
        return itemDao.getItemList()
    }
}