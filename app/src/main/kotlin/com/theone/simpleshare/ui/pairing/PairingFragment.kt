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
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theone.simpleshare.MainActivity
import com.theone.simpleshare.R
import com.theone.simpleshare.bluetooth.BluetoothPairingService
import com.theone.simpleshare.bluetooth.BluetoothUtils.getResourceIcon
import com.theone.simpleshare.bluetooth.BluetoothUtils.isA2dpDevice
import com.theone.simpleshare.bluetooth.BluetoothUtils.isInputDevice
import com.theone.simpleshare.databinding.FragmentPairingBinding
import com.theone.simpleshare.viewmodel.Item
import com.theone.simpleshare.viewmodel.ItemViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ActivityContext
import io.reactivex.internal.schedulers.IoScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.Thread.sleep
import java.util.*

//https://developer88.tistory.com/349
@AndroidEntryPoint
class PairingFragment : Fragment() {
    private val itemViewModel: ItemViewModel by viewModels()
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
        binding = FragmentPairingBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mContext = activity as Context

        //test
        itemViewModel.deleteAll()

        mRecyclerAdapter = RecyclerAdapter()
        mRecyclerView = binding.recyclerView
        with(mRecyclerView) {
            setLayoutManager(LinearLayoutManager(mContext))
            setLayoutManager(LinearLayoutManager(mContext, RecyclerView.VERTICAL, false))
            setAdapter(mRecyclerAdapter)
        }
        mRecyclerAdapter.setOnItemClickListener (
            object : RecyclerAdapter.OnItemClickListener {
                override fun onItemClick(v: View?, pos: Int, item: Item) {
                    //Toast.makeText(mContext, " pos : "+pos , Toast.LENGTH_SHORT).show();
                    val intent = Intent(mContext, BluetoothPairingService::class.java)
                    intent.apply {
                        setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_PAIRING)
                        putExtra(BluetoothDevice.EXTRA_DEVICE, item.device)
                        Toast.makeText(mContext, "start pairing service", Toast.LENGTH_LONG).show()
                        mContext.startService(this)
                    }

                }
            })
        val data =  itemViewModel.getItemList().observe(this){ list ->

            mRecyclerAdapter.setItemList( list as ArrayList<Item>?)

        }

//        if ( itemViewModel.getItemList().value ==null){
//            Log.d("easy", "item list is empty" )
//            var list = ArrayList<Item>()
//            val device:BluetoothDevice? = null
//            list.add(Item(1,0,"empty","empty",device, -1,-1))
//            mRecyclerAdapter.setItemList(list)
//        } else {
//            mRecyclerAdapter.setItemList(itemViewModel.getItemList().value as ArrayList<Item>?)
//        }
        with(binding) {
            manualPairing.setOnClickListener {
                val intent = Intent(mContext, BluetoothPairingService::class.java)
                with(intent) {
                    setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_SCAN)
                    Toast.makeText(mContext, "start scan", Toast.LENGTH_LONG).show()
                    mPairingMode = ParingMode.MANUAL_PAIRING_MODE
                    mContext.startService(this)
                }
                /*refresh rv*/
                mRecyclerAdapter.clear()
            }
            autoPairing.setOnClickListener {
                val intent = Intent(mContext, BluetoothPairingService::class.java)
                with(intent) {
                    setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_SCAN)
                    mContext.startService(this)
                }

                Toast.makeText(mContext, "start auto pairing", Toast.LENGTH_LONG).show()
                mPairingMode = ParingMode.AUTO_PAIRING_MODE
                /*refresh rv*/
                mRecyclerAdapter.clear()
            }
            bondedDevice.setOnClickListener {
                drawBondedDevices()
            }

            roomDbTest.setOnClickListener {
              val fruits = listOf("apple","banna","kiwi","cherry")
              val res=  fruits.asSequence().filter {

                    Log.d(TAG, "checking length of $it")
                    it.length > 2
                }
                    .map {
                        Log.d(TAG, "mapping to the length of $it")
                        "${it.length}"
                    }.take(2)


                Log.d(TAG,"" +res.toList() )



            }
        }
        return root
    }

    private fun drawBondedDevices() {
        val bluetoothManager: BluetoothManager =
            mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.getAdapter()

        with(bluetoothAdapter){
            if(isDiscovering)
                cancelDiscovery();
        }

        /*refresh rv*/
        mRecyclerAdapter.clear()
        for (device in bluetoothAdapter.getBondedDevices()) {
            itemViewModel.insertItem(
                Item(1,
                    R.drawable.ic_launcher_foreground, device.getName(),
                    device.getAddress(), device, -1,-1)
            )
        }
    }
    var itemId = 100
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
                            itemViewModel.insertItem(
                                Item(itemId++,
                                    getResourceIcon(device), device.getName(),
                                    device.getAddress(), device, -1,-1
                                )
                            )
                             itemViewModel.getItemList().observe(this@PairingFragment){
                                with(mRecyclerAdapter){
                                    setItemList( it as ArrayList<Item>?)
                                    notifyItemInserted( it.size - 1 )
                                }
                            }

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

    override fun onDestroyView() {
        super.onDestroyView()
        mContext.unregisterReceiver(mBroadcast)
    }

}