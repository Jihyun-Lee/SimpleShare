package com.theone.simpleshare.ui.cocserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.theone.simpleshare.bluetooth.BleCocServerService
import com.theone.simpleshare.databinding.FragmentCocServerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CocServerFragment : Fragment() {
    private lateinit var cocServerViewModel: CocServerViewModel
    private lateinit var binding: FragmentCocServerBinding
    private lateinit var mContext: Context
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        cocServerViewModel = ViewModelProvider(this).get(CocServerViewModel::class.java)
        binding = FragmentCocServerBinding.inflate(inflater, container, false)
        mContext = activity as Context
        val root: View = binding.root
        val textView: TextView = binding.textDashboard
        cocServerViewModel.text.observe(viewLifecycleOwner){
         s->
            textView.text = s
        }
        binding.button.setOnClickListener {
            Intent(mContext, BleCocServerService::class.java).apply {
                action = BleCocServerService.BLE_ACTION_COC_SERVER_INSECURE
                Toast.makeText(mContext, "start coc insecure server", Toast.LENGTH_LONG).show()
                mContext.startService(this)
            }
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        /*setInfoResources(R.string.ble_coc_client_test_name,
                R.string.ble_coc_insecure_client_test_info, -1);*/

        registerServerBroadcastReceiver()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mContext.unregisterReceiver(mBroadcast)
    }

    fun registerServerBroadcastReceiver() = IntentFilter().apply {
        addAction(BleCocServerService.BLE_LE_CONNECTED)
        addAction(BleCocServerService.BLE_COC_LISTENER_CREATED)
        addAction(BleCocServerService.BLE_PSM_READ)
        addAction(BleCocServerService.BLE_COC_CONNECTED)
        addAction(BleCocServerService.BLE_CONNECTION_TYPE_CHECKED)
        addAction(BleCocServerService.BLE_DATA_8BYTES_READ)
        addAction(BleCocServerService.BLE_DATA_8BYTES_SENT)
        addAction(BleCocServerService.BLE_DATA_LARGEBUF_READ)
        addAction(BleCocServerService.BLE_BLUETOOTH_MISMATCH_SECURE)
        addAction(BleCocServerService.BLE_BLUETOOTH_MISMATCH_INSECURE)
        addAction(BleCocServerService.BLE_SERVER_DISCONNECTED)
        addAction(BleCocServerService.BLE_BLUETOOTH_DISABLED)
        addAction(BleCocServerService.BLE_OPEN_FAIL)
        addAction(BleCocServerService.BLE_ADVERTISE_UNSUPPORTED)
        addAction(BleCocServerService.BLE_ADD_SERVICE_FAIL)
        mContext.registerReceiver(mBroadcast, this)
    }

    private val mBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action:String? = intent.action
            if (DEBUG) {
                Log.d(TAG, "BroadcastReceiver.onReceive: action=$action")
            }
            var newAction: String? = null
            val startIntent = Intent(mContext, BleCocServerService::class.java)
            when (action) {
                BleCocServerService.BLE_BLUETOOTH_DISABLED -> {}
                BleCocServerService.BLE_LE_CONNECTED -> {}
                BleCocServerService.BLE_COC_LISTENER_CREATED -> {}
                BleCocServerService.BLE_PSM_READ -> {}
                BleCocServerService.BLE_COC_CONNECTED -> {}
                BleCocServerService.BLE_CONNECTION_TYPE_CHECKED -> {}
                BleCocServerService.BLE_DATA_8BYTES_READ ->
                    // send the next action to send 8 bytes
                    newAction = BleCocServerService.BLE_COC_SERVER_ACTION_SEND_DATA_8BYTES
                BleCocServerService.BLE_DATA_8BYTES_SENT ->
                    // send the next action to send 8 bytes
                    newAction = BleCocServerService.BLE_COC_SERVER_ACTION_EXCHANGE_DATA
                BleCocServerService.BLE_DATA_LARGEBUF_READ ->                     // Disconnect
                    newAction = BleCocServerService.BLE_COC_SERVER_ACTION_DISCONNECT
                BleCocServerService.BLE_SERVER_DISCONNECTED -> {}
                BleCocServerService.BLE_BLUETOOTH_MISMATCH_SECURE -> {}
                BleCocServerService.BLE_BLUETOOTH_MISMATCH_INSECURE -> {}
                BleCocServerService.BLE_ADVERTISE_UNSUPPORTED -> {}
                BleCocServerService.BLE_OPEN_FAIL -> {}
                BleCocServerService.BLE_ADD_SERVICE_FAIL -> {}
                else -> if (DEBUG) {
                    Log.d(TAG, "Note: BroadcastReceiver.onReceive: unhandled action=$action")
                }
            }
            if (newAction != null) {
                Log.d(TAG, "Starting $newAction")
                startIntent.action = newAction
                mContext.startService(startIntent)
            }
        }
    }

    companion object {
        private const val DEBUG = true
        private const val TAG = "DashboardFragment"
    }
}