package com.theone.simpleshare.ui.paired

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList

class PairedViewModel : ViewModel() {
    private val mText: MutableLiveData<String>
    private val mItemList: MutableLiveData<ArrayList<PairedItem>>
    val text: LiveData<String>
        get() = mText
    val list: LiveData<ArrayList<PairedItem>>
        get() = mItemList

    init {
        mText = MutableLiveData()
        mText.value = "This is home fragment"
        mItemList = MutableLiveData()
        mItemList.value = ArrayList<PairedItem>()
    }
}