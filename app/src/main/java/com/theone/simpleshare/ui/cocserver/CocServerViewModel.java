package com.theone.simpleshare.ui.cocserver;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CocServerViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public CocServerViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is dashboard fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}