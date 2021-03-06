package com.theone.simpleshare.viewmodel


import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.theone.simpleshare.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.ArrayList
import javax.inject.Inject


@HiltViewModel
class ItemViewModel
@Inject constructor(
    var repository: Repository
) : ViewModel() {

    fun insertItem(item : Item) =
        repository.insertItem(item)

    fun deleteItem(name : String) =
        repository.deleteItem(name)

    fun deleteAll():Unit =
        repository.deleteAll()

    fun getItemList() : LiveData<List<Item>> =
        repository.getItemList()
}