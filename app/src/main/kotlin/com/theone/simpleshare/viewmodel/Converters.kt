package com.theone.simpleshare.viewmodel

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@ProvidedTypeConverter
class Converters {
    @TypeConverter
    fun fromBluetoothDeviceToString(device:BluetoothDevice) : String{
        val gson = Gson()

        //return gson.toJson(device)
        val str = gson.toJson(device)
        
        Log.d("easy","[1]dbg : "+ str)
        return str
    }
    @TypeConverter
    fun fromStringToBluetoothDevice( data : String ): BluetoothDevice {
        val gson = Gson()
        val objType = object : TypeToken<BluetoothDevice>(){

        }.type
        Log.d("easy","[2]dbg : "+ data)
        return gson.fromJson(data, objType)
    }
}