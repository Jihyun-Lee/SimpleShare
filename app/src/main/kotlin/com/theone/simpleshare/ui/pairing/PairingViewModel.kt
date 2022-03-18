package com.theone.simpleshare.ui.pairing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList

class PairingViewModel : ViewModel() {
    private val mText: MutableLiveData<String>
    private val mItemList: MutableLiveData<ArrayList<Item>>
    val text: LiveData<String>
        get() = mText
    val list: LiveData<ArrayList<Item>>
        get() = mItemList

    init {
        mText = MutableLiveData()
        mText.value = "This is pairing fragment"
        mItemList = MutableLiveData()
        mItemList.value = ArrayList<Item>()
    }
}