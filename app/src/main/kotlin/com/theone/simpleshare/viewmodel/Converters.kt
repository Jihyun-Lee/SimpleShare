package com.theone.simpleshare.viewmodel

import android.bluetooth.BluetoothDevice
import android.os.Parcel
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@ProvidedTypeConverter
class Converters {
    @TypeConverter
    fun fromBluetoothDeviceToString(device:BluetoothDevice) : ByteArray{
        val p = Parcel.obtain()
        p.writeValue(device)
        return p.marshall()
    }
    @TypeConverter
    fun fromStringToBluetoothDevice( data : ByteArray ): BluetoothDevice {
        val p = Parcel.obtain()
        p.unmarshall(data, 0, data.size)
        p.setDataPosition(0)

        return p.readValue(BluetoothDevice::class.java.classLoader) as BluetoothDevice
    }
}