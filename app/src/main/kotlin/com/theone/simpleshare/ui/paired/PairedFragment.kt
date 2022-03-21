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

class PairedFragment : Fragment() {
    private lateinit var itemViewModel: ItemViewModel
    private lateinit var binding: FragmentPairedBinding
    private lateinit var mContext: Context
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mRecyclerAdapter: RecyclerAdapter
    private lateinit var mSwipeController: SwipeController
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        itemViewModel = ViewModelProvider(this).get(ItemViewModel::class.java)
        binding = FragmentPairedBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mContext = activity as Context
        itemViewModel.text.observe(getViewLifecycleOwner()){
            s->
                //textView.setText(s);
        }
        itemViewModel.list
            .observe(getViewLifecycleOwner()){ it->
                Log.d(TAG, "onChanged")
                mRecyclerAdapter.notifyDataSetChanged()
            }
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

        mRecyclerAdapter.setItemList(itemViewModel.list.value)
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
    private val mBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (action != null) {
                Log.d(TAG, "Processing $action")
            }
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
                    itemViewModel.list.value?.add(
                        Item(
                            R.drawable.ic_launcher_foreground, device!!.name,
                            device.address, device, connectionState, battLevel
                        )
                    )
                    mRecyclerAdapter.notifyItemInserted(
                        itemViewModel.list.value!!.size - 1
                    )
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

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mContext.unregisterReceiver(mBroadcast)
    }

}