package io.a3dv.VIRec

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.a3dv.VIRec.ImuViewContent.SingleAxis
import io.a3dv.VIRec.ImuViewFragment.OnListFragmentInteractionListener
import java.util.Locale

class ImuRecyclerViewAdapter(
    private val mValues: MutableList<SingleAxis?>,
    private val mListener: OnListFragmentInteractionListener?
) : RecyclerView.Adapter<ImuRecyclerViewAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.imu_fragment, parent, false
        )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.mItem = mValues.get(position)
        holder.mIdView.text = holder.mItem!!.id
        holder.mContentView.text = holder.mItem!!.content
        holder.mUnitView.text = FileHelper.fromHtml(holder.mItem!!.unit)
        holder.mView.setOnClickListener(View.OnClickListener { v: View? ->
            if (null != mListener) {
                // Notify the active callbacks interface (the activity, if the
                // fragment is attached to one) that an item has been selected.
                mListener.onListFragmentInteraction(holder.mItem)
            }
        })
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.imu_fragment
    }

    override fun getItemCount(): Int {
        return mValues.size
    }

    fun updateListItem(position: Int, value: Float) {
        mValues.get(position)?.content = String.format(Locale.US, "%.3f", value)
    }

    class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val mIdView: TextView
        val mContentView: TextView
        val mUnitView: TextView
        var mItem: SingleAxis? = null

        init {
            mIdView = mView.findViewById<TextView>(R.id.item_number)
            mContentView = mView.findViewById<TextView>(R.id.content)
            mUnitView = mView.findViewById<TextView>(R.id.unit)
        }

        override fun toString(): String {
            return super.toString() + " '" + mContentView.text + "'"
        }
    }
}
