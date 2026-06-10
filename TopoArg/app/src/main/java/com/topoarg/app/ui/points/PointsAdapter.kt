package com.topoarg.app.ui.points

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.topoarg.app.R
import com.topoarg.app.crs.CoordinateConverter
import com.topoarg.app.crs.CrsCatalog
import com.topoarg.app.data.SurveyPoint
import com.topoarg.app.databinding.ItemPointBinding
import com.topoarg.app.settings.Prefs
import com.topoarg.app.util.Format

class PointsAdapter(
    private val onClick: (SurveyPoint) -> Unit,
    private val onLongClick: (SurveyPoint) -> Unit
) : RecyclerView.Adapter<PointsAdapter.Holder>() {

    private var items: List<SurveyPoint> = emptyList()

    fun submit(points: List<SurveyPoint>) {
        items = points
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPointBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(private val binding: ItemPointBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(p: SurveyPoint) {
            binding.tvName.text = p.name
            binding.tvDate.text = Format.dateTime(p.timestamp)

            val crs = CrsCatalog.effective(Prefs.crs, p.lon, Prefs.autoFaja)
            val c = CoordinateConverter.fromWgs84(p.lat, p.lon, crs)
            binding.tvCoords.text = if (crs.projected) {
                "X: ${Format.m(c.north)}   Y: ${Format.m(c.east)}"
            } else {
                "${Format.dms(p.lat, true)}  ${Format.dms(p.lon, false)}"
            }
            binding.tvMeta.text = binding.root.context.getString(
                R.string.point_meta,
                Format.acc(p.accuracy), p.samples, crs.name
            )

            binding.root.setOnClickListener { onClick(p) }
            binding.root.setOnLongClickListener { onLongClick(p); true }
        }
    }
}
