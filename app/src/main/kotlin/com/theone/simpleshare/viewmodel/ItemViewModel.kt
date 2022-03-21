package com.theone.simpleshare.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList

class ItemViewModel : ViewModel() {
    private val mText: MutableLiveData<String>
    private val mItemList: MutableLiveData<ArrayList<Item>>
    val text: LiveData<String>
        get() = mText
    val list: LiveData<ArrayList<Item>>
        get() = mItemList

    init {
        mText = MutableLiveData()
        mText.value = "This is home fragment"
        mItemList = MutableLiveData()
        mItemList.value = ArrayList<Item>()
    }
}