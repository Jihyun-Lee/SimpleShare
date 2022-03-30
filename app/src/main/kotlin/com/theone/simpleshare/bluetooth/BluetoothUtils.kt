package com.theone.simpleshare.bluetooth

import kotlin.jvm.Synchronized
import android.os.HandlerThread
import android.bluetooth.BluetoothDevice
import com.theone.simpleshare.bluetooth.BluetoothUtils
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.le.BluetoothLeScanner
import com.theone.simpleshare.bluetooth.BluetoothChatService
import com.theone.simpleshare.bluetooth.TestTaskQueue
import com.theone.simpleshare.bluetooth.BatteryLevelReader
import android.content.Intent
import com.theone.simpleshare.bluetooth.BatteryLevelReader.GattBatteryCallbacks
import android.os.IBinder
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattCallback
import android.widget.Toast
import android.os.Build
import android.content.IntentFilter
import com.theone.simpleshare.bluetooth.BleCocClientService
import android.bluetooth.le.ScanCallback
import android.os.ParcelUuid
import com.theone.simpleshare.bluetooth.BleCocServerService
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.bluetooth.BluetoothGattServer
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertiseCallback
import com.theone.simpleshare.bluetooth.BluetoothChatService.AcceptThread
import com.theone.simpleshare.bluetooth.BluetoothChatService.ConnectThread
import com.theone.simpleshare.bluetooth.BluetoothChatService.ConnectedThread
import android.os.Bundle
import com.theone.simpleshare.bluetooth.BluetoothPairingService
import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile.ServiceListener
import android.bluetooth.BluetoothA2dp
import com.theone.simpleshare.R

object BluetoothUtils {
    val MINOR_DEVICE_CLASS_POINTING = "0000010000000".toInt(2)
    val MINOR_DEVICE_CLASS_JOYSTICK = "0000000000100".toInt(2)
    val MINOR_DEVICE_CLASS_GAMEPAD = "0000000001000".toInt(2)
    val MINOR_DEVICE_CLASS_KEYBOARD = "0000001000000".toInt(2)
    val MINOR_DEVICE_CLASS_REMOTE = "0000000001100".toInt(2)
    @JvmStatic
    fun isInputDevice(device: BluetoothDevice): Boolean {
        val devClass = device.bluetoothClass.deviceClass
        if (devClass and MINOR_DEVICE_CLASS_POINTING != 0) {
            return true
        } else if (devClass and MINOR_DEVICE_CLASS_JOYSTICK != 0) {
            return true
        } else if (devClass and MINOR_DEVICE_CLASS_GAMEPAD != 0) {
            return true
        } else if (devClass and MINOR_DEVICE_CLASS_KEYBOARD != 0) {
            return true
        } else if (devClass and MINOR_DEVICE_CLASS_REMOTE != 0) {
            return true
        }
        return false
    }

    @JvmStatic
    fun isA2dpDevice(device: BluetoothDevice): Boolean {
        val devClass = device.bluetoothClass.deviceClass
        return if (devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET || devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES || devClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER || devClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO || devClass == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO) true else false
    }
    @JvmStatic
    fun getResourceIcon(device: BluetoothDevice): Int {
        var res = R.drawable.ic_baseline_bluetooth_24;
        if(isA2dpDevice(device))
            res = R.drawable.ic_baseline_headset_24
        //todo add hid device
        return res
    }

    const val HID_HOST = 4
}