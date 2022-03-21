/*
 * Copyright 2018 The Android Open Source Project
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
import android.app.Service
import android.bluetooth.BluetoothProfile.ServiceListener
import android.bluetooth.BluetoothA2dp
import android.os.*
import android.util.Log
import java.util.*

//import com.android.cts.verifier.R;
class BleCocServerService : Service() {
    private lateinit var mBluetoothManager: BluetoothManager
    private lateinit var mGattServer: BluetoothGattServer
    private lateinit var mService: BluetoothGattService
    private lateinit var mDevice: BluetoothDevice
    private lateinit var mHandler: Handler
    private lateinit var mAdvertiser: BluetoothLeAdvertiser
    private var mSecure = false
    private val mMtuSize = -1
    private lateinit var mServerSocket: BluetoothServerSocket
    private var mPsm = -1
    private lateinit var mLePsmCharacteristic: BluetoothGattCharacteristic
    lateinit var mChatService: BluetoothChatService
    private var mNextReadExpectedLen = -1
    private lateinit var mNextReadCompletionIntent: String
    private var mTotalReadLen = 0
    private var mNextReadByte: Byte = 0
    private var mNextWriteExpectedLen = -1
    private lateinit var mNextWriteCompletionIntent: String

    // Handler for communicating task with peer.
    private lateinit var mTaskQueue: TestTaskQueue

    // current test category
    private lateinit var mCurrentAction: String

    // Task to notify failure of starting secure test.
    //   Secure test calls BluetoothDevice#createBond() when devices were not paired.
    //   createBond() causes onConnectionStateChange() twice, and it works as strange sequence.
    //   At the first onConnectionStateChange(), target device is not paired (bond state is
    //   BluetoothDevice.BOND_NONE).
    //   At the second onConnectionStateChange(), target devices is paired (bond state is
    //   BluetoothDevice.BOND_BONDED).
    //   CTS Verifier will perform lazy check of bond state. Verifier checks bond state
    //   after NOTIFICATION_DELAY_OF_SECURE_TEST_FAILURE from the first onConnectionStateChange().
    private var mNotificationTaskOfSecureTestStartFailure: Runnable? = null
    override fun onCreate() {
        super.onCreate()
        mTaskQueue = TestTaskQueue(javaClass.name + "_taskHandlerThread")
        mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mAdvertiser = mBluetoothManager.adapter.bluetoothLeAdvertiser
        mGattServer = mBluetoothManager.openGattServer(this, mCallbacks)
        mService = createService()

        mHandler = Handler(Looper.getMainLooper())
        if (!mBluetoothManager.adapter.isEnabled) {
            notifyBluetoothDisabled()
        } else if (mGattServer == null) {
            notifyOpenFail()
        } else if (mAdvertiser == null) {
            notifyAdvertiseUnsupported()
        } else {
            // start adding services
            mSecure = false
            if (!mGattServer.addService(mService)) {
                notifyAddServiceFail()
            }
        }
    }

    private fun notifyBluetoothDisabled() =
        sendBroadcast(Intent(BLE_BLUETOOTH_DISABLED))

    private fun notifyMismatchSecure() =
        sendBroadcast(Intent(BLE_BLUETOOTH_MISMATCH_SECURE))


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action
        if (action != null) {
            if (DEBUG) {
                Log.d(TAG, "onStartCommand: action=$action")
            }
            mTaskQueue.addTask({ onTestFinish(action) }, EXECUTION_DELAY.toLong())
        }
        return START_NOT_STICKY
    }

    private fun startServerTest(secure: Boolean) {
        mSecure = secure
        if (mBluetoothManager.adapter.isEnabled && mChatService == null) {
            createChatService()
        }
        if (mBluetoothManager.adapter.isEnabled && mAdvertiser != null) {
            startAdvertise()
        }
    }

    private fun sendMessage(buf: ByteArray) {
        mChatService.write(buf)
    }

    private fun sendData8bytes() {
        if (DEBUG) Log.d(TAG, "sendData8bytes")
        val buf = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        mNextWriteExpectedLen = 8
        mNextWriteCompletionIntent = BLE_DATA_8BYTES_SENT
        sendMessage(buf)
    }

    private fun sendDataLargeBuf() {
        val len = TEST_DATA_EXCHANGE_BUFSIZE
        if (DEBUG) Log.d(TAG, "sendDataLargeBuf of size=$len")
        val buf = ByteArray(len)
        for (i in 0 until len) {
            buf[i] = (i + 1).toByte()
        }
        mNextWriteExpectedLen = len

        sendMessage(buf)
    }

    private fun onTestFinish(action: String) {
        mCurrentAction = action
        if (mCurrentAction != null) {
            when (mCurrentAction) {
                BLE_ACTION_COC_SERVER_INSECURE -> startServerTest(false)
                BLE_ACTION_COC_SERVER_SECURE -> startServerTest(true)
                BLE_COC_SERVER_ACTION_SEND_DATA_8BYTES -> sendData8bytes()
                BLE_COC_SERVER_ACTION_EXCHANGE_DATA -> {
                    sendDataLargeBuf()
                    readDataLargeBuf()
                }
                BLE_COC_SERVER_ACTION_DISCONNECT -> if (mChatService != null) {
                    mChatService!!.stop()
                }
                else -> Log.e(TAG, "Error: Unhandled or invalid action=$mCurrentAction")
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mChatService != null) {
            mChatService.stop()
        }
        cancelNotificationTaskOfSecureTestStartFailure()
        stopAdvertise()
        mTaskQueue.quit()
        if (mGattServer == null) {
            return
        }
        if (mDevice != null) {
            mGattServer.cancelConnection(mDevice)
        }
        mGattServer.clearServices()
        mGattServer.close()
    }

    private fun notifyOpenFail() {
        if (DEBUG) {
            Log.d(TAG, "notifyOpenFail")
        }
        val intent = Intent(BLE_OPEN_FAIL)
        sendBroadcast(intent)
    }

    private fun notifyAddServiceFail() {
        if (DEBUG) {
            Log.d(TAG, "notifyAddServiceFail")
        }
        val intent = Intent(BLE_ADD_SERVICE_FAIL)
        sendBroadcast(intent)
    }

    private fun notifyAdvertiseUnsupported() {
        if (DEBUG) {
            Log.d(TAG, "notifyAdvertiseUnsupported")
        }
        val intent = Intent(BLE_ADVERTISE_UNSUPPORTED)
        sendBroadcast(intent)
    }

    private fun notifyConnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyConnected")
        }
        sendBroadcast(Intent(BLE_LE_CONNECTED))
    }

    private fun notifyDisconnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyDisconnected")
        }
        sendBroadcast(Intent(BLE_SERVER_DISCONNECTED))
    }

    private fun createService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(CHARACTERISTIC_UUID, 0x0A, 0x11)
        with(characteristic){
            value = WRITE_VALUE.toByteArray()
            val descriptor = BluetoothGattDescriptor(DESCRIPTOR_UUID, 0x11)
            descriptor.value = WRITE_VALUE.toByteArray()
            addDescriptor(descriptor)
            var descriptor_permission = BluetoothGattDescriptor(DESCRIPTOR_NO_READ_UUID, 0x10)
            addDescriptor(descriptor_permission)
            descriptor_permission = BluetoothGattDescriptor(DESCRIPTOR_NO_WRITE_UUID, 0x01)
            addDescriptor(descriptor_permission)
            service.addCharacteristic(this)
        }
        // Registered the characteristic of PSM Value
        mLePsmCharacteristic = BluetoothGattCharacteristic(
            BleCocClientService.Companion.LE_PSM_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(mLePsmCharacteristic)
        return service
    }

    private fun showMessage(msg: String) {
        mHandler.post { Toast.makeText(this@BleCocServerService, msg, Toast.LENGTH_SHORT).show() }
    }

    @Synchronized
    private fun cancelNotificationTaskOfSecureTestStartFailure() {
        if (mNotificationTaskOfSecureTestStartFailure != null) {
            mHandler.removeCallbacks(mNotificationTaskOfSecureTestStartFailure!!)
            mNotificationTaskOfSecureTestStartFailure = null
        }
    }

    private val mCallbacks: BluetoothGattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (DEBUG) {
                Log.d(TAG, "onConnectionStateChange: newState=$newState")
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mDevice = device
                    var bonded = false
                    val pairedDevices = mBluetoothManager.adapter.bondedDevices
                    if (pairedDevices.size > 0) {
                        for (target in pairedDevices) {
                            if (target.address == device.address) {
                                bonded = true
                                break
                            }
                        }
                    }
                    if (mSecure && (device.bondState == BluetoothDevice.BOND_NONE ||
                                !bonded)
                    ) {
                        // not pairing and execute Secure Test
                        Log.e(
                            TAG, "BluetoothGattServerCallback.onConnectionStateChange: "
                                    + "Not paired but execute secure test"
                        )
                        cancelNotificationTaskOfSecureTestStartFailure()
                    } else if (!mSecure && (device.bondState != BluetoothDevice.BOND_NONE
                                || bonded)
                    ) {
                        // already pairing and execute Insecure Test
                        Log.e(
                            TAG, "BluetoothGattServerCallback.onConnectionStateChange: "
                                    + "Paired but execute insecure test"
                        )
                    } else {
                        cancelNotificationTaskOfSecureTestStartFailure()
                    }
                    notifyConnected()
                } else if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    notifyDisconnected()
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return")
                }
                return
            }
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicReadRequest()")
            }
            var finished = false
            var value: ByteArray? = null
            if (mMtuSize > 0) {
                val buf = characteristic.value
                if (buf != null) {
                    val len = Math.min(buf.size - offset, mMtuSize)
                    if (len > 0) {
                        value = Arrays.copyOfRange(buf, offset, offset + len)
                    }
                    finished = offset + len >= buf.size
                    if (finished) {
                        Log.d(TAG, "sent whole data: " + String(characteristic.value))
                    }
                }
            } else {
                value = characteristic.value
                finished = true
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            val uid = characteristic.uuid
            if (uid == BleCocClientService.Companion.LE_PSM_CHARACTERISTIC_UUID) {
                Log.d(TAG, "onCharacteristicReadRequest: reading PSM")
            }
        }
    }

    private fun leCheckConnectionType() {
        if (mChatService == null) {
            Log.e(TAG, "leCheckConnectionType: no LE Coc connection")
            return
        }
        val type = mChatService.socketConnectionType
        if (type != BluetoothSocket.TYPE_L2CAP) {
            Log.e(TAG, "leCheckConnectionType: invalid connection type=$type")
            return
        }
        showMessage("LE CoC Connection Type Checked")
        sendBroadcast(Intent(BLE_CONNECTION_TYPE_CHECKED))
    }

    private fun readData8bytes() {
        mNextReadExpectedLen = 8
        mTotalReadLen = 0
        mNextReadCompletionIntent = BLE_DATA_8BYTES_READ
        mNextReadByte = 1
    }

    private fun readDataLargeBuf() {
        mNextReadExpectedLen = TEST_DATA_EXCHANGE_BUFSIZE
        mTotalReadLen = 0
        mNextReadCompletionIntent = BLE_DATA_LARGEBUF_READ
        mNextReadByte = 1
    }

    private fun processChatStateChange(newState: Int) {
        val intent: Intent
        if (DEBUG) {
            Log.d(TAG, "processChatStateChange: newState=$newState")
        }
        when (newState) {
            BluetoothChatService.Companion.STATE_LISTEN -> {
                intent = Intent(BLE_COC_LISTENER_CREATED)
                sendBroadcast(intent)
            }
            BluetoothChatService.Companion.STATE_CONNECTED -> {
                intent = Intent(BLE_COC_CONNECTED)
                sendBroadcast(intent)

                // Check the connection type
                leCheckConnectionType()

                // Prepare the next data read
                readData8bytes()
            }
        }
    }

    private fun checkReadBufContent(buf: ByteArray, len: Int): Boolean {
        // Check that the content is correct
        for (i in 0 until len) {
            if (buf[i] != mNextReadByte) {
                Log.e(
                    TAG, "handleMessageRead: Error: wrong byte content. buf["
                            + i + "]=" + buf[i] + " not equal to " + mNextReadByte
                )
                return false
            }
            mNextReadByte++
        }
        return true
    }

    private fun handleMessageRead(msg: Message) {
        val buf = msg.obj as ByteArray
        val len = msg.arg1
        if (len <= 0) {
            return
        }
        mTotalReadLen += len
        if (DEBUG) {
            Log.d(
                TAG, "handleMessageRead: receive buffer of length=" + len + ", mTotalReadLen="
                        + mTotalReadLen + ", mNextReadExpectedLen=" + mNextReadExpectedLen
            )
        }
        if (mNextReadExpectedLen == mTotalReadLen) {
            if (!checkReadBufContent(buf, len)) {
                mNextReadExpectedLen = -1
                return
            }
            showMessage("Read $len bytes")
            if (DEBUG) {
                Log.d(TAG, "handleMessageRead: broadcast intent $mNextReadCompletionIntent")
            }
            val intent = Intent(mNextReadCompletionIntent)
            sendBroadcast(intent)
            mNextReadExpectedLen = -1

            mTotalReadLen = 0
        } else if (mNextReadExpectedLen > mTotalReadLen) {
            if (!checkReadBufContent(buf, len)) {
                mNextReadExpectedLen = -1
                return
            }
        } else if (mNextReadExpectedLen < mTotalReadLen) {
            Log.e(
                TAG, "handleMessageRead: Unexpected receive buffer of length=" + len
                        + ", expected len=" + mNextReadExpectedLen
            )
        }
    }

    private fun handleMessageWrite(msg: Message) {
        val buffer = msg.obj as ByteArray
        val len = buffer.size
        showMessage(
            "LE CoC Server wrote " + len + " bytes" + ", mNextWriteExpectedLen="
                    + mNextWriteExpectedLen
        )
        if (len == mNextWriteExpectedLen) {
            if (mNextWriteCompletionIntent != null) {
                val intent = Intent(mNextWriteCompletionIntent)
                sendBroadcast(intent)
            }
        } else {
            Log.d(TAG, "handleMessageWrite: unrecognized length=$len")
        }
        mNextWriteExpectedLen = -1
    }

    private inner class ChatHandler : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (DEBUG) {
                Log.d(TAG, "ChatHandler.handleMessage: msg=$msg")
            }
            when (msg.what) {
                BluetoothChatService.Companion.MESSAGE_STATE_CHANGE -> processChatStateChange(msg.arg1)
                BluetoothChatService.Companion.MESSAGE_READ -> handleMessageRead(msg)
                BluetoothChatService.Companion.MESSAGE_WRITE -> handleMessageWrite(msg)
            }
        }
    }

    /* Start the Chat Service to create the Bluetooth Server Socket for LE CoC */
    private fun createChatService() {
        mChatService = BluetoothChatService(this, ChatHandler(), true)
        mChatService.start(mSecure)
        mPsm = mChatService.psm
        if (DEBUG) {
            Log.d(TAG, "createChatService: assigned PSM=$mPsm")
        }
        if (mPsm > 0x00ff) {
            Log.e(TAG, "createChatService: Invalid PSM=$mPsm")
        }
        // Notify that the PSM is read
        val intent = Intent(BLE_PSM_READ)
        sendBroadcast(intent)

        // Set the PSM value in the PSM characteristics in the GATT Server.
        mLePsmCharacteristic.setValue(mPsm, BluetoothGattCharacteristic.FORMAT_UINT8, 0)
    }

    private fun startAdvertise() {
        if (DEBUG) {
            Log.d(TAG, "startAdvertise")
        }
        val data = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(ADV_COC_SERVICE_UUID), byteArrayOf(1, 2, 3))
            .addServiceUuid(ParcelUuid(ADV_COC_SERVICE_UUID))
            .build()
        val setting = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        mAdvertiser.startAdvertising(setting, data, mAdvertiseCallback)
    }

    private fun stopAdvertise() {
        if (DEBUG) {
            Log.d(TAG, "stopAdvertise")
        }
        if (mAdvertiser != null) {
            mAdvertiser.stopAdvertising(mAdvertiseCallback)
        }
    }

    private val mAdvertiseCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Implementation for API Test.
            super.onStartFailure(errorCode)
            if (DEBUG) {
                Log.d(TAG, "onStartFailure")
            }
            if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                notifyAdvertiseUnsupported()
            } else {
                notifyOpenFail()
            }
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            // Implementation for API Test.
            super.onStartSuccess(settingsInEffect)
            if (DEBUG) {
                Log.d(TAG, "onStartSuccess")
            }
        }
    }

    companion object {
        const val DEBUG = true
        const val TAG = "BleCocServerService"
        const val COMMAND_ADD_SERVICE = 0
        const val COMMAND_WRITE_CHARACTERISTIC = 1
        const val COMMAND_WRITE_DESCRIPTOR = 2
        const val TEST_DATA_EXCHANGE_BUFSIZE = 8 * 1024
        const val BLE_BLUETOOTH_MISMATCH_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_SECURE"
        const val BLE_BLUETOOTH_MISMATCH_INSECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_INSECURE"
        const val BLE_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_DISABLED"
        const val BLE_ACTION_COC_SERVER_INSECURE =
            "com.android.cts.verifier.bluetooth.BLE_ACTION_COC_SERVER_INSECURE"
        const val BLE_ACTION_COC_SERVER_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_ACTION_COC_SERVER_SECURE"
        const val BLE_ACTION_SERVER_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_ACTION_SERVER_SECURE"
        const val BLE_ACTION_SERVER_NON_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_ACTION_SERVER_NON_SECURE"
        const val BLE_LE_CONNECTED = "com.android.cts.verifier.bluetooth.BLE_LE_CONNECTED"
        const val BLE_COC_LISTENER_CREATED =
            "com.android.cts.verifier.bluetooth.BLE_COC_LISTENER_CREATED"
        const val BLE_PSM_READ = "com.android.cts.verifier.bluetooth.BLE_PSM_READ"
        const val BLE_COC_CONNECTED = "com.android.cts.verifier.bluetooth.BLE_COC_CONNECTED"
        const val BLE_CONNECTION_TYPE_CHECKED =
            "com.android.cts.verifier.bluetooth.BLE_CONNECTION_TYPE_CHECKED"
        const val BLE_DATA_8BYTES_READ = "com.android.cts.verifier.bluetooth.BLE_DATA_8BYTES_READ"
        const val BLE_DATA_LARGEBUF_READ =
            "com.android.cts.verifier.bluetooth.BLE_DATA_LARGEBUF_READ"
        const val BLE_DATA_8BYTES_SENT = "com.android.cts.verifier.bluetooth.BLE_DATA_8BYTES_SENT"
        const val BLE_LE_DISCONNECTED = "com.android.cts.verifier.bluetooth.BLE_LE_DISCONNECTED"
        const val BLE_COC_SERVER_ACTION_SEND_DATA_8BYTES =
            "com.android.cts.verifier.bluetooth.BLE_COC_SERVER_ACTION_SEND_DATA_8BYTES"
        const val BLE_COC_SERVER_ACTION_EXCHANGE_DATA =
            "com.android.cts.verifier.bluetooth.BLE_COC_SERVER_ACTION_EXCHANGE_DATA"
        const val BLE_COC_SERVER_ACTION_DISCONNECT =
            "com.android.cts.verifier.bluetooth.BLE_COC_SERVER_ACTION_DISCONNECT"
        const val BLE_SERVER_DISCONNECTED =
            "com.android.cts.verifier.bluetooth.BLE_SERVER_DISCONNECTED"
        const val BLE_OPEN_FAIL = "com.android.cts.verifier.bluetooth.BLE_OPEN_FAIL"
        const val BLE_ADVERTISE_UNSUPPORTED =
            "com.android.cts.verifier.bluetooth.BLE_ADVERTISE_UNSUPPORTED"
        const val BLE_ADD_SERVICE_FAIL = "com.android.cts.verifier.bluetooth.BLE_ADD_SERVICE_FAIL"
        private val SERVICE_UUID = UUID.fromString("00009999-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_UUID = UUID.fromString("00009998-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_RESULT_UUID =
            UUID.fromString("00009974-0000-1000-8000-00805f9b34fb")
        private val UPDATE_CHARACTERISTIC_UUID =
            UUID.fromString("00009997-0000-1000-8000-00805f9b34fb")
        private val DESCRIPTOR_UUID = UUID.fromString("00009996-0000-1000-8000-00805f9b34fb")
        val ADV_COC_SERVICE_UUID = UUID.fromString("00003334-0000-1000-8000-00805f9b34fb")
        private val SERVICE_UUID_ADDITIONAL =
            UUID.fromString("00009995-0000-1000-8000-00805f9b34fb")
        private val SERVICE_UUID_INCLUDED = UUID.fromString("00009994-0000-1000-8000-00805f9b34fb")

        // Variable for registration permission of Descriptor
        private val DESCRIPTOR_NO_READ_UUID =
            UUID.fromString("00009973-0000-1000-8000-00805f9b34fb")
        private val DESCRIPTOR_NO_WRITE_UUID =
            UUID.fromString("00009972-0000-1000-8000-00805f9b34fb")
        private val DESCRIPTOR_NEED_ENCRYPTED_READ_UUID =
            UUID.fromString("00009969-0000-1000-8000-00805f9b34fb")
        private val DESCRIPTOR_NEED_ENCRYPTED_WRITE_UUID =
            UUID.fromString("00009968-0000-1000-8000-00805f9b34fb")
        private const val CONN_INTERVAL = 150 // connection interval 150ms
        private const val EXECUTION_DELAY = 1500

        // Delay of notification when secure test failed to start.
        private const val NOTIFICATION_DELAY_OF_SECURE_TEST_FAILURE = (5 * 1000).toLong()
        const val WRITE_VALUE = "SERVER_TEST"
        private const val NOTIFY_VALUE = "NOTIFY_TEST"
        private const val INDICATE_VALUE = "INDICATE_TEST"
        const val READ_NO_PERMISSION = "READ_NO_CHAR"
        const val WRITE_NO_PERMISSION = "WRITE_NO_CHAR"
        const val DESCRIPTOR_READ_NO_PERMISSION = "READ_NO_DESC"
        const val DESCRIPTOR_WRITE_NO_PERMISSION = "WRITE_NO_DESC"

        /*protected*/
        fun dumpService(service: BluetoothGattService, level: Int) {
            var indent = ""
            for (i in 0 until level) {
                indent += "  "
            }
            Log.d(TAG, "$indent[service]")
            Log.d(TAG, indent + "UUID: " + service.uuid)
            Log.d(TAG, "$indent  [characteristics]")
            for (ch in service.characteristics) {
                Log.d(TAG, indent + "    UUID: " + ch.uuid)
                Log.d(
                    TAG, indent + "      properties: "
                            + String.format("0x%02X", ch.properties)
                )
                Log.d(
                    TAG, indent + "      permissions: "
                            + String.format("0x%02X", ch.permissions)
                )
                Log.d(TAG, "$indent      [descriptors]")
                for (d in ch.descriptors) {
                    Log.d(TAG, indent + "        UUID: " + d.uuid)
                    Log.d(
                        TAG, indent + "          permissions: "
                                + String.format("0x%02X", d.permissions)
                    )
                }
            }
            if (service.includedServices != null) {
                Log.d(TAG, "$indent  [included services]")
                for (s in service.includedServices) {
                    dumpService(s, level + 1)
                }
            }
        }
    }
}