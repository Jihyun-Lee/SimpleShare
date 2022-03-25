package com.theone.simpleshare.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.room.*

@Entity(tableName = "item_table")
data class Item(
    @PrimaryKey(autoGenerate = true)
    var itemId:Int,
    var resourceId: Int,
    @ColumnInfo(name = "device_name")
    var name: String?,
    @ColumnInfo(name = "device_address")
    var address: String,
    @ColumnInfo(name = "device_object")
    var device: BluetoothDevice?,
    var state: Int,
    var batteryLevel: Int,

)