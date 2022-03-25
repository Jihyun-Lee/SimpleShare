package com.theone.simpleshare.ui.cocclient

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
import androidx.annotation.NonNull
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.theone.simpleshare.bluetooth.BleCocClientService
import com.theone.simpleshare.databinding.FragmentCocClientBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
@AndroidEntryPoint
class CocClientFragment : Fragment() {
    private lateinit var notificationsViewModel: CocClientViewModel
    private lateinit var binding: FragmentCocClientBinding
    private lateinit var mContext: Context

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        notificationsViewModel = ViewModelProvider(this).get(CocClientViewModel::class.java)
        binding = FragmentCocClientBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val textView: TextView = binding.textNotifications
        notificationsViewModel.text
            .observe(getViewLifecycleOwner()){ s->
                    textView.setText(s)
            }

        mContext = activity as Context
        mContext.let { it ->
            with(Intent(it, BleCocClientService::class.java)){
                setAction(BleCocClientService.BLE_COC_CLIENT_ACTION_LE_INSECURE_CONNECT)
                Toast.makeText(it, "start coc insecure client service", Toast.LENGTH_LONG).show()
                it.startService(this)
            }
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun registerReceiver() =
            IntentFilter().apply {
            addAction(BleCocClientService.BLE_LE_CONNECTED)
            addAction(BleCocClientService.BLE_GOT_PSM)
            addAction(BleCocClientService.BLE_COC_CONNECTED)
            addAction(BleCocClientService.BLE_CONNECTION_TYPE_CHECKED)
            addAction(BleCocClientService.BLE_DATA_8BYTES_SENT)
            addAction(BleCocClientService.BLE_DATA_8BYTES_READ)
            addAction(BleCocClientService.BLE_DATA_LARGEBUF_READ)
            addAction(BleCocClientService.BLE_LE_DISCONNECTED)
            addAction(BleCocClientService.BLE_BLUETOOTH_DISCONNECTED)
            addAction(BleCocClientService.BLE_BLUETOOTH_DISABLED)
            addAction(BleCocClientService.BLE_BLUETOOTH_MISMATCH_SECURE)
            addAction(BleCocClientService.BLE_BLUETOOTH_MISMATCH_INSECURE)
            addAction(BleCocClientService.BLE_CLIENT_ERROR)
            mContext.registerReceiver(mBroadcast, this)
        }



    private val mBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val showProgressDialog = false
            //closeDialog();
            val action: String? = intent.getAction()
            var newAction: String? = null
            // String actionName = null;
            //long previousPassed = mPassed;
            val startIntent = Intent(mContext, BleCocClientService::class.java)
            if (action != null) {
                Log.d(TAG, "Processing $action")
            }
            when (action) {
                BleCocClientService.BLE_LE_CONNECTED -> {
                    // actionName = getString(R.string.ble_coc_client_le_connect);
                    //mTestAdapter.setTestPass(TEST_BLE_LE_CONNECTED);
                    //mPassed |= (1 << TEST_BLE_LE_CONNECTED);
                    // Start LE Service Discovery and then read the PSM
                    Log.d(TAG, "BLE_LE_CONNECTED")
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_GET_PSM
                }
                BleCocClientService.BLE_GOT_PSM -> {
                    //actionName = getString(R.string.ble_coc_client_get_psm);
                    //mTestAdapter.setTestPass(TEST_BLE_GOT_PSM);
                    //mPassed |= (1 << TEST_BLE_GOT_PSM);
                    // Connect the LE CoC
                    Log.d(TAG, "BLE_GOT_PSM")
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_COC_CLIENT_CONNECT
                }
                BleCocClientService.BLE_COC_CONNECTED ->                     //actionName = getString(R.string.ble_coc_client_coc_connect);
                    //mTestAdapter.setTestPass(TEST_BLE_COC_CONNECTED);
                    // mPassed |= (1 << TEST_BLE_COC_CONNECTED);
                    // Check the connection type
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_CHECK_CONNECTION_TYPE
                BleCocClientService.BLE_CONNECTION_TYPE_CHECKED ->                     //actionName = getString(R.string.ble_coc_client_check_connection_type);
                    //mTestAdapter.setTestPass(TEST_BLE_CONNECTION_TYPE_CHECKED);
                    //mPassed |= (1 << TEST_BLE_CONNECTION_TYPE_CHECKED);
                    // Send 8 bytes
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_SEND_DATA_8BYTES
                BleCocClientService.BLE_DATA_8BYTES_SENT ->                     //actionName = getString(R.string.ble_coc_client_send_data_8bytes);
                    //mTestAdapter.setTestPass(TEST_BLE_DATA_8BYTES_SENT);
                    //mPassed |= (1 << TEST_BLE_DATA_8BYTES_SENT);
                    // Read 8 bytes
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_READ_DATA_8BYTES
                BleCocClientService.BLE_DATA_8BYTES_READ ->                     //actionName = getString(R.string.ble_coc_client_receive_data_8bytes);
                    //mTestAdapter.setTestPass(TEST_BLE_DATA_8BYTES_READ);
                    //mPassed |= (1 << TEST_BLE_DATA_8BYTES_READ);
                    // Do data exchanges
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_EXCHANGE_DATA
                BleCocClientService.BLE_DATA_LARGEBUF_READ ->                     //actionName = getString(R.string.ble_coc_client_data_exchange);
                    //mTestAdapter.setTestPass(TEST_BLE_DATA_EXCHANGED);
                    //mPassed |= (1 << TEST_BLE_DATA_EXCHANGED);
                    // Disconnect
                    newAction = BleCocClientService.BLE_CLIENT_ACTION_CLIENT_DISCONNECT
                BleCocClientService.BLE_BLUETOOTH_DISCONNECTED ->                     //mTestAdapter.setTestPass(TEST_BLE_CLIENT_DISCONNECTED);
                    //mPassed |= (1 << TEST_BLE_CLIENT_DISCONNECTED);
                    // all tests done
                    newAction = null
                BleCocClientService.BLE_BLUETOOTH_DISABLED -> {}
                BleCocClientService.BLE_BLUETOOTH_MISMATCH_SECURE -> {}
                BleCocClientService.BLE_BLUETOOTH_MISMATCH_INSECURE -> {}
                else -> Log.e(TAG, "onReceive: Error: unhandled action=$action")
            }
            if (newAction != null) {
                Log.d(TAG, "Starting $newAction")
                startIntent.setAction(newAction)
                mContext.startService(startIntent)
            }
        }
    }

    companion object {
        private const val TAG = "NotificationsFragment"
    }
}