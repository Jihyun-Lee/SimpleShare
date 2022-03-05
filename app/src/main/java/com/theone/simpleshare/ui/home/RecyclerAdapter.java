package com.theone.simpleshare.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.theone.simpleshare.R;
import java.util.ArrayList;


public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {

    private ArrayList<Item> mItemList;
    Context mContext;
    @NonNull
    @Override
    public RecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recyclerview, parent, false);
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

    public void setItemList(ArrayList<Item> list){
        this.mItemList = list;
        notifyDataSetChanged();
    }
    public interface OnItemClickListener {
        void onItemClick(View v, int pos , Item item);
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
        TextView name;
        TextView message;

        public ViewHolder(@NonNull View itemView){
            super(itemView);

            profile = itemView.findViewById(R.id.profile);
            name = itemView.findViewById(R.id.name);
            message = itemView.findViewById(R.id.message);

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

        void onBind(Item item){
            profile.setImageResource(item.getResourceId());
            name.setText(item.getName());
            message.setText(item.getMessage());
        }

    }

}
