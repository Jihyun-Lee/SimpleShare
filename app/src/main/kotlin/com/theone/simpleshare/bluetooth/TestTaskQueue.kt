/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theone.simpleshare.bluetooth

import kotlin.jvm.Synchronized
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
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattCallback
import android.widget.Toast
import android.content.IntentFilter
import com.theone.simpleshare.bluetooth.BleCocClientService
import android.bluetooth.le.ScanCallback
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
import com.theone.simpleshare.bluetooth.BluetoothPairingService
import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile.ServiceListener
import android.bluetooth.BluetoothA2dp
import android.os.*
import java.util.ArrayList

/**
 * TestTaskQueue runs asynchronous operations on background thread.
 *
 * TestTaskQueue holds Handler which runs on background thread.
 * Asynchronous operations will be run by adding operation by addTask().
 * Each operations will be managed by Handler.
 */
class TestTaskQueue(threadName: String?) {
    private var mHandler: Handler?
    private val mTasks = ArrayList<Runnable>()

    /**
     * Cancels all pending operations.
     */
    @Synchronized
    fun quit() {
        // cancel all pending operations.
        for (task in mTasks) {
            mHandler!!.removeCallbacks(task)
        }
        mTasks.clear()

        // terminate Handler
        mHandler!!.looper.quit()
        mHandler = null
    }

    /**
     * Reserves new asynchronous operation.
     * Operations will be run sequentially.
     *
     * @param r new operation
     */
    @Synchronized
    fun addTask(r: Runnable?) {
        addTask(r, 0)
    }

    /**
     * Reserves new asynchronous operation.
     * Operations will be run sequentially.
     *
     * @param r new operation
     * @param delay delay for execution
     */
    @Synchronized
    fun addTask(r: Runnable?, delay: Long) {
        if (mHandler != null && r != null) {
            val task: Runnable = object : Runnable {
                override fun run() {
                    mTasks.remove(this)
                    r.run()
                }
            }
            mTasks.add(task)
            mHandler!!.postDelayed(task, delay)
        }
    }

    init {
        val th = HandlerThread(threadName)
        th.start()
        mHandler = Handler(th.looper)
    }
}