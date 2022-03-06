package com.theone.simpleshare.ui.paired;



import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
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
import com.theone.simpleshare.bluetooth.BluetoothPairingService;
import com.theone.simpleshare.databinding.FragmentPairedBinding;
import com.theone.simpleshare.databinding.FragmentPairingBinding;

import java.util.ArrayList;
import java.util.Set;

public class PairedFragment extends Fragment {

    private PairedViewModel pairingViewModel;
    private FragmentPairedBinding binding;
    private final static String TAG = "HomeFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;
    private ArrayList<Item> mItemList;
    private RecyclerAdapter mRecyclerAdapter;
    private SwipeController mSwipeController;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        pairingViewModel =
                new ViewModelProvider(this).get(PairedViewModel.class);

        binding = FragmentPairedBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        mContext = getActivity();


        pairingViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                //textView.setText(s);
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
                Toast.makeText(mContext, " pos : "+pos , Toast.LENGTH_SHORT).show();


            }
        });

        mItemList = new ArrayList<>();
        mRecyclerAdapter.setItemList(mItemList);



        mSwipeController = new SwipeController(new SwipeControllerActions() {
            @Override
            public void onRightClicked(int position) {
                //Todo: remove bond
                Toast.makeText(getActivity(), "onRightClicked : "+position,Toast.LENGTH_SHORT).show();
               /* mRecyclerAdapter.players.remove(position);
                mRecyclerAdapter.notifyItemRemoved(position);
                mRecyclerAdapter.notifyItemRangeChanged(position, mAdapter.getItemCount());*/
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
        Log.d(TAG, "dev set size : " + devSet.size());

        if( devSet.size() != 0) {

            for (BluetoothDevice device : devSet) {
                Log.d(TAG, "add : " + device.getName());
                for( int i = 0 ; i < 20; i++)
                    mItemList.add(new Item(R.drawable.ic_launcher_foreground, device.getName(),
                            device.getAddress(), device));

            }
        } else {
            mItemList.add(new Item(R.drawable.ic_launcher_foreground, "empty",
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