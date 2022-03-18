package com.theone.simpleshare.ui.pairing

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
    private var mItemList: ArrayList<Item>? = null
    var mContext: Context? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recyclerview, parent, false)
        mContext = parent.context
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(mItemList!![position])
    }


    override fun getItemCount(): Int {
        return mItemList!!.size
    }

    fun setItemList(list: ArrayList<Item>?) {
        mItemList = list
        notifyDataSetChanged()
    }

    interface OnItemClickListener {
        fun onItemClick(v: View?, pos: Int, item: Item)
    }

    private var mListener: OnItemClickListener? = null
    fun setOnItemClickListener(listener: OnItemClickListener?) {
        mListener = listener
    }

    fun clear() {
        mItemList!!.clear()
        notifyDataSetChanged()
    }

    open inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var profile: ImageView
        var name: TextView
        var message: TextView
        fun onBind(item: Item) {
            profile.setImageResource(item.resourceId)
            name.text = item.name
            message.text = item.message
        }

        init {
            profile = itemView.findViewById(R.id.profile)
            name = itemView.findViewById<TextView>(R.id.name)
            message = itemView.findViewById<TextView>(R.id.address)
            itemView.setOnClickListener { view ->
                val pos: Int = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    mListener!!.onItemClick(view, pos, mItemList!![pos])
                }
            }
        }
    }
}