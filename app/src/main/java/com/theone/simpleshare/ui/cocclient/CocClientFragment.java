package com.theone.simpleshare.ui.cocclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.theone.simpleshare.bluetooth.BleCocClientService;
import com.theone.simpleshare.databinding.FragmentCocClientBinding;


public class CocClientFragment extends Fragment {

    private CocClientViewModel notificationsViewModel;
    private FragmentCocClientBinding binding;
    private final static String TAG = "NotificationsFragment";
    private Context mContext;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        notificationsViewModel =
                new ViewModelProvider(this).get(CocClientViewModel.class);

        binding = FragmentCocClientBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textNotifications;
        notificationsViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        mContext = getActivity();


        Intent intent = new Intent(mContext, BleCocClientService.class);
        intent.setAction(BleCocClientService.BLE_COC_CLIENT_ACTION_LE_INSECURE_CONNECT);
        Toast.makeText(mContext, "start coc insecure client service",Toast.LENGTH_LONG).show();
        mContext.startService(intent);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    private void registerReceiver(){

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleCocClientService.BLE_LE_CONNECTED);
        filter.addAction(BleCocClientService.BLE_GOT_PSM);
        filter.addAction(BleCocClientService.BLE_COC_CONNECTED);
        filter.addAction(BleCocClientService.BLE_CONNECTION_TYPE_CHECKED);
        filter.addAction(BleCocClientService.BLE_DATA_8BYTES_SENT);
        filter.addAction(BleCocClientService.BLE_DATA_8BYTES_READ);
        filter.addAction(BleCocClientService.BLE_DATA_LARGEBUF_READ);
        filter.addAction(BleCocClientService.BLE_LE_DISCONNECTED);
        filter.addAction(BleCocClientService.BLE_BLUETOOTH_DISCONNECTED);
        filter.addAction(BleCocClientService.BLE_BLUETOOTH_DISABLED);
        filter.addAction(BleCocClientService.BLE_BLUETOOTH_MISMATCH_SECURE);
        filter.addAction(BleCocClientService.BLE_BLUETOOTH_MISMATCH_INSECURE);
        filter.addAction(BleCocClientService.BLE_CLIENT_ERROR);

        mContext.registerReceiver(mBroadcast, filter);

    }

    private final BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean showProgressDialog = false;
            //closeDialog();

            String action = intent.getAction();
            String newAction = null;
            // String actionName = null;
            //long previousPassed = mPassed;
            final Intent startIntent = new Intent(mContext, BleCocClientService.class);
            if (action != null) {
                Log.d(TAG, "Processing " + action);
            }
            switch (action) {

                case BleCocClientService.BLE_LE_CONNECTED:
                    // actionName = getString(R.string.ble_coc_client_le_connect);
                    //mTestAdapter.setTestPass(TEST_BLE_LE_CONNECTED);
                    //mPassed |= (1 << TEST_BLE_LE_CONNECTED);
                    // Start LE Service Discovery and then read the PSM
                    Log.d(TAG, "BLE_LE_CONNECTED");
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_GET_PSM;
                    break;

                case BleCocClientService.BLE_GOT_PSM:
                    //actionName = getString(R.string.ble_coc_client_get_psm);
                    //mTestAdapter.setTestPass(TEST_BLE_GOT_PSM);
                    //mPassed |= (1 << TEST_BLE_GOT_PSM);
                    // Connect the LE CoC
                    Log.d(TAG, "BLE_GOT_PSM");
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_COC_CLIENT_CONNECT;
                    break;

                case BleCocClientService.BLE_COC_CONNECTED:
                    //actionName = getString(R.string.ble_coc_client_coc_connect);
                    //mTestAdapter.setTestPass(TEST_BLE_COC_CONNECTED);
                    // mPassed |= (1 << TEST_BLE_COC_CONNECTED);
                    // Check the connection type
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_CHECK_CONNECTION_TYPE;
                    break;

                case BleCocClientService.BLE_CONNECTION_TYPE_CHECKED:
                    //actionName = getString(R.string.ble_coc_client_check_connection_type);
                    //mTestAdapter.setTestPass(TEST_BLE_CONNECTION_TYPE_CHECKED);
                    //mPassed |= (1 << TEST_BLE_CONNECTION_TYPE_CHECKED);
                    // Send 8 bytes
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_SEND_DATA_8BYTES;
                    break;

                case BleCocClientService.BLE_DATA_8BYTES_SENT:
                    //actionName = getString(R.string.ble_coc_client_send_data_8bytes);
                    //mTestAdapter.setTestPass(TEST_BLE_DATA_8BYTES_SENT);
                    //mPassed |= (1 << TEST_BLE_DATA_8BYTES_SENT);
                    // Read 8 bytes
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_READ_DATA_8BYTES;
                    break;

                case BleCocClientService.BLE_DATA_8BYTES_READ:
                    //actionName = getString(R.string.ble_coc_client_receive_data_8bytes);
                    //mTestAdapter.setTestPass(TEST_BLE_DATA_8BYTES_READ);
                    //mPassed |= (1 << TEST_BLE_DATA_8BYTES_READ);
                    // Do data exchanges
                    newAction = BleCocClientService.BLE_COC_CLIENT_ACTION_EXCHANGE_DATA;
                    break;

                case BleCocClientService.BLE_DATA_LARGEBUF_READ:
                    //actionName = getString(R.string.ble_coc_client_data_exchange);
                    //mTestAdapter.setTestPass(TEST_BLE_DATA_EXCHANGED);
                    //mPassed |= (1 << TEST_BLE_DATA_EXCHANGED);
                    // Disconnect
                    newAction = BleCocClientService.BLE_CLIENT_ACTION_CLIENT_DISCONNECT;
                    break;

                case BleCocClientService.BLE_BLUETOOTH_DISCONNECTED:
                    //mTestAdapter.setTestPass(TEST_BLE_CLIENT_DISCONNECTED);
                    //mPassed |= (1 << TEST_BLE_CLIENT_DISCONNECTED);
                    // all tests done
                    newAction = null;
                    break;

                case BleCocClientService.BLE_BLUETOOTH_DISABLED:
                    //showErrorDialog(R.string.ble_bluetooth_disable_title, R.string.ble_bluetooth_disable_message, true);
                    break;

                case BleCocClientService.BLE_BLUETOOTH_MISMATCH_SECURE:
                    //showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_secure_message, true);
                    break;

                case BleCocClientService.BLE_BLUETOOTH_MISMATCH_INSECURE:
                    //showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_insecure_message, true);
                    break;

                default:
                    Log.e(TAG, "onReceive: Error: unhandled action=" + action);
            }

            if (newAction != null) {
                Log.d(TAG, "Starting " + newAction);
                startIntent.setAction(newAction);
                mContext.startService(startIntent);

            }
        }
    };
}