package com.theone.simpleshare.ui.paired;



import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.theone.simpleshare.R;
import com.theone.simpleshare.bluetooth.BatteryLevelReader;
import com.theone.simpleshare.bluetooth.BluetoothPairingService;
import com.theone.simpleshare.databinding.FragmentPairedBinding;

import java.util.ArrayList;
import java.util.Set;

public class PairedFragment extends Fragment {

    private PairedViewModel pairedViewModel;
    private FragmentPairedBinding binding;
    private final static String TAG = "PairedFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;
    private RecyclerAdapter mRecyclerAdapter;
    private SwipeController mSwipeController;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        pairedViewModel =
                new ViewModelProvider(this).get(PairedViewModel.class);

        binding = FragmentPairedBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        mContext = getActivity();


        pairedViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                //ui
                //textView.setText(s);

            }
        });

        pairedViewModel.getList().observe(getViewLifecycleOwner(), new Observer<ArrayList<Item>>() {
            @Override
            public void onChanged(ArrayList<Item> items) {
                //ui
                Log.d(TAG, "onChanged");
                mRecyclerAdapter.notifyDataSetChanged();

            }
        });

        setupRecyclerView();
        drawBondedDevices();

        return root;
    }
    private void setupRecyclerView() {

        mRecyclerAdapter = new RecyclerAdapter();
        mRecyclerView = binding.recyclerView;
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext, RecyclerView.VERTICAL, false));
        mRecyclerView.setAdapter(mRecyclerAdapter);

        mRecyclerAdapter.setOnItemClickListener(new RecyclerAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int pos, Item item) {
                Toast.makeText(mContext, " pos : "+pos + " name : " + item.name , Toast.LENGTH_SHORT).show();
                Log.d(TAG, " pos : "+pos + " name : " + item.name);
                Intent intent = new Intent(mContext, BatteryLevelReader.class);
                intent.setAction(BatteryLevelReader.BLUETOOTH_ACTION_READ_BATTERY_LEVEL);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, item.device);
                mContext.startService(intent);
            }
        });

        //mItemList = new ArrayList<>();
        //mRecyclerAdapter.setItemList(mItemList);
        mRecyclerAdapter.setItemList(pairedViewModel.getList().getValue());




        mSwipeController = new SwipeController(new SwipeControllerActions() {
            @Override
            public void onRightClicked(int position) {
                //Todo: remove bond
                Toast.makeText(getActivity(), "onRightClicked : "+position,Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(mContext, BluetoothPairingService.class);
                intent.setAction(BluetoothPairingService.BLUETOOTH_ACTION_REMOVE_BOND);
                Item item = mRecyclerAdapter.getItemFromList(position);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, item.device);
                mContext.startService(intent);

                mRecyclerAdapter.removeItemFromList(position);


            }

            @Override
            public void onLeftClicked(int position) {
                //Todo: edit
                Toast.makeText(getActivity(), "onLeftClicked : "+position,Toast.LENGTH_SHORT).show();
            }
        });

        ItemTouchHelper itemTouchhelper = new ItemTouchHelper(mSwipeController);
        itemTouchhelper.attachToRecyclerView(mRecyclerView);

        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
                mSwipeController.onDraw(c);
            }
        });
    }
    private void drawBondedDevices(){
        BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        /*refresh rv*/
        mRecyclerAdapter.clear();
        Set<BluetoothDevice> devSet =  bluetoothAdapter.getBondedDevices();
        if( devSet.size() != 0) {
            for (BluetoothDevice device : devSet) {
                pairedViewModel.getList().getValue().add(new Item(R.drawable.ic_launcher_foreground, device.getName(),
                        device.getAddress(), device));

            }
        } else {
            //add dummy item
            pairedViewModel.getList().getValue().add(new Item(R.drawable.ic_launcher_foreground, "empty",
                          "empty", null));

        }
        mRecyclerAdapter.notifyDataSetChanged();
    }

    private final BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action != null) {
                Log.d(TAG, "Processing " + action);
            }
            switch (action) {

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
        //filter.addAction(BluetoothDevice.ACTION_FOUND);
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