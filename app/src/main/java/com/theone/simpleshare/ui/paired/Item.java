package com.theone.simpleshare.ui.paired;

import android.bluetooth.BluetoothDevice;

public class Item {
    String name;
    String message;
    int resourceId;
    BluetoothDevice device;

    public Item(int resourceId, String name, String message, BluetoothDevice device) {
        this.name = name;
        this.message = message;
        this.resourceId = resourceId;
        this.device = device;
    }

    public int getResourceId() {
        return resourceId;
    }

    public String getMessage() {
        return message;
    }

    public String getName() {
        return name;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setResourceId(int resourceId) {
        this.resourceId = resourceId;
    }

    public void setDevice( BluetoothDevice device ){ this.device = device;}
    public BluetoothDevice getDevice( ){ return device;}
}
