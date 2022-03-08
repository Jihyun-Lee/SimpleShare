package com.theone.simpleshare.ui.paired;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

public class PairedViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    private final MutableLiveData<ArrayList<PairedItem>> mItemList;
    public PairedViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is home fragment");

        mItemList = new MutableLiveData<>();
        mItemList.setValue( new ArrayList<>());
    }

    public LiveData<String> getText() {
        return mText;
    }

    public LiveData<ArrayList<PairedItem>> getList(){
        return mItemList;
    }

}