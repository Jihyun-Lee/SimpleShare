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
import android.content.Context
import android.os.*
import android.util.Log
import java.util.*

class BatteryLevelReader : Service() {
    private var mBleState = BluetoothProfile.STATE_DISCONNECTED

    // current test category
    private var mCurrentAction: String? = null
    private lateinit var mBluetoothManager: BluetoothManager
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var mDevice: BluetoothDevice
    private lateinit var mBluetoothGatt: BluetoothGatt
    private lateinit var mScanner: BluetoothLeScanner
    private lateinit var mHandler: Handler
    private var mSecure = false
    private val mValidityService = false
    private var mPsm = 0
    private lateinit var mChatService: BluetoothChatService
    private val mNextReadExpectedLen = -1
    private lateinit var mNextReadCompletionIntent: String
    private val mTotalReadLen = 0
    private val mNextReadByte: Byte = 0
    private val mNextWriteExpectedLen = -1
    private lateinit var mNextWriteCompletionIntent: String

    // Handler for communicating task with peer.
    private lateinit var mTaskQueue: TestTaskQueue
    private lateinit var mDeviceGatt: BluetoothGatt
    override fun onCreate() {
        super.onCreate()
        mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter
        mScanner = mBluetoothAdapter.getBluetoothLeScanner() as BluetoothLeScanner
        mTaskQueue = TestTaskQueue(javaClass.name + "_taskHandlerThread")
        mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(m: Message) {
                when (m.what) {
                    MSG_CONNECT_TIMEOUT -> {}
                    MSG_CONNECT -> {}
                    else -> {}
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!mBluetoothAdapter.isEnabled) {
            notifyBluetoothDisabled()
        } else {
            mTaskQueue.addTask({ onTestFinish(intent) }, EXECUTION_DELAY.toLong())
        }
        return START_NOT_STICKY
    }

    private fun onTestFinish(intent: Intent) {
        mCurrentAction = intent.action
        Log.d(TAG, "onTestFinish action : $mCurrentAction")
        if (mCurrentAction != null) {
            when (mCurrentAction) {
                BLUETOOTH_ACTION_GET_BONDED_DEVICES -> {
                    val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                    val bluetoothAdapter = bluetoothManager.adapter
                    val devSet = bluetoothAdapter.bondedDevices
                    if (devSet.size != 0) {
                        for (device in devSet) {
                            mDevice = device
                            if (mDevice.type == BluetoothDevice.DEVICE_TYPE_LE ||
                                        mDevice.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
                                // Only LE devices support GATT
                                // Todo : need autoConnect???
                                Log.d(TAG, "try connectGatt on " + mDevice.name)
                                mDeviceGatt = mDevice.connectGatt(
                                    this@BatteryLevelReader,
                                    true,
                                    GattBatteryCallbacks()
                                )
                            } else {
                                if (mDevice.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                                    Log.e(TAG, "DEVICE_TYPE_CLASSIC device..")

                                    Intent(BLUETOOTH_ACTION_NOTIFY_BONDED_DEVICE).apply {
                                        putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice)
                                        putExtra(BATTERY_LEVEL, NO_BATTTERY_INFO)
                                        sendBroadcast(this)
                                    }
                                } else {
                                    Log.e(TAG, "~~~~~~~~error")
                                    Log.e(TAG, "mDevice.getName() : " + mDevice.name)
                                    Log.e(TAG, "mDevice.getType() : " + mDevice.type)
                                }
                            }
                        }
                    }
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
        mBluetoothGatt.disconnect()
        mBluetoothGatt.close()
        mTaskQueue.quit()
    }

    private fun readCharacteristic(uuid: UUID) {
        val characteristic = getCharacteristic(uuid)
        if (characteristic != null) {
            mBluetoothGatt.readCharacteristic(characteristic)
        }
    }

    private fun notifyError(message: String) {
        showMessage(message)
        Log.e(TAG, message)

        // Intent intent = new Intent(BLE_CLIENT_ERROR);
        // sendBroadcast(intent);
    }

    private fun notifyMismatchSecure() {
        // Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_SECURE);
        // sendBroadcast(intent);
    }

    private fun notifyMismatchInsecure() {
        //Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_INSECURE);
        //sendBroadcast(intent);
    }

    private fun notifyBluetoothDisabled() {
        //Intent intent = new Intent(BLE_BLUETOOTH_DISABLED);
        //sendBroadcast(intent);
    }

    private fun notifyConnected() {
        showMessage("Bluetooth LE GATT connected")
        // Intent intent = new Intent(BLE_LE_CONNECTED);
        //sendBroadcast(intent);
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
        //Intent intent = new Intent(BLE_BLUETOOTH_DISCONNECTED);
        //sendBroadcast(intent);
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
            //show toast
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
                    //Intent intent = new Intent(BLE_GOT_PSM);
                    //sendBroadcast(intent);
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

    public inner class GattBatteryCallbacks : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (DEBUG) {
                Log.d(TAG, "Connection status:$status state:$newState")
            }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (DEBUG) {
                    Log.e(TAG, "Service discovery failure on $gatt")
                }
                return
            }
            val battService = gatt.getService(GATT_BATTERY_SERVICE_UUID)
            if (battService == null) {
                if (DEBUG) {
                    Log.d(TAG, "No battery service")
                }
                return
            }
            val battLevel = battService.getCharacteristic(GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID)
            if (battLevel == null) {
                if (DEBUG) {
                    Log.d(TAG, "No battery level")
                }
                Log.d(TAG, "send device and battery level")
                val intent = Intent(BLUETOOTH_ACTION_NOTIFY_BONDED_DEVICE)
                intent.apply {
                    putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice)
                    putExtra(BATTERY_LEVEL, NO_BATTTERY_INFO)
                    sendBroadcast(this)
                }

                return
            }
            gatt.readCharacteristic(battLevel)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (DEBUG) {
                    Log.e(TAG, "Read characteristic failure on $gatt $characteristic")
                }
                return
            }
            if (GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID == characteristic.uuid) {
                val batteryLevel =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                mHandler.post { /*if (mBatteryPref != null && !mUnpairing) {
                            mBatteryPref.setTitle(getString(R.string.accessory_battery,
                                    batteryLevel));
                            mBatteryPref.setVisible(true);
                        }*/
                    Log.d(TAG, "battery level : $batteryLevel")
                    Toast.makeText(
                        this@BatteryLevelReader,
                        "battery level : $batteryLevel",
                        Toast.LENGTH_SHORT
                    ).show()
                    val device = gatt.device
                    val intent = Intent(BLUETOOTH_ACTION_NOTIFY_BONDED_DEVICE)
                    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                    intent.putExtra(BATTERY_LEVEL, batteryLevel)
                    //intent.putExtra(CONNECTION_STATE, getConnectionState(device));
                    sendBroadcast(intent)
                }
            }
        }
    }

    fun getConnectionState(device: BluetoothDevice?): Int {
        var connectionState = if (BluetoothUtils.isA2dpDevice(device)) {
            mBluetoothManager.getConnectionState(device, BluetoothProfile.A2DP)
        } else if (BluetoothUtils.isInputDevice(device)) {
            mBluetoothManager.getConnectionState(device, BluetoothProfile.HID_DEVICE)
        } else {
            Log.d(TAG, "ignore connection state")
            -1
        }
        return connectionState
    }

    companion object {
        private const val TAG = "BatteryLevelReader"
        const val DEBUG = true
        private const val MSG_CONNECT_TIMEOUT = 1
        private const val MSG_CONNECT = 2
        private const val MSG_REMOVE_BOND = 3
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val CONNECT_DELAY = 1000
        private const val TRANSPORT_MODE_FOR_SECURE_CONNECTION = BluetoothDevice.TRANSPORT_LE
        const val BLUETOOTH_ACTION_START_SCAN =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_START_SCAN"
        const val BLUETOOTH_ACTION_START_PAIRING =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_START_PAIRING"
        const val BLUETOOTH_ACTION_REMOVE_BOND =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_REMOVE_BOND"
        const val BLUETOOTH_ACTION_GET_BONDED_DEVICES =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_GET_BONDED_DEVICES"
        const val BLUETOOTH_ACTION_NOTIFY_BONDED_DEVICE =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_NOTIFY_BONDED_DEVICE"
        private val SERVICE_UUID = UUID.fromString("00009999-0000-1000-8000-00805f9b34fb")

        /**
         * UUID of the GATT Read Characteristics for LE_PSM value.
         */
        val LE_PSM_CHARACTERISTIC_UUID = UUID.fromString("2d410339-82b6-42aa-b34e-e2e01df8cc1a")
        const val WRITE_VALUE = "CLIENT_TEST"
        private const val NOTIFY_VALUE = "NOTIFY_TEST"
        private const val EXECUTION_DELAY = 1500
        private val GATT_BATTERY_SERVICE_UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        const val BATTERY_LEVEL = "com.theone.simpleshare.bluetooth.BATTERY_LEVEL"
        const val CONNECTION_STATE = "com.theone.simpleshare.bluetooth.CONNECTION_STATE"
        @JvmField
        var NO_BATTTERY_INFO = -1
        @JvmField
        var NO_CONNECTION_INFO = -1
        fun connectGatt(
            device: BluetoothDevice, context: Context?,
            autoConnect: Boolean, callback: BluetoothGattCallback?
        ): BluetoothGatt {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show()
                device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                Toast.makeText(context, "connectGatt", Toast.LENGTH_SHORT).show()
                device.connectGatt(context, autoConnect, callback)
            }
        }
    }
}