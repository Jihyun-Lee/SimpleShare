package com.theone.simpleshare.ui.paired

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theone.simpleshare.R
import com.theone.simpleshare.bluetooth.BatteryLevelReader
import com.theone.simpleshare.bluetooth.BluetoothPairingService
import com.theone.simpleshare.databinding.FragmentPairedBinding
import com.theone.simpleshare.viewmodel.Item
import com.theone.simpleshare.viewmodel.ItemViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList

@AndroidEntryPoint
class PairedFragment : Fragment() {
    private val itemViewModel: ItemViewModel by viewModels()
    private lateinit var binding: FragmentPairedBinding
    private lateinit var mContext: Context
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mRecyclerAdapter: RecyclerAdapter
    private lateinit var mSwipeController: SwipeController
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPairedBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mContext = activity as Context

        //clear room db
        itemViewModel.deleteAll()
        setupRecyclerView()
        bondedDevicesIntent()
        return root
    }

    companion object {
        private const val TAG = "PairedFragment"
    }
    private fun setupRecyclerView() {
        mRecyclerAdapter = RecyclerAdapter()
        mRecyclerView = binding.recyclerView
        mRecyclerView.apply {
            setLayoutManager(LinearLayoutManager(mContext))
            setLayoutManager(LinearLayoutManager(mContext, RecyclerView.VERTICAL, false))
            setAdapter(mRecyclerAdapter)
        }
        mRecyclerAdapter.setOnItemClickListener(object : RecyclerAdapter.OnItemClickListener{
            override fun onItemClick(v: View?, pos: Int, item: Item) {
                Toast.makeText(mContext, " pos : " + pos + " name : " + item.name, Toast.LENGTH_SHORT).show()
                Log.d(TAG, " pos : " + pos + " name : " + item.name)
                val intent = Intent(mContext, BatteryLevelReader::class.java)
                intent.apply {
                    setAction(BatteryLevelReader.BLUETOOTH_ACTION_GET_BONDED_DEVICES)
                    putExtra(BluetoothDevice.EXTRA_DEVICE, item.device)
                    mContext.startService(this)
                }
            }
        })


        mSwipeController = SwipeController(object : SwipeControllerActions() {
            override fun onRightClicked(position: Int) {
                //Todo: remove bond
                Toast.makeText(getActivity(), "onRightClicked : $position", Toast.LENGTH_SHORT)
                    .show()
                val intent = Intent(mContext, BluetoothPairingService::class.java)
                with(intent){
                    setAction(BluetoothPairingService.BLUETOOTH_ACTION_REMOVE_BOND)
                    val item = mRecyclerAdapter.getItemFromList(position)
                    putExtra(BluetoothDevice.EXTRA_DEVICE, item.device)
                    mContext.startService(this)
                }
                mRecyclerAdapter.removeItemFromList(position)
            }

            override fun onLeftClicked(position: Int) {
                Toast.makeText(getActivity(), "onLeftClicked : $position", Toast.LENGTH_SHORT)
                    .show()
            }
        })
        val itemTouchhelper = ItemTouchHelper(mSwipeController)
        itemTouchhelper.attachToRecyclerView(mRecyclerView)
        mRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                mSwipeController.onDraw(c)
            }
        })
    }

    private fun bondedDevicesIntent (){
            /*refresh rv*/
            mRecyclerAdapter.clear()

            val intent = Intent(mContext, BatteryLevelReader::class.java)
            intent.apply {
                setAction(BatteryLevelReader.BLUETOOTH_ACTION_GET_BONDED_DEVICES)
                mContext.startService(this)
            }

    }
    internal var itemId = 200
    private val mBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            Log.d(TAG, "Processing $action")
            when (action) {
                BatteryLevelReader.Companion.BLUETOOTH_ACTION_NOTIFY_BONDED_DEVICE -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val battLevel: Int = intent.getIntExtra(
                        BatteryLevelReader.Companion.BATTERY_LEVEL,
                        BatteryLevelReader.Companion.NO_BATTTERY_INFO
                    )
                    val connectionState: Int = intent.getIntExtra(
                        BatteryLevelReader.Companion.CONNECTION_STATE,
                        BatteryLevelReader.Companion.NO_CONNECTION_INFO
                    )
                    itemViewModel.insertItem(
                        Item(itemId++,
                            R.drawable.ic_launcher_foreground, device!!.name,
                            device.address, device, connectionState, battLevel
                        )
                    )
                    mRecyclerAdapter.notifyItemInserted(
                        itemViewModel.getItemList().value!!.size - 1
                    )
                    mRecyclerAdapter.setItemList(itemViewModel.getItemList().value as ArrayList<Item>)
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
        filter.addAction(BatteryLevelReader.Companion.BLUETOOTH_ACTION_NOTIFY_BONDED_DEVICE)
        mContext.registerReceiver(mBroadcast, filter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mContext.unregisterReceiver(mBroadcast)
    }

}