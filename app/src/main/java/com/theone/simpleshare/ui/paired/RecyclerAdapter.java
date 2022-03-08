package com.theone.simpleshare.ui.paired;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.theone.simpleshare.R;

import java.util.ArrayList;


public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {


    private ArrayList<PairedItem> mItemList;
    Context mContext;
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.paired_item_recyclerview, parent, false);
        mContext = parent.getContext();
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.onBind(mItemList.get(position));
    }
    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    public void setItemList(ArrayList<PairedItem> list){
        this.mItemList = list;
        notifyDataSetChanged();
    }
    public PairedItem getItemFromList(int position){
        return this.mItemList.get(position);
    }

    public void removeItemFromList(int position){
        this.mItemList.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, getItemCount());
    }

    public interface OnItemClickListener {
        void onItemClick(View v, int pos , PairedItem item);
    }
    private  OnItemClickListener mListener=null;
    public void setOnItemClickListener( OnItemClickListener listener){
        this.mListener = listener;
    }

    public void clear(){
        mItemList.clear();
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profile;
        TextView name, address, state, batteryLevel;
        public ViewHolder(@NonNull View itemView){
            super(itemView);

            profile = itemView.findViewById(R.id.profile);
            name = itemView.findViewById(R.id.name);
            address = itemView.findViewById(R.id.address);
            state = itemView.findViewById(R.id.state);
            batteryLevel = itemView.findViewById(R.id.battery_level);

            itemView.setOnClickListener( new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    int pos = getAdapterPosition();
                    if( pos != RecyclerView.NO_POSITION){
                        mListener.onItemClick(view, pos , mItemList.get(pos) );
                    }
                }
            });

        }

        void onBind(PairedItem item){
            profile.setImageResource(item.getResourceId());
            name.setText(item.getName());
            address.setText(item.getAddress());
            state.setText(""+ item.state);
            batteryLevel.setText(item.batteryLevel + "%");

        }

    }

}
