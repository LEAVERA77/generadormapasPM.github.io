package com.topoarg.app.ui.points

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.topoarg.app.R
import com.topoarg.app.crs.CoordinateConverter
import com.topoarg.app.data.PointRepository
import com.topoarg.app.data.SurveyPoint
import com.topoarg.app.databinding.DialogSavePointBinding
import com.topoarg.app.databinding.FragmentPointsBinding
import com.topoarg.app.util.Format

class PointsFragment : Fragment() {

    private var _binding: FragmentPointsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PointsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPointsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PointsAdapter(
            onClick = { showDetail(it) },
            onLongClick = { showActions(it) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        PointRepository.points.observe(viewLifecycleOwner) { points ->
            adapter.submit(points)
            binding.tvEmpty.isVisible = points.isEmpty()
            binding.tvCount.text = getString(R.string.points_count, points.size)
        }
    }

    /** Detalle: el punto convertido a TODOS los sistemas argentinos. */
    private fun showDetail(p: SurveyPoint) {
        val sb = StringBuilder()
        sb.append(getString(R.string.detail_header)).append("\n\n")
        sb.append("WGS84: ${Format.dms(p.lat, true)}  ${Format.dms(p.lon, false)}\n")
        sb.append("h elipsoidal: ${Format.m(p.altitude)}\n")
        sb.append("Precisión: ${Format.acc(p.accuracy)} (V: ${Format.acc(p.verticalAccuracy)})\n")
        sb.append("Muestras: ${p.samples}  σ: ${Format.m(p.stdHorizontal)}\n")
        sb.append("Fecha: ${Format.dateTime(p.timestamp)}\n")
        if (p.description.isNotBlank()) sb.append("Obs: ${p.description}\n")
        sb.append("\n──────────────────────\n\n")

        var lastGroup = ""
        for (c in CoordinateConverter.toAllSystems(p.lat, p.lon)) {
            if (c.crs.group != lastGroup) {
                lastGroup = c.crs.group
                sb.append("▌ ").append(lastGroup).append('\n')
            }
            sb.append("  ").append(c.crs.name)
                .append(" (EPSG:").append(c.crs.epsg).append(")\n")
            if (c.crs.projected) {
                sb.append("    X (Norte): ").append(Format.m(c.north)).append('\n')
                sb.append("    Y (Este):  ").append(Format.m(c.east)).append('\n')
            } else {
                sb.append("    Lat: ").append(Format.deg(c.north)).append('\n')
                sb.append("    Lon: ").append(Format.deg(c.east)).append('\n')
            }
        }

        val tv = TextView(requireContext()).apply {
            text = sb.toString()
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }

        AlertDialog.Builder(requireContext())
            .setTitle(p.name)
            .setView(scroll)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showActions(p: SurveyPoint) {
        val options = arrayOf(getString(R.string.edit), getString(R.string.delete))
        AlertDialog.Builder(requireContext())
            .setTitle(p.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEdit(p)
                    1 -> confirmDelete(p)
                }
            }
            .show()
    }

    private fun showEdit(p: SurveyPoint) {
        val dialogBinding = DialogSavePointBinding.inflate(layoutInflater)
        dialogBinding.etName.setText(p.name)
        dialogBinding.etDescription.setText(p.description)
        dialogBinding.tvSummary.isVisible = false
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                PointRepository.update(
                    p.id,
                    dialogBinding.etName.text.toString().ifBlank { p.name },
                    dialogBinding.etDescription.text.toString().trim()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(p: SurveyPoint) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_confirm, p.name))
            .setPositiveButton(R.string.delete) { _, _ -> PointRepository.delete(p.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
