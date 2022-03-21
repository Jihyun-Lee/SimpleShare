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
import com.theone.simpleshare.bluetooth.BleCocServerService
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import com.theone.simpleshare.bluetooth.BluetoothChatService.AcceptThread
import com.theone.simpleshare.bluetooth.BluetoothChatService.ConnectThread
import com.theone.simpleshare.bluetooth.BluetoothChatService.ConnectedThread
import com.theone.simpleshare.bluetooth.BluetoothPairingService
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothProfile.ServiceListener
import android.bluetooth.BluetoothA2dp
import android.bluetooth.le.*
import android.content.Context
import android.os.*
import android.util.Log
import java.util.*

//import com.android.cts.verifier.R;
class BleCocClientService : Service() {
    private var mBleState = BluetoothProfile.STATE_DISCONNECTED

    // current test category
    private lateinit var  mCurrentAction: String
    private lateinit var mBluetoothManager: BluetoothManager
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var mDevice: BluetoothDevice
    private lateinit var mBluetoothGatt: BluetoothGatt
    private lateinit var mScanner: BluetoothLeScanner
    private lateinit var mHandler: Handler
    private var mSecure = false
    private var mValidityService = false
    private var mPsm = 0
    private lateinit var mChatService: BluetoothChatService
    private var mNextReadExpectedLen = -1
    private lateinit var mNextReadCompletionIntent: String
    private var mTotalReadLen = 0
    private var mNextReadByte: Byte = 0
    private var mNextWriteExpectedLen = -1
    private lateinit var mNextWriteCompletionIntent: String

    // Handler for communicating task with peer.
    private lateinit var mTaskQueue: TestTaskQueue
    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            mBondStatusReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
        mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter
        mScanner = mBluetoothAdapter.bluetoothLeScanner
        mHandler = Handler(Looper.getMainLooper())
        mTaskQueue = TestTaskQueue(javaClass.name + "_taskHandlerThread")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!mBluetoothAdapter.isEnabled) {
            notifyBluetoothDisabled()
        } else {
            mTaskQueue.addTask({ onTestFinish(intent.action) }, EXECUTION_DELAY.toLong())
        }
        return START_NOT_STICKY
    }

    private fun onTestFinish(action: String?) {
        if (action != null) {
            mCurrentAction = action
        }
        Log.d(TAG, "onTestFinish action : $action")
        when (mCurrentAction) {
            BLE_COC_CLIENT_ACTION_LE_INSECURE_CONNECT -> {
                mSecure = false
                startScan()
            }
            BLE_COC_CLIENT_ACTION_LE_SECURE_CONNECT -> {
                mSecure = true
                startScan()
            }
            BLE_COC_CLIENT_ACTION_GET_PSM -> startLeDiscovery()
            BLE_COC_CLIENT_ACTION_COC_CLIENT_CONNECT -> leCocClientConnect()
            BLE_COC_CLIENT_ACTION_CHECK_CONNECTION_TYPE -> leCheckConnectionType()
            BLE_COC_CLIENT_ACTION_SEND_DATA_8BYTES -> sendData8bytes()
            BLE_COC_CLIENT_ACTION_READ_DATA_8BYTES -> readData8bytes()
            BLE_COC_CLIENT_ACTION_EXCHANGE_DATA -> {
                sendDataLargeBuf()
                readDataLargeBuf()
            }
            BLE_CLIENT_ACTION_CLIENT_DISCONNECT -> {
                mBluetoothGatt.disconnect()
                mChatService.stop()
            }
            else -> Log.e(TAG, "Error: Unhandled or invalid action=$mCurrentAction")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()

        mBluetoothGatt.disconnect()
        mBluetoothGatt.close()
        stopScan()
        unregisterReceiver(mBondStatusReceiver)

        mChatService.stop()
        mTaskQueue.quit()
    }

    private fun readCharacteristic(uuid: UUID) {
        val characteristic = getCharacteristic(uuid)
        mBluetoothGatt.readCharacteristic(characteristic)
    }

    private fun notifyError(message: String) {
        showMessage(message)
        Log.e(TAG, message)
        sendBroadcast(Intent(BLE_CLIENT_ERROR))
    }

    private fun notifyMismatchSecure() =
        sendBroadcast(Intent(BLE_BLUETOOTH_MISMATCH_SECURE))

    private fun notifyMismatchInsecure() =
        sendBroadcast(Intent(BLE_BLUETOOTH_MISMATCH_INSECURE))


    private fun notifyBluetoothDisabled() =
        sendBroadcast(Intent(BLE_BLUETOOTH_DISABLED))

    private fun notifyConnected() {
        showMessage("Bluetooth LE GATT connected")
        sendBroadcast(Intent(BLE_LE_CONNECTED))
    }

    private fun startLeDiscovery() {
        // Start Service Discovery
        if (mBleState == BluetoothProfile.STATE_CONNECTED) {
            mBluetoothGatt.discoverServices()
        } else {
            showMessage("Bluetooth LE GATT not connected.")
        }
    }

    private fun notifyDisconnected() {
        showMessage("Bluetooth LE disconnected")
        sendBroadcast(Intent(BLE_BLUETOOTH_DISCONNECTED))
    }

    private fun notifyServicesDiscovered() {
        showMessage("Service discovered")
        // Find the LE_COC_PSM characteristics
        if (DEBUG) {
            Log.d(TAG, "notifyServicesDiscovered: Next step is to read the PSM char.")
        }
        readCharacteristic(LE_PSM_CHARACTERISTIC_UUID)
    }

    private val service: BluetoothGattService?
        get() {
            var service = mBluetoothGatt.getService(SERVICE_UUID)
            if (service == null) {
                showMessage("GATT Service not found")
            }
            return service
        }

    private fun getCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        var characteristic: BluetoothGattCharacteristic? = null
        val service = service
        if (service != null) {
            characteristic = service.getCharacteristic(uuid)
            if (characteristic == null) {
                showMessage("Characteristic not found")
            }
        }
        return characteristic
    }

    private fun showMessage(msg: String) {
        mHandler.post {
            Log.d(TAG, msg)
            Toast.makeText(this@BleCocClientService, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private val mGattCallbacks: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (DEBUG) {
                Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mBleState = newState
                    val bondState = gatt.device.bondState
                    var bonded = false
                    val target = gatt.device
                    val pairedDevices = mBluetoothAdapter.bondedDevices
                    if (!pairedDevices.isEmpty()) {
                        for (device in pairedDevices) {
                            if (device.address == target.address) {
                                bonded = true
                                break
                            }
                        }
                    }
                    if (mSecure && (bondState == BluetoothDevice.BOND_NONE || !bonded)) {
                        // not pairing and execute Secure Test
                        Log.e(
                            TAG, "BluetoothGattCallback.onConnectionStateChange: "
                                    + "Not paired but execute secure test"
                        )
                        mBluetoothGatt.disconnect()
                        notifyMismatchSecure()
                    } else if (!mSecure && (bondState != BluetoothDevice.BOND_NONE || bonded)) {
                        // already pairing and execute Insecure Test
                        Log.e(
                            TAG, "BluetoothGattCallback.onConnectionStateChange: "
                                    + "Paired but execute insecure test"
                        )
                        mBluetoothGatt.disconnect()
                        notifyMismatchInsecure()
                    } else {
                        notifyConnected()
                    }
                } else if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    mBleState = newState
                    mSecure = false
                    mBluetoothGatt.close()
                    notifyDisconnected()
                }
            } else {
                showMessage("Failed to connect: $status , newState = $newState")
                mBluetoothGatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (DEBUG) {
                Log.d(TAG, "onServicesDiscovered: status=$status")
            }
            if (status == BluetoothGatt.GATT_SUCCESS &&
                mBluetoothGatt.getService(SERVICE_UUID) != null
            ) {
                notifyServicesDiscovered()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            val uid = characteristic.uuid
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicRead: status=$status, uuid=$uid")
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.getStringValue(0)
                if (characteristic.uuid == LE_PSM_CHARACTERISTIC_UUID) {
                    mPsm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    if (DEBUG) {
                        Log.d(TAG, "onCharacteristicRead: reading PSM=$mPsm")
                    }
                    sendBroadcast(Intent(BLE_GOT_PSM))
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "onCharacteristicRead: Note: unknown uuid=$uid")
                    }
                }
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                notifyError("Not Permission Read: $status : $uid")
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                notifyError("Not Authentication Read: $status : $uid")
            } else {
                notifyError("Failed to read characteristic: $status : $uid")
            }
        }
    }
    private val mScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "onScanResult")
            // verify the validity of the advertisement packet.
            mValidityService = false
            val uuids = result.scanRecord!!.serviceUuids
            for (uuid in uuids) {
                if (uuid.uuid == BleCocServerService.Companion.ADV_COC_SERVICE_UUID) {
                    if (DEBUG) {
                        Log.d(TAG, "onScanResult: Found ADV with LE CoC Service UUID.")
                    }
                    mValidityService = true
                    break
                }
            }
            if (mValidityService) {
                stopScan()
                val device = result.device
                if (DEBUG) {
                    Log.d(
                        TAG, "onScanResult: Found ADV with CoC UUID on device="
                                + device
                    )
                }
                if (mSecure) {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        if (!device.createBond()) {
                            notifyError("Failed to call create bond")
                        }
                    } else {
                        mDevice = device
                        mBluetoothGatt = connectGatt(
                            result.device, this@BleCocClientService, false,
                            mSecure, mGattCallbacks
                        )
                    }
                } else {
                    mDevice = device
                    mBluetoothGatt = connectGatt(
                        result.device, this@BleCocClientService, false, mSecure,
                        mGattCallbacks
                    )
                }
            } else {
                notifyError("No valid service in Advertisement")
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

    private fun sendMessage(buf: ByteArray) {
        mChatService.write(buf)
    }

    private fun handleMessageWrite(msg: Message) {
        val buffer = msg.obj as ByteArray
        val len = buffer.size
        showMessage("LE Coc Client wrote $len bytes")
        if (len == mNextWriteExpectedLen) {
            sendBroadcast(Intent(mNextWriteCompletionIntent))
        } else {
            Log.d(TAG, "handleMessageWrite: unrecognized length=$len")
        }
    }

    private inner class ChatHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (DEBUG) {
                Log.d(TAG, "ChatHandler.handleMessage: msg=$msg")
            }
            val state = msg.arg1
            when (msg.what) {
                BluetoothChatService.Companion.MESSAGE_STATE_CHANGE -> if (state == BluetoothChatService.Companion.STATE_CONNECTED) {
                    // LE CoC is established
                    notifyLeCocClientConnected()
                }
                BluetoothChatService.Companion.MESSAGE_READ -> handleMessageRead(msg)
                BluetoothChatService.Companion.MESSAGE_WRITE -> handleMessageWrite(msg)
            }
        }
    }

    private fun notifyLeCocClientConnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyLeCocClientConnected: device=$mDevice, mSecure=$mSecure")
        }
        showMessage("Bluetooth LE Coc connected")
        val intent = Intent(BLE_COC_CONNECTED)
        sendBroadcast(intent)
    }

    private fun leCocClientConnect() {
        if (DEBUG) {
            Log.d(TAG, "leCocClientConnect: device=$mDevice, mSecure=$mSecure")
        }

        // Construct BluetoothChatService with useBle=true parameter
        mChatService = BluetoothChatService(this, ChatHandler(), true)
        mChatService.connect(mDevice, mSecure, mPsm)
    }

    private fun leCheckConnectionType() {
        val type = mChatService.socketConnectionType
        if (type != BluetoothSocket.TYPE_L2CAP) {
            Log.e(TAG, "leCheckConnectionType: invalid connection type=$type")
            return
        }
        showMessage("LE Coc Connection Type Checked")
        val intent = Intent(BLE_CONNECTION_TYPE_CHECKED)
        sendBroadcast(intent)
    }

    private fun sendData8bytes() {
        if (DEBUG) Log.d(TAG, "sendData8bytes")
        val buf = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        mNextWriteExpectedLen = 8
        mNextWriteCompletionIntent = BLE_DATA_8BYTES_SENT
        sendMessage(buf)
    }

    private fun sendDataLargeBuf() {
        val len: Int = BleCocServerService.Companion.TEST_DATA_EXCHANGE_BUFSIZE
        if (DEBUG) Log.d(TAG, "sendDataLargeBuf of size=$len")
        val buf = ByteArray(len)
        for (i in 0 until len) {
            buf[i] = (i + 1).toByte()
        }
        mNextWriteExpectedLen = len

        sendMessage(buf)
    }

    private fun readData8bytes() {
        mNextReadExpectedLen = 8
        mNextReadCompletionIntent = BLE_DATA_8BYTES_READ
        mNextReadByte = 1
    }

    private fun readDataLargeBuf() {
        mNextReadExpectedLen = BleCocServerService.Companion.TEST_DATA_EXCHANGE_BUFSIZE
        mNextReadCompletionIntent = BLE_DATA_LARGEBUF_READ
        mNextReadByte = 1
    }

    private fun startScan() {
        if (DEBUG) Log.d(TAG, "startScan")
        val filter = Arrays.asList(
            ScanFilter.Builder().setServiceUuid(
                ParcelUuid(BleCocServerService.Companion.ADV_COC_SERVICE_UUID)
            ).build()
        )
        val setting = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        mScanner.startScan(filter, setting, mScanCallback)
    }

    private fun stopScan() {
        if (DEBUG) Log.d(TAG, "stopScan")
        mScanner.stopScan(mScanCallback)
    }

    private val mBondStatusReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val state = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )
                when (state) {
                    BluetoothDevice.BOND_BONDED -> {
                        if (DEBUG) {
                            Log.d(
                                TAG, "onReceive:BOND_BONDED: calling connectGatt. device="
                                        + device + ", mSecure=" + mSecure
                            )
                        }
                        if (device != null) {
                            mDevice = device
                        }
                        mBluetoothGatt = connectGatt(
                            device, this@BleCocClientService, false, mSecure,
                            mGattCallbacks
                        )
                    }
                    BluetoothDevice.BOND_NONE -> notifyError("Failed to create bond")
                    BluetoothDevice.BOND_BONDING -> {}
                    else -> {}
                }
            }
        }
    }

    companion object {
        const val DEBUG = true
        const val TAG = "BleCocClientService"
        private const val TRANSPORT_MODE_FOR_SECURE_CONNECTION = BluetoothDevice.TRANSPORT_LE
        const val BLE_LE_CONNECTED = "com.android.cts.verifier.bluetooth.BLE_LE_CONNECTED"
        const val BLE_GOT_PSM = "com.android.cts.verifier.bluetooth.BLE_GOT_PSM"
        const val BLE_COC_CONNECTED = "com.android.cts.verifier.bluetooth.BLE_COC_CONNECTED"
        const val BLE_CONNECTION_TYPE_CHECKED =
            "com.android.cts.verifier.bluetooth.BLE_CONNECTION_TYPE_CHECKED"
        const val BLE_DATA_8BYTES_SENT = "com.android.cts.verifier.bluetooth.BLE_DATA_8BYTES_SENT"
        const val BLE_DATA_8BYTES_READ = "com.android.cts.verifier.bluetooth.BLE_DATA_8BYTES_READ"
        const val BLE_DATA_LARGEBUF_READ =
            "com.android.cts.verifier.bluetooth.BLE_DATA_LARGEBUF_READ"
        const val BLE_LE_DISCONNECTED = "com.android.cts.verifier.bluetooth.BLE_LE_DISCONNECTED"
        const val BLE_BLUETOOTH_MISMATCH_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_SECURE"
        const val BLE_BLUETOOTH_MISMATCH_INSECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_INSECURE"
        const val BLE_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_DISABLED"
        const val BLE_GATT_CONNECTED = "com.android.cts.verifier.bluetooth.BLE_GATT_CONNECTED"
        const val BLE_BLUETOOTH_DISCONNECTED =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_DISCONNECTED"
        const val BLE_CLIENT_ERROR = "com.android.cts.verifier.bluetooth.BLE_CLIENT_ERROR"
        const val EXTRA_COMMAND = "com.android.cts.verifier.bluetooth.EXTRA_COMMAND"
        const val EXTRA_WRITE_VALUE = "com.android.cts.verifier.bluetooth.EXTRA_WRITE_VALUE"
        const val EXTRA_BOOL = "com.android.cts.verifier.bluetooth.EXTRA_BOOL"

        // Literal for Client Action
        const val BLE_COC_CLIENT_ACTION_LE_INSECURE_CONNECT =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_LE_INSECURE_CONNECT"
        const val BLE_COC_CLIENT_ACTION_LE_SECURE_CONNECT =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_LE_SECURE_CONNECT"
        const val BLE_COC_CLIENT_ACTION_GET_PSM =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_GET_PSM"
        const val BLE_COC_CLIENT_ACTION_COC_CLIENT_CONNECT =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_COC_CLIENT_CONNECT"
        const val BLE_COC_CLIENT_ACTION_CHECK_CONNECTION_TYPE =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_CHECK_CONNECTION_TYPE"
        const val BLE_COC_CLIENT_ACTION_SEND_DATA_8BYTES =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_SEND_DATA_8BYTES"
        const val BLE_COC_CLIENT_ACTION_READ_DATA_8BYTES =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_READ_DATA_8BYTES"
        const val BLE_COC_CLIENT_ACTION_EXCHANGE_DATA =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_EXCHANGE_DATA"
        const val BLE_COC_CLIENT_ACTION_CLIENT_CONNECT =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_CLIENT_CONNECT"
        const val BLE_COC_CLIENT_ACTION_CLIENT_CONNECT_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_COC_CLIENT_ACTION_CLIENT_CONNECT_SECURE"
        const val BLE_CLIENT_ACTION_CLIENT_DISCONNECT =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_CLIENT_DISCONNECT"
        private val SERVICE_UUID = UUID.fromString("00009999-0000-1000-8000-00805f9b34fb")

        /**
         * UUID of the GATT Read Characteristics for LE_PSM value.
         */
        val LE_PSM_CHARACTERISTIC_UUID = UUID.fromString("2d410339-82b6-42aa-b34e-e2e01df8cc1a")
        const val WRITE_VALUE = "CLIENT_TEST"
        private const val NOTIFY_VALUE = "NOTIFY_TEST"
        private const val EXECUTION_DELAY = 1500
        fun connectGatt(
            device: BluetoothDevice?, context: Context?,
            autoConnect: Boolean, isSecure: Boolean,
            callback: BluetoothGattCallback?
        ): BluetoothGatt {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (isSecure) {
                    if (TRANSPORT_MODE_FOR_SECURE_CONNECTION == BluetoothDevice.TRANSPORT_AUTO) {
                        Toast.makeText(context, "connectGatt(transport=AUTO)", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT)
                            .show()
                    }
                    device!!.connectGatt(
                        context, autoConnect, callback,
                        TRANSPORT_MODE_FOR_SECURE_CONNECTION
                    )
                } else {
                    Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show()
                    device!!.connectGatt(
                        context, autoConnect, callback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                }
            } else {
                Toast.makeText(context, "connectGatt", Toast.LENGTH_SHORT).show()
                device!!.connectGatt(context, autoConnect, callback)
            }
        }
    }
}