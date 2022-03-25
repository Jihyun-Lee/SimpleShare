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
    private var mItemList: LiveData<List<Item>> =MutableLiveData(ArrayList<Item>())
    //private lateinit var repository:Repository

//
//    @ViewModelInject
//    constructor( repository: Repository ) : this(){
//        Log.d("easy", "constructor of viewmodel")
//        this.repository = repository
//        //this.mItemList = MutableLiveData(ArrayList<Item>())
//    }

    fun insertItem(item : Item){
        repository.insertItem(item)
    }
    fun deleteItem(name : String){
        repository.deleteItem(name)
    }

    fun deleteAll():Unit{
        repository.deleteAll()
    }

    fun getItemList() : LiveData<List<Item>>{
        return MutableLiveData<List<Item>>(repository.getItemList())
    }
}