package com.theone.simpleshare.viewmodel

import android.bluetooth.BluetoothDevice

class Item(
    var resourceId: Int,
    var name: String?,
    var address: String,
    var device: BluetoothDevice,
    var state: Int,
    var batteryLevel: Int
)