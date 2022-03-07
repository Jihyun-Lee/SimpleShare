package com.theone.simpleshare.ui.pairing;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

public class PairingViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    private final MutableLiveData<ArrayList<Item>> mItemList;
    public PairingViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is pairing fragment");

        mItemList = new MutableLiveData<>();
        mItemList.setValue(new ArrayList<>());
    }

    public LiveData<String> getText() {
        return mText;
    }
    public LiveData<ArrayList<Item>> getList(){
        return mItemList;
    }
}