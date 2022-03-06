package com.theone.simpleshare.ui.cocserver;

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

import com.theone.simpleshare.bluetooth.BleCocServerService;
import com.theone.simpleshare.databinding.FragmentCocServerBinding;


public class CocServerFragment extends Fragment {

    private static final boolean DEBUG = true;
    private CocServerViewModel cocServerViewModel;
    private FragmentCocServerBinding binding;
    private final static String TAG = "DashboardFragment";
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        cocServerViewModel =
                new ViewModelProvider(this).get(CocServerViewModel.class);

        binding = FragmentCocServerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textDashboard;
        cocServerViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        binding.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mIntent = new Intent(mContext, BleCocServerService.class);
                mIntent.setAction(BleCocServerService.BLE_ACTION_COC_SERVER_INSECURE);
                Toast.makeText(mContext, "start coc insecure server",Toast.LENGTH_LONG).show();
                mContext.startService(mIntent);
            }
        });

        return root;
    }
    private Intent mIntent;
    private Context mContext;
    @Override
    public void onResume() {
        super.onResume();
        /*setInfoResources(R.string.ble_coc_client_test_name,
                R.string.ble_coc_insecure_client_test_info, -1);*/
        mContext = getActivity();
        registerServerBroadcastReceiver();

    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        mContext.unregisterReceiver(mBroadcast);
    }


    void registerServerBroadcastReceiver(){
        IntentFilter filter = new IntentFilter();

        filter.addAction(BleCocServerService.BLE_LE_CONNECTED);
        filter.addAction(BleCocServerService.BLE_COC_LISTENER_CREATED);
        filter.addAction(BleCocServerService.BLE_PSM_READ);
        filter.addAction(BleCocServerService.BLE_COC_CONNECTED);
        filter.addAction(BleCocServerService.BLE_CONNECTION_TYPE_CHECKED);
        filter.addAction(BleCocServerService.BLE_DATA_8BYTES_READ);
        filter.addAction(BleCocServerService.BLE_DATA_8BYTES_SENT);
        filter.addAction(BleCocServerService.BLE_DATA_LARGEBUF_READ);

        filter.addAction(BleCocServerService.BLE_BLUETOOTH_MISMATCH_SECURE);
        filter.addAction(BleCocServerService.BLE_BLUETOOTH_MISMATCH_INSECURE);
        filter.addAction(BleCocServerService.BLE_SERVER_DISCONNECTED);

        filter.addAction(BleCocServerService.BLE_BLUETOOTH_DISABLED);
        filter.addAction(BleCocServerService.BLE_OPEN_FAIL);
        filter.addAction(BleCocServerService.BLE_ADVERTISE_UNSUPPORTED);
        filter.addAction(BleCocServerService.BLE_ADD_SERVICE_FAIL);

        mContext.registerReceiver(mBroadcast, filter);
    }
    private BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DEBUG) {
                Log.d(TAG, "BroadcastReceiver.onReceive: action=" + action);
            }
            String newAction = null;
            final Intent startIntent = new Intent(mContext, BleCocServerService.class);

            switch (action) {
                case BleCocServerService.BLE_BLUETOOTH_DISABLED:
                    break;
                case BleCocServerService.BLE_LE_CONNECTED:

                    break;
                case BleCocServerService.BLE_COC_LISTENER_CREATED:

                    break;
                case BleCocServerService.BLE_PSM_READ:

                    break;
                case BleCocServerService.BLE_COC_CONNECTED:

                    break;
                case BleCocServerService.BLE_CONNECTION_TYPE_CHECKED:

                    break;
                case BleCocServerService.BLE_DATA_8BYTES_READ:

                    // send the next action to send 8 bytes
                    newAction = BleCocServerService.BLE_COC_SERVER_ACTION_SEND_DATA_8BYTES;
                    break;
                case BleCocServerService.BLE_DATA_8BYTES_SENT:

                    // send the next action to send 8 bytes
                    newAction = BleCocServerService.BLE_COC_SERVER_ACTION_EXCHANGE_DATA;
                    break;
                case BleCocServerService.BLE_DATA_LARGEBUF_READ:
                    // Disconnect
                    newAction = BleCocServerService.BLE_COC_SERVER_ACTION_DISCONNECT;
                    break;
                case BleCocServerService.BLE_SERVER_DISCONNECTED:
                    // all tests done
                    break;
                case BleCocServerService.BLE_BLUETOOTH_MISMATCH_SECURE:

                    break;
                case BleCocServerService.BLE_BLUETOOTH_MISMATCH_INSECURE:

                    break;
                case BleCocServerService.BLE_ADVERTISE_UNSUPPORTED:

                    break;
                case BleCocServerService.BLE_OPEN_FAIL:

                    break;
                case BleCocServerService.BLE_ADD_SERVICE_FAIL:

                    break;
                default:
                    if (DEBUG) {
                        Log.d(TAG, "Note: BroadcastReceiver.onReceive: unhandled action=" + action);
                    }
            }



            if (newAction != null) {
                Log.d(TAG, "Starting " + newAction);
                startIntent.setAction(newAction);
                mContext.startService(startIntent);
            }

        }
    };
}