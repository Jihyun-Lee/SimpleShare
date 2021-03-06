package com.theone.simpleshare.ui.paired

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.theone.simpleshare.R
import com.theone.simpleshare.viewmodel.Item
import java.util.ArrayList

class RecyclerAdapter : RecyclerView.Adapter<RecyclerAdapter.ViewHolder?>() {
    private var mItemList: ArrayList<Item> = ArrayList<Item>()
    private lateinit var mListener: OnItemClickListener
    private lateinit var mContext: Context
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.paired_item_recyclerview, parent, false)
        mContext = parent.getContext()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(mItemList[position])
    }

    fun setItemList(list: ArrayList<Item>?) {
        if (list != null) {
            mItemList = list
        }
        notifyDataSetChanged()
    }

    fun getItemFromList(position: Int): Item = mItemList[position]

    fun removeItemFromList(position: Int) {
        mItemList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, itemCount)
    }

    interface OnItemClickListener {
        fun onItemClick(v: View?, pos: Int, item: Item)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        mListener = listener
    }

    fun clear() {
        mItemList.clear()
        notifyDataSetChanged()
    }

    open inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var profile: ImageView
        var name: TextView
        var address: TextView
        var state: TextView
        var batteryLevel: TextView
        fun onBind(item: Item) {
            profile.setImageResource(item.resourceId)
            name.text=item.name
            address.text=item.address
            state.text = if (item.state != -1) "" else ""
            batteryLevel.text = if (item.batteryLevel != -1) "${item.batteryLevel}%" else ""
        }

        init {
            with(itemView) {
                profile = findViewById(R.id.profile)
                name = findViewById<TextView>(R.id.name)
                address = findViewById<TextView>(R.id.address)
                state = findViewById<TextView>(R.id.state)
                batteryLevel = findViewById<TextView>(R.id.battery_level)
                setOnClickListener { view ->
                    val pos: Int = getAdapterPosition()
                    if (pos != RecyclerView.NO_POSITION) {
                        mListener.onItemClick(view, pos, mItemList[pos])
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = mItemList.size


}