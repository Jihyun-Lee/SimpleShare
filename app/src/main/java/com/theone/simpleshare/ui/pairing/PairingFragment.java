package com.theone.simpleshare.ui.pairing;



import static com.theone.simpleshare.bluetooth.BluetoothUtils.isA2dpDevice;
import static com.theone.simpleshare.bluetooth.BluetoothUtils.isInputDevice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.theone.simpleshare.R;
import com.theone.simpleshare.bluetooth.BluetoothPairingService;
import com.theone.simpleshare.databinding.FragmentPairingBinding;

import java.util.ArrayList;

public class PairingFragment extends Fragment {

    private PairingViewModel pairingViewModel;
    private FragmentPairingBinding binding;
    private final static String TAG = "PairingFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;

    private RecyclerAdapter mRecyclerAdapter;

    private enum ParingMode {AUTO_PAIRING_MODE, MANUAL_PAIRING_MODE}

    private ParingMode mPairingMode = ParingMode.MANUAL_PAIRING_MODE;



    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        pairingViewModel =
                new ViewModelProvider(this).get(PairingViewModel.class);

        binding = FragmentPairingBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        mContext = getActivity();



        pairingViewModel.getList().observe(getViewLifecycleOwner(), new Observer<ArrayList<Item>>() {
            @Override
            public void onChanged(ArrayList<Item> items) {
                Log.d(TAG, "onChanged");
            }
        });
        mRecyclerAdapter = new RecyclerAdapter();
        mRecyclerView = binding.recyclerView;
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext, RecyclerView.VERTICAL, false));
        mRecyclerView.setAdapter(mRecyclerAdapter);

        mRecyclerAdapter.setOnItemClickListener(new RecyclerAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int pos, Item item) {
                //Toast.makeText(mContext, " pos : "+pos , Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(mContext, BluetoothPairingService.class);

                intent.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_PAIRING);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, item.device);

                Toast.makeText(mContext, "start pairing service",Toast.LENGTH_LONG).show();
                mContext.startService(intent);

            }
        });

        mRecyclerAdapter.setItemList(pairingViewModel.getList().getValue());
        binding.manualPairing.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(mContext, BluetoothPairingService.class);
                intent.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_SCAN);
                Toast.makeText(mContext, "start scan",Toast.LENGTH_LONG).show();

                mPairingMode = ParingMode.MANUAL_PAIRING_MODE;

                mContext.startService(intent);

                /*refresh rv*/
                mRecyclerAdapter.clear();
            }
        });
        binding.autoPairing.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(mContext, BluetoothPairingService.class);
                intent.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_SCAN);


                Toast.makeText(mContext, "start auto pairing",Toast.LENGTH_LONG).show();

                mPairingMode = ParingMode.AUTO_PAIRING_MODE;
                mContext.startService(intent);
                /*refresh rv*/
                mRecyclerAdapter.clear();

            }
        });
        binding.bondedDevice.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                drawBondedDevices();
            }
        });
        return root;
    }

    private void drawBondedDevices(){
        BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        /*refresh rv*/
        mRecyclerAdapter.clear();

        for(BluetoothDevice device :bluetoothAdapter.getBondedDevices()){
            pairingViewModel.getList().getValue().add(new Item(R.drawable.ic_launcher_foreground, device.getName(),
                    device.getAddress(), device));

        }
    }

    private final BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action != null) {
                Log.d(TAG, "Processing " + action);
            }
            switch (action) {

                case BluetoothDevice.ACTION_FOUND :
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        /*Deal with HID and A2DP device*/
                        if ( isA2dpDevice(device)|| isInputDevice(device) ) {
                            Log.d(TAG, "device : " + device.getName());
                            pairingViewModel.getList().getValue().add( new Item(R.drawable.ic_launcher_foreground, device.getName(),
                                    device.getAddress(), device));
                            mRecyclerAdapter.notifyItemInserted(pairingViewModel.getList().getValue().size());

                            if(mPairingMode == ParingMode.AUTO_PAIRING_MODE){
                                Toast.makeText(mContext, "AUTO PAIR :" +device.getName(),Toast.LENGTH_LONG).show();
                                Log.d(TAG, "AUTO PAIR :" +device.getName());

                                Intent i = new Intent(mContext, BluetoothPairingService.class);
                                i.setAction(BluetoothPairingService.BLUETOOTH_ACTION_START_PAIRING);
                                i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);

                                mContext.startService(i);
                            }
                        } else {
                            if( device.getName() != null) {
                                pairingViewModel.getList().getValue().add(new Item(R.drawable.ic_launcher_foreground, device.getName(),
                                        device.getAddress(), device));
                                mRecyclerAdapter.notifyItemInserted(pairingViewModel.getList().getValue().size());
                            } else {
                                Log.w(TAG, "empty device name");
                            }
                        }

                    }

                    break;

                default:
                    Log.e(TAG, "onReceive: Error: unhandled action=" + action);
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver();
    }

    private void registerReceiver(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        mContext.unregisterReceiver(mBroadcast);
    }
}