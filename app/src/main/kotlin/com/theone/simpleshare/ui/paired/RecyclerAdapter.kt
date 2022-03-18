package com.theone.simpleshare.ui.paired

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.theone.simpleshare.R
import java.util.ArrayList

class RecyclerAdapter : RecyclerView.Adapter<RecyclerAdapter.ViewHolder?>() {
    private lateinit var mItemList: ArrayList<PairedItem>
    private lateinit var mListener: OnItemClickListener
    private lateinit var mContext: Context
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.paired_item_recyclerview, parent, false)
        mContext = parent.getContext()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(mItemList!![position])
    }

    fun setItemList(list: ArrayList<PairedItem>?) {
        if (list != null) {
            mItemList = list
        }
        notifyDataSetChanged()
    }

    fun getItemFromList(position: Int): PairedItem = mItemList[position]

    fun removeItemFromList(position: Int) {
        mItemList!!.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, itemCount)
    }

    interface OnItemClickListener {
        fun onItemClick(v: View?, pos: Int, item: PairedItem)
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
        fun onBind(item: PairedItem) {
            profile.setImageResource(item.resourceId)
            name.text=item.name
            address.text=item.address
            state.text = "" + item.state
            batteryLevel.text = item.batteryLevel.toString() + "%"
        }

        init {
            profile = itemView.findViewById(R.id.profile)
            name = itemView.findViewById<TextView>(R.id.name)
            address = itemView.findViewById<TextView>(R.id.address)
            state = itemView.findViewById<TextView>(R.id.state)
            batteryLevel = itemView.findViewById<TextView>(R.id.battery_level)
            itemView.setOnClickListener { view ->
                val pos: Int = getAdapterPosition()
                if (pos != RecyclerView.NO_POSITION) {
                    mListener.onItemClick(view, pos, mItemList[pos])
                }
            }
        }
    }

    override fun getItemCount(): Int = mItemList.size


}