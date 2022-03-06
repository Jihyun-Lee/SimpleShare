package com.theone.simpleshare.ui.cocclient;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CocClientViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public CocClientViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is notifications fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}