package com.theone.simpleshare.ui.paired

import com.theone.simpleshare.bluetooth.BluetoothUtils.isA2dpDevice
import com.theone.simpleshare.bluetooth.BluetoothUtils.isInputDevice
import android.bluetooth.BluetoothDevice
import com.theone.simpleshare.ui.paired.PairedViewModel
import com.theone.simpleshare.ui.paired.SwipeController
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import com.theone.simpleshare.ui.paired.PairedItem
import com.theone.simpleshare.ui.paired.PairedFragment
import android.widget.Toast
import android.content.Intent
import com.theone.simpleshare.bluetooth.BatteryLevelReader
import com.theone.simpleshare.ui.paired.SwipeControllerActions
import com.theone.simpleshare.bluetooth.BluetoothPairingService
import android.content.BroadcastReceiver
import com.theone.simpleshare.R
import android.content.IntentFilter
import android.widget.TextView
import com.theone.simpleshare.ui.paired.ButtonsState
import android.graphics.RectF
import android.view.View.OnTouchListener
import android.view.MotionEvent
import com.theone.simpleshare.ui.pairing.PairingViewModel
import com.theone.simpleshare.ui.pairing.PairingFragment.ParingMode
import com.theone.simpleshare.ui.pairing.PairingFragment
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import com.theone.simpleshare.ui.cocclient.CocClientViewModel
import com.theone.simpleshare.bluetooth.BleCocClientService
import com.theone.simpleshare.ui.cocclient.CocClientFragment
import com.theone.simpleshare.ui.cocserver.CocServerViewModel
import com.theone.simpleshare.bluetooth.BleCocServerService
import com.theone.simpleshare.ui.cocserver.CocServerFragment

class PairedItem(
    var resourceId: Int,
    var name: String,
    var address: String,
    var device: BluetoothDevice,
    var state: Int,
    var batteryLevel: Int
)