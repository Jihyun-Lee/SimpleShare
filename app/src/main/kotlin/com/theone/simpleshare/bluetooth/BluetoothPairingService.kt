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
import java.lang.Exception
import java.util.*

class BluetoothPairingService : Service() {
    private var mBleState = BluetoothProfile.STATE_DISCONNECTED

    // current test category
    private lateinit var mCurrentAction: String
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
    private val mNextWriteExpectedLen = -1
    private lateinit var mNextWriteCompletionIntent: String

    // Handler for communicating task with peer.
    private var mTaskQueue: TestTaskQueue? = null
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBondStatusReceiver, filter)
        mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter
        mScanner = mBluetoothAdapter.bluetoothLeScanner
        mTaskQueue = TestTaskQueue(javaClass.name + "_taskHandlerThread")
        mHandler = object : Handler() {
            override fun handleMessage(m: Message) {
                when (m.what) {
                    MSG_CONNECT_TIMEOUT -> {}
                    MSG_CONNECT -> {
                        if (mA2dpProfile == null) {
                            return
                            //break
                        }
                        Log.d(TAG, "try to connect")
                        a2dpConnect(mDevice)
                        mHandler!!.postDelayed({
                            if (BluetoothProfile.STATE_CONNECTED == mA2dpProfile!!.getConnectionState(
                                    mDevice
                                )
                            ) {
                                Log.d(TAG, mDevice!!.name + " connected")
                                Toast.makeText(
                                    this@BluetoothPairingService,
                                    mDevice!!.name + " connected",
                                    Toast.LENGTH_SHORT
                                ).show()
                                //todo : display battery and state in rv
                            } else {
                                Log.d(TAG, mDevice!!.name + " not connected")
                                notifyError(mDevice!!.name + " not connected")
                            }
                        }, 2000)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun a2dpConnect(device: BluetoothDevice?) {
        Log.d(TAG, "connect")
        try {
            val connect = Class.forName("android.bluetooth.BluetoothA2dp")
                .getMethod("connect", BluetoothDevice::class.java)
            connect.invoke(mA2dpProfile, device)
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
        }
    }

    /*
    private void hidHostConnect(BluetoothDevice device) {
        Log.d(TAG,"connect");
        try{
            Method connect = Class.forName("android.bluetooth.BluetoothHidHost").getMethod("connect", BluetoothDevice.class);
            connect.invoke(mInputProxy, device);
        }catch(Exception e){
            Log.d(TAG, e.toString());
        }
    }
    */
    private fun removeBond(device: BluetoothDevice) {
        Log.d(TAG, "removeBond")
        try {
            val connect = Class.forName("android.bluetooth.BluetoothDevice").getMethod("removeBond")
            connect.invoke(device)
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!mBluetoothAdapter.isEnabled) {
            notifyBluetoothDisabled()
        } else {
            mTaskQueue!!.addTask({ onTestFinish(intent) }, EXECUTION_DELAY.toLong())
        }
        return START_NOT_STICKY
    }

    private fun onTestFinish(intent: Intent) {
        mCurrentAction = intent.action!!
        Log.d(TAG, "onTestFinish action : $mCurrentAction")
        if (mCurrentAction != null) {
            when (mCurrentAction) {
                BLUETOOTH_ACTION_START_SCAN -> {
                    if (mBluetoothAdapter!!.isDiscovering) {
                        Log.d(TAG, "stop scan")
                        stopDiscovery()
                    }
                    startDiscovery()
                }
                BLUETOOTH_ACTION_START_PAIRING -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    stopDiscovery()
                    if (device != null) {
                        val name = device.name
                        val address = device.address
                        Log.d(TAG, "name : $name address : $address")
                        if (device.bondState != BluetoothDevice.BOND_BONDED) {
                            if (!device.createBond()) {
                                notifyError("Failed to call create bond")
                            }
                        } else {
                            notifyError(device.name + " is already paired")
                        }
                    } else {
                        Log.e(TAG, "ERROR : device is null")
                        notifyError("Failed to get device from discovery")
                    }
                }
                BLUETOOTH_ACTION_REMOVE_BOND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { removeBond(it) }
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
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect()
            mBluetoothGatt.close()
        }
        stopDiscovery()
        unregisterReceiver(mBondStatusReceiver)
        Log.d(TAG, "BluetoothPairingService onDestroy")
        mTaskQueue!!.quit()
    }

    private fun readCharacteristic(uuid: UUID) {
        val characteristic = getCharacteristic(uuid)
        if (characteristic != null) {
            mBluetoothGatt!!.readCharacteristic(characteristic)
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
        if (mBluetoothGatt != null && mBleState == BluetoothProfile.STATE_CONNECTED) {
            mBluetoothGatt!!.discoverServices()
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
        private get() {
            var service: BluetoothGattService? = null
            if (mBluetoothGatt != null) {
                service = mBluetoothGatt.getService(SERVICE_UUID)
                if (service == null) {
                    showMessage("GATT Service not found")
                }
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
            Toast.makeText(this@BluetoothPairingService, msg, Toast.LENGTH_SHORT).show()
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
                    val pairedDevices = mBluetoothAdapter!!.bondedDevices
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
    private val mScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "onScanResult")
            if (mBluetoothGatt == null) {
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
                    //stopScan();
                    stopDiscovery()
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
                                result.device, this@BluetoothPairingService, false,
                                mSecure, mGattCallbacks
                            )
                        }
                    } else {
                        mDevice = device
                        mBluetoothGatt = connectGatt(
                            result.device, this@BluetoothPairingService, false, mSecure,
                            mGattCallbacks
                        )
                    }
                } else {
                    notifyError("No valid service in Advertisement")
                }
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
            if (mNextWriteCompletionIntent != null) {
                val intent = Intent(mNextWriteCompletionIntent)
                sendBroadcast(intent)
            }
        } else {
            Log.d(TAG, "handleMessageWrite: unrecognized length=$len")
        }
    }

    private inner class ChatHandler : Handler() {
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
        //Intent intent = new Intent(BLE_COC_CONNECTED);
        //sendBroadcast(intent);
    }

    private fun leCocClientConnect() {
        if (DEBUG) {
            Log.d(TAG, "leCocClientConnect: device=$mDevice, mSecure=$mSecure")
        }
        if (mDevice == null) {
            Log.e(TAG, "leCocClientConnect: mDevice is null")
            return
        }
        // Construct BluetoothChatService with useBle=true parameter
        /* mChatService = new BluetoothChatService(this, new BleCocClientService.ChatHandler(), true);
        mChatService.connect(mDevice, mSecure, mPsm);*/
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
        showMessage("LE Coc Connection Type Checked")
        //Intent intent = new Intent(BLE_CONNECTION_TYPE_CHECKED);
        //sendBroadcast(intent);
    }

    private fun startDiscovery() {
        if (DEBUG) Log.d(TAG, "startDiscovery")
        if (mBluetoothAdapter.isDiscovering) mBluetoothAdapter.cancelDiscovery()
        mBluetoothAdapter.startDiscovery()
    }

    private fun stopDiscovery() {
        if (DEBUG) Log.d(TAG, "stopDiscovery")
        if (mBluetoothAdapter.isDiscovering) mBluetoothAdapter.cancelDiscovery()
    }

    private val mBondStatusReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val state = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )
                when (state) {
                    BluetoothDevice.BOND_BONDED ->
                        if (mBluetoothGatt == null) {
                            if (DEBUG) {
                                Log.d(TAG, "onReceive:BOND_BONDED: calling connectGatt. device="
                                    + device + ", mSecure=" + mSecure)
                        }

                        //BD/EDR(A2DP) or BLE device.
                        mDevice = device!!
                        if (BluetoothUtils.isA2dpDevice(mDevice)) {
                            if (!mBluetoothAdapter.getProfileProxy(
                                    applicationContext,
                                    mA2dpServiceConnection,
                                    BluetoothProfile.A2DP
                                )
                            ) {
                                Log.d(TAG, "A2DP getProfileProxy failed")
                                mA2dpProfile = null
                                //break
                            }
                            // regardless of the UUID content, at this point, we're sure we can initiate a
                            // profile connection.
                            Log.d(TAG, "send MSG_CONNECT message")
                            mHandler.sendEmptyMessageDelayed(
                                MSG_CONNECT_TIMEOUT,
                                CONNECT_TIMEOUT_MS.toLong()
                            )
                            if (!mHandler.hasMessages(MSG_CONNECT)) {
                                mHandler.sendEmptyMessageDelayed(
                                    MSG_CONNECT,
                                    CONNECT_DELAY.toLong()
                                )
                            }
                        } else if (BluetoothUtils.isInputDevice(device)) {
                            Log.d(TAG, "isInputDevice true")
                            if (!mBluetoothAdapter.getProfileProxy(
                                    applicationContext,
                                    mHidHostServiceConnection, BluetoothUtils.HID_HOST
                                )
                            ) {
                                Log.d(TAG, "Hid getProfileProxy failed")
                                //break
                            }
                        }
                    }
                    BluetoothDevice.BOND_NONE -> notifyError("BOND_NONE")
                    BluetoothDevice.BOND_BONDING ->                         // fall through
                        Log.d(TAG, "BOND_BONDING")
                    else -> {}
                }
            }
        }
    }
    private val mHidHostServiceConnection: ServiceListener = object : ServiceListener {
        override fun onServiceDisconnected(profile: Int) {
            Log.w(TAG, "Service disconnected, perhaps unexpectedly")
            //todo : notify
        }

        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (DEBUG) {
                Log.d(TAG, "hid host Connection made to bluetooth proxy.")
            }
            //todo : notify
            if (mDevice != null) Toast.makeText(
                this@BluetoothPairingService,
                mDevice.name + " is connected ",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private var mA2dpProfile: BluetoothA2dp? = null
    private val mA2dpServiceConnection: ServiceListener = object : ServiceListener {
        override fun onServiceDisconnected(profile: Int) {
            Log.w(TAG, "Service disconnected, perhaps unexpectedly")
        }

        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (DEBUG) {
                Log.d(TAG, "a2dp Connection made to bluetooth proxy.")
            }
            mA2dpProfile = proxy as BluetoothA2dp
            if (DEBUG) {
                Log.d(
                    TAG,
                    "Connecting to target: " + mDevice!!.address + " name : " + mDevice!!.name
                )
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothPairingService"
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
        private val SERVICE_UUID = UUID.fromString("00009999-0000-1000-8000-00805f9b34fb")

        /**
         * UUID of the GATT Read Characteristics for LE_PSM value.
         */
        val LE_PSM_CHARACTERISTIC_UUID = UUID.fromString("2d410339-82b6-42aa-b34e-e2e01df8cc1a")
        const val WRITE_VALUE = "CLIENT_TEST"
        private const val NOTIFY_VALUE = "NOTIFY_TEST"
        private const val EXECUTION_DELAY = 1500
        fun connectGatt(
            device: BluetoothDevice, context: Context?,
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
                    device.connectGatt(
                        context, autoConnect, callback,
                        TRANSPORT_MODE_FOR_SECURE_CONNECTION
                    )
                } else {
                    Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show()
                    device.connectGatt(
                        context, autoConnect, callback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                }
            } else {
                Toast.makeText(context, "connectGatt", Toast.LENGTH_SHORT).show()
                device.connectGatt(context, autoConnect, callback)
            }
        }

        fun getConnectionStateName(connectionState: Int): String {
            return when (connectionState) {
                BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
                BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
                BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
                else -> "STATE_UNKNOWN"
            }
        }
    }
}