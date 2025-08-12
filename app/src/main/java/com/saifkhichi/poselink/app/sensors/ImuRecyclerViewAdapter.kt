package io.a3dv.VIRec.core.sensors

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.app.sensors.ImuViewFragment
import com.saifkhichi.poselink.core.sensors.ImuViewContent
import com.saifkhichi.poselink.storage.FileHelper
import java.util.Locale

class ImuRecyclerViewAdapter(
    private val mValues: MutableList<ImuViewContent.SingleAxis?>,
    private val mListener: ImuViewFragment.OnListFragmentInteractionListener?
) : RecyclerView.Adapter<ImuRecyclerViewAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.imu_fragment, parent, false
        )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.mItem = mValues[position]
        holder.mIdView.text = holder.mItem!!.id
        holder.mContentView.text = holder.mItem!!.content
        holder.mUnitView.text = FileHelper.fromHtml(holder.mItem!!.unit)
        holder.mView.setOnClickListener { v: View? ->
            mListener?.onListFragmentInteraction(holder.mItem)
        }
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
        val mIdView: TextView = mView.findViewById(R.id.item_number)
        val mContentView: TextView = mView.findViewById(R.id.content)
        val mUnitView: TextView = mView.findViewById(R.id.unit)
        var mItem: ImuViewContent.SingleAxis? = null

        override fun toString(): String {
            return super.toString() + " '" + mContentView.text + "'"
        }
    }
}
