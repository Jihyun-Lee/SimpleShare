package com.theone.simpleshare.ui.pairing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theone.simpleshare.R
import com.theone.simpleshare.bluetooth.BluetoothPairingService
import com.theone.simpleshare.bluetooth.BluetoothUtils.isA2dpDevice
import com.theone.simpleshare.bluetooth.BluetoothUtils.isInputDevice
import com.theone.simpleshare.databinding.FragmentPairingBinding
import java.util.ArrayList

class PairingFragment : Fragment() {
    private lateinit var pairingViewModel: PairingViewModel
    private lateinit var binding: FragmentPairingBinding
    private lateinit var mContext: Context
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mRecyclerAdapter: RecyclerAdapter

    companion object {
        private const val TAG = "PairingFragment"
    }
    enum class ParingMode {
        AUTO_PAIRING_MODE, MANUAL_PAIRING_MODE
    }

    private var mPairingMode = ParingMode.MANUAL_PAIRING_MODE
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        pairingViewModel = ViewModelProvider(this).get(PairingViewModel::class.java)
        binding = FragmentPairingBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mContext = activity as Context
        pairingViewModel.list
            .observe(getViewLifecycleOwner()){
                items: ArrayList<Item>->
                    //Log.d(TAG, "onChanged")
            }

        mRecyclerAdapter = RecyclerAdapter()
        mRecyclerView = binding!!.recyclerView
        mRecyclerView.setLayoutManager(LinearLayoutManager(mContext))
        mRecyclerView.setLayoutManager(LinearLayoutManager(mContext, RecyclerView.VERTICAL, false))
        mRecyclerView.setAdapter(mRecyclerAdapter)
        mRecyclerAdapter.setOnItemClickListener (
            object : RecyclerAdapter.OnItemClickListener {
                override fun onItemClick(v: View?, pos: Int, item: Item) {
                    //Toast.makeText(mContext, " pos : "+pos , Toast.LENGTH_SHORT).show();
                    val intent = Intent(mContext, BluetoothPairingService::class.java)
                    intent.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_PAIRING)
                    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, item.device)
                    Toast.makeText(mContext, "start pairing service", Toast.LENGTH_LONG).show()
                    mContext.startService(intent)
                }
            })
        mRecyclerAdapter.setItemList(pairingViewModel.list.value)
        binding.manualPairing.setOnClickListener {
            val intent = Intent(mContext, BluetoothPairingService::class.java)
            intent.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_SCAN)
            Toast.makeText(mContext, "start scan", Toast.LENGTH_LONG).show()
            mPairingMode = ParingMode.MANUAL_PAIRING_MODE
            mContext.startService(intent)

            /*refresh rv*/
            mRecyclerAdapter!!.clear()
        }
        binding!!.autoPairing.setOnClickListener {
            val intent = Intent(mContext, BluetoothPairingService::class.java)
            intent.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_SCAN)
            Toast.makeText(mContext, "start auto pairing", Toast.LENGTH_LONG).show()
            mPairingMode = ParingMode.AUTO_PAIRING_MODE
            mContext.startService(intent)
            /*refresh rv*/
            mRecyclerAdapter!!.clear()
        }
        binding.bondedDevice.setOnClickListener { drawBondedDevices() }
        return root
    }

    private fun drawBondedDevices() {
        val bluetoothManager: BluetoothManager =
            mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.getAdapter()

        /*refresh rv*/
        mRecyclerAdapter.clear()
        for (device in bluetoothAdapter.getBondedDevices()) {
            pairingViewModel.list.value?.add(
                Item(
                    R.drawable.ic_launcher_foreground, device.getName(),
                    device.getAddress(), device
                )
            )
        }
    }

    private val mBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (action != null) {
                Log.d(TAG, "Processing $action")
            }
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    device?.let { device ->
                        if (isA2dpDevice(device) || isInputDevice(device)) {
                            Log.d(TAG, "device : " + device.getName())
                            pairingViewModel.list.value?.add(
                                Item(
                                    R.drawable.ic_launcher_foreground, device.getName(),
                                    device.getAddress(), device
                                )
                            )
                            mRecyclerAdapter.notifyItemInserted(
                                pairingViewModel.list.value!!.size
                            )
                            if (mPairingMode == ParingMode.AUTO_PAIRING_MODE) {
                                Toast.makeText(
                                    mContext,
                                    "AUTO PAIR :" + device.getName(),
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.d(TAG, "AUTO PAIR :" + device.getName())
                                val i = Intent(mContext, BluetoothPairingService::class.java)
                                i.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_PAIRING)
                                i.putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                                mContext.startService(i)
                            }

                        }
                    }
                }
                else -> Log.e(TAG, "onReceive: Error: unhandled action=$action")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
    }

    private fun registerReceiver() {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        mContext.registerReceiver(mBroadcast, filter)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mContext.unregisterReceiver(mBroadcast)
    }

}