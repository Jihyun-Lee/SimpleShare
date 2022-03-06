package com.theone.simpleshare.ui.pairing;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PairingViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public PairingViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is pairing fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}