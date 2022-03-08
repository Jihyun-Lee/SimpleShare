package com.theone.simpleshare.ui.paired;

import android.bluetooth.BluetoothDevice;

public class PairedItem {
    String name;
    String address;
    int resourceId;
    BluetoothDevice device;
    int batteryLevel;
    int state;
    public PairedItem(int resourceId, String name, String message, BluetoothDevice device, int state, int batteryLevel) {
        this.name = name;
        this.address = message;
        this.resourceId = resourceId;
        this.device = device;
        this.state = state;
        this.batteryLevel = batteryLevel;
    }

    public int getResourceId() {
        return resourceId;
    }
    public int getState() {
        return state;
    }
    public void setState(int state) {
        this.state = state;
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public void setAddress(String message) {
        this.address = message;
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
