package com.theone.simpleshare.bluetooth;


import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;

import android.annotation.SuppressLint;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BatteryLevelReader extends Service {
    private final static String TAG = "BatteryLevelReader";
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

    public static final String BLUETOOTH_ACTION_READ_BATTERY_LEVEL =
            "com.theone.simpleshare.bluetooth.BLUETOOTH_ACTION_READ_BATTERY_LEVEL";

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


    private static final UUID GATT_BATTERY_SERVICE_UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");






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
    private BluetoothGatt mDeviceGatt;

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
                        break;
                    case MSG_CONNECT:
                        break;
                    default:
                        break;
                }
            }
        };
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
                case BLUETOOTH_ACTION_READ_BATTERY_LEVEL: {

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = device;

                    if (mDevice != null &&
                            (mDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE ||
                                    mDevice.getType() == BluetoothDevice.DEVICE_TYPE_DUAL)) {
                        // Only LE devices support GATT
                        // Todo : need autoConnect???
                        Log.d(TAG, "try connectGatt on "+mDevice.getName());
                        mDeviceGatt = mDevice.connectGatt( BatteryLevelReader.this, true, new GattBatteryCallbacks());
                    } else {

                        if (mDevice.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC){
                            Log.e(TAG, "DEVICE_TYPE_CLASSIC device..");
                        } else {
                            Log.e(TAG, "~~~~~~~~error");
                            Log.e(TAG, "mDevice.getName() : "+ mDevice.getName());
                            Log.e(TAG, "mDevice.getType() : "+ mDevice.getType());
                        }

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

        unregisterReceiver(mBondStatusReceiver);
        Log.d(TAG, "BluetoothPairingService onDestroy");
        mTaskQueue.quit();

    }

    public static BluetoothGatt connectGatt(BluetoothDevice device, Context context,
                                            boolean autoConnect, BluetoothGattCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show();
            return device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE);
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
                //show toast
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
    private class GattBatteryCallbacks extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG) {
                Log.d(TAG, "Connection status:" + status + " state:" + newState);
            }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (DEBUG) {
                    Log.e(TAG, "Service discovery failure on " + gatt);
                }
                return;
            }

            final BluetoothGattService battService = gatt.getService(GATT_BATTERY_SERVICE_UUID);
            if (battService == null) {
                if (DEBUG) {
                    Log.d(TAG, "No battery service");
                }
                return;
            }

            final BluetoothGattCharacteristic battLevel =
                    battService.getCharacteristic(GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID);
            if (battLevel == null) {
                if (DEBUG) {
                    Log.d(TAG, "No battery level");
                }
                return;
            }

            gatt.readCharacteristic(battLevel);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (DEBUG) {
                    Log.e(TAG, "Read characteristic failure on " + gatt + " " + characteristic);
                }
                return;
            }

            if (GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                final int batteryLevel =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        /*if (mBatteryPref != null && !mUnpairing) {
                            mBatteryPref.setTitle(getString(R.string.accessory_battery,
                                    batteryLevel));
                            mBatteryPref.setVisible(true);
                        }*/

                        Log.d(TAG, "battery level : " + batteryLevel);
                        Toast.makeText( BatteryLevelReader.this,"battery level : " + batteryLevel ,Toast.LENGTH_SHORT ).show();
                    }
                });
            }
        }
    }


}
