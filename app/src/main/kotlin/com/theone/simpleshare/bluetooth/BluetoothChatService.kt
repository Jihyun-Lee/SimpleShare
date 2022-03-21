/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.Context
import android.os.*
import android.util.Log

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
class BluetoothChatService {
    // Member fields
    private val mAdapter: BluetoothAdapter
    private val mHandler: Handler
    private lateinit var mUuid: UUID
    private lateinit var mSecureAcceptThread: AcceptThread
    private lateinit var mInsecureAcceptThread: AcceptThread
    private lateinit var mConnectThread: ConnectThread
    private lateinit var mConnectedThread: ConnectedThread
    private var mState: Int
    private var mBleTransport: Boolean
    public var psm = 0

    companion object {
        // Message types sent from the BluetoothChatService Handler
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5

        // Key names received from the BluetoothChatService Handler
        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"
        val SECURE_UUID = UUID.fromString("8591d757-18ee-45e1-9b12-92875d06ba23")
        val INSECURE_UUID = UUID.fromString("301c214f-91a2-43bf-a795-09d1198a81a7")
        val HANDSFREE_INSECURE_UUID = UUID.fromString("0000111F-0000-1000-8000-00805F9B34FB")

        // Debugging
        private const val TAG = "CtsBluetoothChatService"
        private const val D = true

        // Name for the SDP record when creating server socket
        private const val NAME_SECURE = "CtsBluetoothChatSecure"
        private const val NAME_INSECURE = "CtsBluetoothChatInsecure"

        // Constants that indicate the current connection state
        const val STATE_NONE = 0 // we're doing nothing
        const val STATE_LISTEN = 1 // now listening for incoming connections
        const val STATE_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED = 3 // now connected to a remote device
    }

    /**
     * Return the socket Connection Type.
     */
    @get:Synchronized
    var socketConnectionType = -1
        private set

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    constructor(context: Context, handler: Handler, uuid: UUID) {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        mState = STATE_NONE
        mHandler = handler
        mUuid = uuid
        mBleTransport = false
    }

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     * @param useBle   A flag to use the BLE transport
     */
    constructor(context: Context?, handler: Handler, useBle: Boolean) {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        mState = STATE_NONE
        mHandler = handler
        mBleTransport = useBle
        if (D) Log.d(TAG, "Construct BluetoothChatService: useBle=$useBle")
    }
    /**
     * Return the current connection state.  */// Give the new state to the Handler so the UI Activity can update
    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    @get:Synchronized
    @set:Synchronized
    var state: Int
        get() = mState
        private set(state) {
            if (D) Log.d(TAG, "setState() $mState -> $state")
            mState = state

            // Give the new state to the Handler so the UI Activity can update
            mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
        }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()  */
    @Synchronized
    fun start(secure: Boolean) {
        if (D) Log.d(TAG, "start secure: " + secure + UUID.randomUUID() + " - " + UUID.randomUUID())
        // Cancel any thread attempting to make a connection
        mConnectThread.cancel()


        // Cancel any thread currently running a connection
        mConnectedThread.cancel()
        state = STATE_LISTEN

        // Start the thread to listen on a BluetoothServerSocket
        if (secure) {
            mSecureAcceptThread = AcceptThread(true)
            mSecureAcceptThread.start()
        }
        if (!secure) {
            mInsecureAcceptThread = AcceptThread(false)
            mInsecureAcceptThread.start()
        }
    }


    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect to
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    @Synchronized
    fun connect(device: BluetoothDevice, secure: Boolean) {
        if (!mBleTransport) {
            connect(device, secure, 0)
        } else {
            Log.e(TAG, "connect: Error: LE cannot call this method!")
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect to
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     * @param psm Assigned PSM value
     */
    @Synchronized
    fun connect(device: BluetoothDevice, secure: Boolean, psm: Int) {
        if (D) Log.d(TAG, "connect to: $device, psm: $psm, ble: $mBleTransport")

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            mConnectThread.cancel()
        }

        // Cancel any thread currently running a connection
        mConnectedThread.cancel()


        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device, secure, psm)
        mConnectThread.start()
        state = STATE_CONNECTING
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice, socketType: String?) {
        if (D) Log.d(TAG, "connected, Socket Type: $socketType")

        // Cancel the thread that completed the connection
        mConnectThread.cancel()

        // Cancel any thread currently running a connection
        mConnectedThread.cancel()

        // Cancel the accept thread because we only want to connect to one device
        mSecureAcceptThread.cancel()
        mInsecureAcceptThread.cancel()

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket, socketType)
        mConnectedThread.start()

        // Send the name of the connected device back to the UI Activity
        val msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(DEVICE_NAME, device.name)
        msg.data = bundle
        mHandler.sendMessage(msg)
        state = STATE_CONNECTED
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        if (D) Log.d(TAG, "stop")
        mConnectThread.cancel()
        mConnectedThread.cancel()
        mSecureAcceptThread.cancel()
        mInsecureAcceptThread.cancel()
        state = STATE_NONE
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray?) {
        // Create temporary object
        var r: ConnectedThread?
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState != STATE_CONNECTED) return
            r = mConnectedThread
        }
        // Perform the write unsynchronized
        r!!.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Unable to connect device")
        msg.data = bundle
        mHandler.sendMessage(msg)
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Device connection was lost")
        msg.data = bundle
        mHandler.sendMessage(msg)
    }


    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    internal inner class AcceptThread(secure: Boolean) : Thread() {
        // The local server socket
        private lateinit var mmServerSocket: BluetoothServerSocket
        private val mSocketType: String
        override fun run() {
            if (D) Log.d(
                TAG, "Socket Type: " + mSocketType +
                        " BEGIN mAcceptThread" + this
            )
            name = "AcceptThread$mSocketType"
            lateinit var socket: BluetoothSocket

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                socket = try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmServerSocket.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket Type: $mSocketType accept() failed", e)
                    break
                }

                // If a connection was accepted
                synchronized(this@BluetoothChatService) {
                    when (mState) {
                        STATE_LISTEN, STATE_CONNECTING -> {
                            // Situation normal. Start the connected thread.
                            socketConnectionType = socket.connectionType
                            connected(
                                socket, socket.remoteDevice,
                                mSocketType
                            )
                        }
                        STATE_NONE, STATE_CONNECTED ->                             // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close()
                            } catch (e: IOException) {
                                Log.e(TAG, "Could not close unwanted socket", e)
                            }
                        else -> {
                            Log.e(TAG, "unknown state")
                        }
                    }
                }
            }
            if (D) {
                Log.i(
                    TAG, "END mAcceptThread, socket Type: " + mSocketType
                            + ", SocketConnectionType: " + socketConnectionType
                )
            }
        }

        fun cancel() {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this)
            try {
                mmServerSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e)
            }
        }

        init {
            mSocketType = if (secure) "Secure" else "Insecure"

            // Create a new listening server socket
            mAdapter.apply {
                try {
                    mmServerSocket = if (mBleTransport) {
                        if (secure) {
                            listenUsingL2capChannel()
                        } else {
                            listenUsingInsecureL2capChannel()
                        }
                    } else {
                        if (secure) {
                            listenUsingRfcommWithServiceRecord(NAME_SECURE, mUuid)
                        } else {
                            listenUsingInsecureRfcommWithServiceRecord(
                                NAME_INSECURE,
                                mUuid
                            )
                        }
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "Socket Type: $mSocketType, le: $mBleTransport listen() failed", e)
                }
            }
            if (mBleTransport) {
                // Get the assigned PSM value
                psm = mmServerSocket.psm
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    internal inner class ConnectThread : Thread {
        private var mmSocket: BluetoothSocket
        private var mmDevice: BluetoothDevice
        private lateinit var mSocketType: String

        constructor(device: BluetoothDevice, secure: Boolean) {
            if (mBleTransport) {
                Log.e(TAG, "ConnectThread: Error: LE should not call this constructor")
            }
            mmDevice = device
            mmSocket = connectThreadCommon(device, secure, 0)
        }

        constructor(device: BluetoothDevice, secure: Boolean, psm: Int) {
            mmDevice = device
            mmSocket = connectThreadCommon(device, secure, psm)
        }

        private fun connectThreadCommon(
            device: BluetoothDevice,
            secure: Boolean,
            psm: Int
        ): BluetoothSocket {
            lateinit var tmp: BluetoothSocket
            mSocketType = if (secure) "Secure" else "Insecure"

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = if (mBleTransport) {
                    if (secure) {
                        device.createL2capChannel(psm)
                    } else {
                        device.createInsecureL2capChannel(psm)
                    }
                } else {
                    if (secure) {
                        device.createRfcommSocketToServiceRecord(mUuid)
                    } else {
                        device.createInsecureRfcommSocketToServiceRecord(mUuid)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e)
            }
            socketConnectionType = tmp.connectionType
            return tmp
        }

        override fun run() {
            Log.i(
                TAG, "BEGIN mConnectThread SocketType:" + mSocketType
                        + ", mSocketConnectionType: " + socketConnectionType
            )
            name = "ConnectThread$mSocketType"

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect()
            } catch (e: IOException) {
                Log.e(TAG, "connect() failed ", e)
                // Close the socket
                try {
                    mmSocket.close()
                } catch (e2: IOException) {
                    Log.e(
                        TAG, "unable to close() " + mSocketType +
                                " socket during connection failure", e2
                    )
                }
                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothChatService) {
                //mConnectThread = null
            }


            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType)
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect $mSocketType socket failed", e)
            }
        }
    }


    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    internal inner class ConnectedThread(socket: BluetoothSocket?, socketType: String?) : Thread() {
        private lateinit var mmSocket: BluetoothSocket
        private lateinit var mmInStream: InputStream
        private lateinit var mmOutStream: OutputStream
        override fun run() {
            Log.i(TAG, "BEGIN mConnectedThread")
            val buffer = ByteArray(1024)
            var bytes: Int

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer)

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                        .sendToTarget()
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        fun write(buffer: ByteArray?) {
            try {
                mmOutStream.write(buffer)
                mmOutStream.flush()

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }

        init {
            Log.d(TAG, "create ConnectedThread: $socketType")
            if (socket != null) {
                mmSocket = socket
            }
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket?.inputStream
                tmpOut = socket?.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }
            if (tmpIn != null) {
                mmInStream = tmpIn
            }
            if (tmpOut != null) {
                mmOutStream = tmpOut
            }
        }
    }
}