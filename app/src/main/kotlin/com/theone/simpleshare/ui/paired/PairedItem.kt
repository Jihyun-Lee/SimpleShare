package com.theone.simpleshare.ui.paired

import android.bluetooth.BluetoothDevice

class PairedItem(
    var resourceId: Int,
    var name: String,
    var address: String,
    var device: BluetoothDevice,
    var state: Int,
    var batteryLevel: Int
)