package com.theone.simpleshare.bluetooth;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;

import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothPairingService extends Service {
    private final static String TAG = "BluetoothPairingService";
    public static final boolean DEBUG = true;
    private static final int MSG_CONNECT_TIMEOUT = 1;
    private static final int MSG_CONNECT = 2;
    private static final int MSG_REMOVE_BOND = 3;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int CONNECT_DELAY = 1000;

    private static final int TRANSPORT_MODE_FOR_SECURE_CONNECTION = BluetoothDevice.TRANSPORT_LE;

    public static final String BLUETOOTH_ACTION_START_SCAN =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_START_SCAN";
    public static final String BLUETOOTH_ACTION_START_PAIRING =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_START_PAIRING";
    public static final String BLUETOOTH_ACTION_REMOVE_BOND =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_REMOVE_BOND";

    private static final UUID SERVICE_UUID =
            UUID.fromString("00009999-0000-1000-8000-00805f9b34fb");

    /**
     * UUID of the GATT Read Characteristics for LE_PSM value.
     */
    public static final UUID LE_PSM_CHARACTERISTIC_UUID =
            UUID.fromString("2d410339-82b6-42aa-b34e-e2e01df8cc1a");

    public static final String WRITE_VALUE = "CLIENT_TEST";
    private static final String NOTIFY_VALUE = "NOTIFY_TEST";
    private int mBleState = STATE_DISCONNECTED;
    private static final int EXECUTION_DELAY = 1500;

    // current test category
    private String mCurrentAction;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothLeScanner mScanner;
    private Handler mHandler;
    private boolean mSecure;
    private boolean mValidityService;
    private int mPsm;
    private BluetoothChatService mChatService;
    private int mNextReadExpectedLen = -1;
    private String mNextReadCompletionIntent;
    private int mTotalReadLen = 0;
    private byte mNextReadByte;
    private int mNextWriteExpectedLen = -1;
    private String mNextWriteCompletionIntent = null;

    // Handler for communicating task with peer.
    private TestTaskQueue mTaskQueue;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBondStatusReceiver, filter);

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mScanner = mBluetoothAdapter.getBluetoothLeScanner();


        mTaskQueue = new TestTaskQueue(getClass().getName() + "_taskHandlerThread");


        mHandler = new Handler() {
            @Override
            public void handleMessage(Message m) {
                switch (m.what) {
                    case MSG_CONNECT_TIMEOUT:
                        //failed();
                        break;
                    case MSG_CONNECT:

                        if (mA2dpProfile == null) {
                            break;
                        }
                        Log.d(TAG, "try to connect");
                        connect(mDevice);

                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if ( STATE_CONNECTED == mA2dpProfile.getConnectionState(mDevice) ){
                                    Log.d(TAG, mDevice.getName() + " connected");
                                    Toast.makeText(BluetoothPairingService.this,mDevice.getName() + " connected", Toast.LENGTH_SHORT).show();
                                    //todo : display battery and state in rv

                                } else {
                                    Log.d(TAG, mDevice.getName() + " not connected");
                                    notifyError(mDevice.getName() + " not connected");
                                }
                            }
                        }, 2000);

                        break;

                    default:
                        break;
                }
            }
        };
    }

    private void connect(BluetoothDevice device) {
        Log.d(TAG,"connect");
        try{

            Method connect = Class.forName("android.bluetooth.BluetoothA2dp").getMethod("connect", BluetoothDevice.class);
            connect.invoke(mA2dpProfile, device);

        }catch(Exception e){
            Log.d(TAG, e.toString());
        }
    }

    private void removeBond(BluetoothDevice device) {
        Log.d(TAG,"removeBond");
        try{

            Method connect = Class.forName("android.bluetooth.BluetoothDevice").getMethod("removeBond");
            connect.invoke(device);

        }catch(Exception e){
            Log.d(TAG, e.toString());
        }
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {

        if (!mBluetoothAdapter.isEnabled()) {
            notifyBluetoothDisabled();
        } else {
            mTaskQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onTestFinish(intent);
                }
            }, EXECUTION_DELAY);
        }
        return START_NOT_STICKY;
    }

    private void onTestFinish(@NonNull Intent intent) {
        mCurrentAction = intent.getAction();
        Log.d(TAG, "onTestFinish action : " + mCurrentAction);
        if (mCurrentAction != null) {
            switch (mCurrentAction) {
                case BLUETOOTH_ACTION_START_SCAN:

                    if ( mBluetoothAdapter.isDiscovering()){
                        stopDiscovery();
                    }
                    startDiscovery();
                    break;

                case BLUETOOTH_ACTION_START_PAIRING: {

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    stopDiscovery();


                    if (device != null) {
                        String name = device.getName();
                        String address = device.getAddress();
                        Log.d(TAG, "name : " + name + " address : " + address);
                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            if (!device.createBond()) {
                                notifyError("Failed to call create bond");
                            }
                        } else {
                            notifyError(device.getName() + " is already paired");
                        }

                    } else {
                        Log.e(TAG, "ERROR : device is null");
                        notifyError("Failed to get device from discovery");
                    }

                }
                    break;
                case BLUETOOTH_ACTION_REMOVE_BOND: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        removeBond(device);
                    }
                }
                    break;
                default:
                    Log.e(TAG, "Error: Unhandled or invalid action=" + mCurrentAction);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        stopDiscovery();
        unregisterReceiver(mBondStatusReceiver);
        Log.d(TAG, "BluetoothPairingService onDestroy");
        mTaskQueue.quit();

    }

    public static BluetoothGatt connectGatt(BluetoothDevice device, Context context,
                                            boolean autoConnect, boolean isSecure,
                                            BluetoothGattCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isSecure) {
                if (TRANSPORT_MODE_FOR_SECURE_CONNECTION == BluetoothDevice.TRANSPORT_AUTO) {
                    Toast.makeText(context, "connectGatt(transport=AUTO)", Toast.LENGTH_SHORT)
                            .show();
                } else {
                    Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show();
                }
                return device.connectGatt(context, autoConnect, callback,
                        TRANSPORT_MODE_FOR_SECURE_CONNECTION);
            } else {
                Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show();
                return device.connectGatt(context, autoConnect, callback,
                        BluetoothDevice.TRANSPORT_LE);
            }
        } else {
            Toast.makeText(context, "connectGatt", Toast.LENGTH_SHORT).show();
            return device.connectGatt(context, autoConnect, callback);
        }
    }

    private void readCharacteristic(UUID uuid) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(uuid);
        if (characteristic != null) {
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    private void notifyError(String message) {
        showMessage(message);
        Log.e(TAG, message);

       // Intent intent = new Intent(BLE_CLIENT_ERROR);
       // sendBroadcast(intent);
    }

    private void notifyMismatchSecure() {
       // Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_SECURE);
       // sendBroadcast(intent);
    }

    private void notifyMismatchInsecure() {
        //Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_INSECURE);
        //sendBroadcast(intent);
    }

    private void notifyBluetoothDisabled() {
        //Intent intent = new Intent(BLE_BLUETOOTH_DISABLED);
        //sendBroadcast(intent);
    }

    private void notifyConnected() {
        showMessage("Bluetooth LE GATT connected");
        // Intent intent = new Intent(BLE_LE_CONNECTED);
        //sendBroadcast(intent);
    }

    private void startLeDiscovery() {
        // Start Service Discovery
        if (mBluetoothGatt != null && mBleState == STATE_CONNECTED) {
            mBluetoothGatt.discoverServices();
        } else {
            showMessage("Bluetooth LE GATT not connected.");
        }
    }

    private void notifyDisconnected() {
        showMessage("Bluetooth LE disconnected");
        //Intent intent = new Intent(BLE_BLUETOOTH_DISCONNECTED);
        //sendBroadcast(intent);
    }

    private void notifyServicesDiscovered() {
        showMessage("Service discovered");
        // Find the LE_COC_PSM characteristics
        if (DEBUG) {
            Log.d(TAG, "notifyServicesDiscovered: Next step is to read the PSM char.");
        }
        readCharacteristic(LE_PSM_CHARACTERISTIC_UUID);
    }

    private BluetoothGattService getService() {
        BluetoothGattService service = null;

        if (mBluetoothGatt != null) {
            service = mBluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                showMessage("GATT Service not found");
            }
        }
        return service;
    }

    private BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        BluetoothGattCharacteristic characteristic = null;

        BluetoothGattService service = getService();
        if (service != null) {
            characteristic = service.getCharacteristic(uuid);
            if (characteristic == null) {
                showMessage("Characteristic not found");
            }
        }
        return characteristic;
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                Log.d(TAG, msg);
                Toast.makeText(BluetoothPairingService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG) {
                Log.d(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == STATE_CONNECTED) {
                    mBleState = newState;
                    int bondState = gatt.getDevice().getBondState();
                    boolean bonded = false;
                    BluetoothDevice target = gatt.getDevice();
                    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                    if (!pairedDevices.isEmpty()) {
                        for (BluetoothDevice device : pairedDevices) {
                            if (device.getAddress().equals(target.getAddress())) {
                                bonded = true;
                                break;
                            }
                        }
                    }
                    if (mSecure && ((bondState == BluetoothDevice.BOND_NONE) || !bonded)) {
                        // not pairing and execute Secure Test
                        Log.e(TAG, "BluetoothGattCallback.onConnectionStateChange: "
                                + "Not paired but execute secure test");
                        mBluetoothGatt.disconnect();
                        notifyMismatchSecure();
                    } else if (!mSecure && ((bondState != BluetoothDevice.BOND_NONE) || bonded)) {
                        // already pairing and execute Insecure Test
                        Log.e(TAG, "BluetoothGattCallback.onConnectionStateChange: "
                                + "Paired but execute insecure test");
                        mBluetoothGatt.disconnect();
                        notifyMismatchInsecure();
                    } else {
                        notifyConnected();
                    }
                } else if (status == STATE_DISCONNECTED) {
                    mBleState = newState;
                    mSecure = false;
                    mBluetoothGatt.close();
                    notifyDisconnected();
                }
            } else {
                showMessage("Failed to connect: " + status + " , newState = " + newState);
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (DEBUG) {
                Log.d(TAG, "onServicesDiscovered: status=" + status);
            }
            if ((status == BluetoothGatt.GATT_SUCCESS) &&
                    (mBluetoothGatt.getService(SERVICE_UUID) != null)) {
                notifyServicesDiscovered();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicRead: status=" + status + ", uuid=" + uid);
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String value = characteristic.getStringValue(0);
                if (characteristic.getUuid().equals(LE_PSM_CHARACTERISTIC_UUID)) {
                    mPsm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    if (DEBUG) {
                        Log.d(TAG, "onCharacteristicRead: reading PSM=" + mPsm);
                    }
                    //Intent intent = new Intent(BLE_GOT_PSM);
                    //sendBroadcast(intent);
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "onCharacteristicRead: Note: unknown uuid=" + uid);
                    }
                }
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                notifyError("Not Permission Read: " + status + " : " + uid);
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                notifyError("Not Authentication Read: " + status + " : " + uid);
            } else {
                notifyError("Failed to read characteristic: " + status + " : " + uid);
            }
        }
    };

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult");
            if (mBluetoothGatt == null) {
                // verify the validity of the advertisement packet.
                mValidityService = false;
                List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
                for (ParcelUuid uuid : uuids) {
                    if (uuid.getUuid().equals(BleCocServerService.ADV_COC_SERVICE_UUID)) {
                        if (DEBUG) {
                            Log.d(TAG, "onScanResult: Found ADV with LE CoC Service UUID.");
                        }
                        mValidityService = true;
                        break;
                    }
                }
                if (mValidityService) {
                    //stopScan();
                    stopDiscovery();

                    BluetoothDevice device = result.getDevice();
                    if (DEBUG) {
                        Log.d(TAG, "onScanResult: Found ADV with CoC UUID on device="
                                + device);
                    }
                    if (mSecure) {
                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            if (!device.createBond()) {
                                notifyError("Failed to call create bond");
                            }
                        } else {
                            mDevice = device;
                            mBluetoothGatt = connectGatt(result.getDevice(), BluetoothPairingService.this, false,
                                    mSecure, mGattCallbacks);
                        }
                    } else {
                        mDevice = device;
                        mBluetoothGatt = connectGatt(result.getDevice(), BluetoothPairingService.this, false, mSecure,
                                mGattCallbacks);
                    }
                } else {
                    notifyError("No valid service in Advertisement");
                }
            }
        }
    };

    private boolean checkReadBufContent(byte[] buf, int len) {
        // Check that the content is correct
        for (int i = 0; i < len; i++) {
            if (buf[i] != mNextReadByte) {
                Log.e(TAG, "handleMessageRead: Error: wrong byte content. buf["
                        + i + "]=" + buf[i] + " not equal to " + mNextReadByte);
                return false;
            }
            mNextReadByte++;
        }
        return true;
    }

    private void handleMessageRead(Message msg) {
        byte[] buf = (byte[])msg.obj;
        int len = msg.arg1;
        if (len <= 0) {
            return;
        }
        mTotalReadLen += len;
        if (DEBUG) {
            Log.d(TAG, "handleMessageRead: receive buffer of length=" + len + ", mTotalReadLen="
                    + mTotalReadLen + ", mNextReadExpectedLen=" + mNextReadExpectedLen);
        }

        if (mNextReadExpectedLen == mTotalReadLen) {
            if (!checkReadBufContent(buf, len)) {
                mNextReadExpectedLen = -1;
                return;
            }
            showMessage("Read " + len + " bytes");
            if (DEBUG) {
                Log.d(TAG, "handleMessageRead: broadcast intent " + mNextReadCompletionIntent);
            }
            Intent intent = new Intent(mNextReadCompletionIntent);
            sendBroadcast(intent);
            mNextReadExpectedLen = -1;
            mNextReadCompletionIntent = null;
            mTotalReadLen = 0;
        } else if (mNextReadExpectedLen > mTotalReadLen) {
            if (!checkReadBufContent(buf, len)) {
                mNextReadExpectedLen = -1;
                return;
            }
        } else if (mNextReadExpectedLen < mTotalReadLen) {
            Log.e(TAG, "handleMessageRead: Unexpected receive buffer of length=" + len
                    + ", expected len=" + mNextReadExpectedLen);
        }
    }

    private void sendMessage(byte[] buf) {
        mChatService.write(buf);
    }

    private void handleMessageWrite(Message msg) {
        byte[] buffer = (byte[]) msg.obj;
        int len = buffer.length;

        showMessage("LE Coc Client wrote " + len + " bytes");
        if (len == mNextWriteExpectedLen) {
            if (mNextWriteCompletionIntent != null) {
                Intent intent = new Intent(mNextWriteCompletionIntent);
                sendBroadcast(intent);
            }
        } else {
            Log.d(TAG, "handleMessageWrite: unrecognized length=" + len);
        }
    }

    private class ChatHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (DEBUG) {
                Log.d(TAG, "ChatHandler.handleMessage: msg=" + msg);
            }
            int state = msg.arg1;
            switch (msg.what) {
                case BluetoothChatService.MESSAGE_STATE_CHANGE:
                    if (state == BluetoothChatService.STATE_CONNECTED) {
                        // LE CoC is established
                        notifyLeCocClientConnected();
                    }
                    break;
                case BluetoothChatService.MESSAGE_READ:
                    handleMessageRead(msg);
                    break;
                case BluetoothChatService.MESSAGE_WRITE:
                    handleMessageWrite(msg);
                    break;
            }
        }
    }

    private void notifyLeCocClientConnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyLeCocClientConnected: device=" + mDevice + ", mSecure=" + mSecure);
        }
        showMessage("Bluetooth LE Coc connected");
        //Intent intent = new Intent(BLE_COC_CONNECTED);
        //sendBroadcast(intent);
    }

    private void leCocClientConnect() {
        if (DEBUG) {
            Log.d(TAG, "leCocClientConnect: device=" + mDevice + ", mSecure=" + mSecure);
        }
        if (mDevice == null) {
            Log.e(TAG, "leCocClientConnect: mDevice is null");
            return;
        }
        // Construct BluetoothChatService with useBle=true parameter
       /* mChatService = new BluetoothChatService(this, new BleCocClientService.ChatHandler(), true);
        mChatService.connect(mDevice, mSecure, mPsm);*/
    }

    private void leCheckConnectionType() {
        if (mChatService == null) {
            Log.e(TAG, "leCheckConnectionType: no LE Coc connection");
            return;
        }
        int type = mChatService.getSocketConnectionType();
        if (type != BluetoothSocket.TYPE_L2CAP) {
            Log.e(TAG, "leCheckConnectionType: invalid connection type=" + type);
            return;
        }
        showMessage("LE Coc Connection Type Checked");
        //Intent intent = new Intent(BLE_CONNECTION_TYPE_CHECKED);
        //sendBroadcast(intent);
    }

    private void sendData8bytes() {
        if (DEBUG) Log.d(TAG, "sendData8bytes");

        final byte[] buf = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        mNextWriteExpectedLen = 8;
        //mNextWriteCompletionIntent = BLE_DATA_8BYTES_SENT;
        sendMessage(buf);
    }

    private void sendDataLargeBuf() {
        final int len = BleCocServerService.TEST_DATA_EXCHANGE_BUFSIZE;
        if (DEBUG) Log.d(TAG, "sendDataLargeBuf of size=" + len);

        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            buf[i] = (byte)(i + 1);
        }
        mNextWriteExpectedLen = len;
        mNextWriteCompletionIntent = null;
        sendMessage(buf);
    }

    private void readData8bytes() {
        mNextReadExpectedLen = 8;
        //mNextReadCompletionIntent = BLE_DATA_8BYTES_READ;
        mNextReadByte = 1;
    }

    private void readDataLargeBuf() {
        mNextReadExpectedLen = BleCocServerService.TEST_DATA_EXCHANGE_BUFSIZE;
       // mNextReadCompletionIntent = BLE_DATA_LARGEBUF_READ;
        mNextReadByte = 1;
    }




    private void startDiscovery() {
        if (DEBUG) Log.d(TAG, "startDiscovery");
        if( mBluetoothAdapter.isDiscovering())
            mBluetoothAdapter.cancelDiscovery();
        mBluetoothAdapter.startDiscovery();
    }
    private void stopDiscovery() {
        if (DEBUG) Log.d(TAG, "stopDiscovery");
        if( mBluetoothAdapter.isDiscovering())
            mBluetoothAdapter.cancelDiscovery();
    }


    private final BroadcastReceiver mBondStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE);
                switch (state) {
                    case BluetoothDevice.BOND_BONDED:
                        if (mBluetoothGatt == null) {
                            if (DEBUG) {
                                Log.d(TAG, "onReceive:BOND_BONDED: calling connectGatt. device="
                                        + device + ", mSecure=" + mSecure);
                            }

                            //BD/EDR(A2DP) or BLE device.
                            mDevice = device;

                            if( !mBluetoothAdapter.getProfileProxy(getApplicationContext(), mServiceConnection, BluetoothProfile.A2DP)) {
                                Log.d(TAG, "A2DP getProfileProxy failed");
                                mA2dpProfile = null;
                                break;
                            }
                            // regardless of the UUID content, at this point, we're sure we can initiate a
                            // profile connection.
                            Log.d(TAG, "send MSG_CONNECT message");
                            mHandler.sendEmptyMessageDelayed(MSG_CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS);
                            if (!mHandler.hasMessages(MSG_CONNECT)) {
                                mHandler.sendEmptyMessageDelayed(MSG_CONNECT, CONNECT_DELAY);
                            }
                        }
                        break;
                    case BluetoothDevice.BOND_NONE:
                        notifyError("Failed to create bond");
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        // fall through
                    default:
                        // wait for next state
                        break;
                }
            }
        }
    };

    private BluetoothA2dp mA2dpProfile;
    private final BluetoothProfile.ServiceListener mServiceConnection =
            new BluetoothProfile.ServiceListener() {

                @Override
                public void onServiceDisconnected(int profile) {
                    Log.w(TAG, "Service disconnected, perhaps unexpectedly");
                    //unregisterConnectionStateReceiver();
                    //closeA2dpProfileProxy();
                    //failed();
                }

                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (DEBUG) {
                        Log.d(TAG, "Connection made to bluetooth proxy." );
                    }
                    mA2dpProfile = (BluetoothA2dp) proxy;
                    if (DEBUG) {
                        Log.d(TAG, "Connecting to target: " + mDevice.getAddress() + " name : "+mDevice.getName());
                    }

                    //registerConnectionStateReceiver();
                    // We initiate SDP because connecting to A2DP before services are discovered leads to
                    // error.
                    //mTarget.fetchUuidsWithSdp();
                }
            };

    static String getConnectionStateName(int connectionState) {
        switch (connectionState) {
            case STATE_DISCONNECTED:
                return "STATE_DISCONNECTED";
            case STATE_CONNECTING:
                return "STATE_CONNECTING";
            case STATE_CONNECTED:
                return "STATE_CONNECTED";
            case STATE_DISCONNECTING:
                return "STATE_DISCONNECTING";
            default:
                return "STATE_UNKNOWN";
        }
    }


}
