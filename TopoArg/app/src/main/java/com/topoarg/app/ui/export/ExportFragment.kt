package com.topoarg.app.ui.export

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.topoarg.app.R
import com.topoarg.app.crs.CrsCatalog
import com.topoarg.app.data.PointRepository
import com.topoarg.app.databinding.FragmentExportBinding
import com.topoarg.app.export.ExportFormat
import com.topoarg.app.export.Exporters
import com.topoarg.app.settings.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvExportCrs.text = getString(R.string.export_crs_note, Prefs.crs.toString())

        PointRepository.points.observe(viewLifecycleOwner) { points ->
            binding.tvExportCount.text = getString(R.string.points_count, points.size)
            binding.btnExport.isEnabled = points.isNotEmpty()
        }

        binding.btnExport.setOnClickListener { export() }
    }

    private fun selectedFormat(): ExportFormat = when (binding.radioGroup.checkedRadioButtonId) {
        R.id.radioKml -> ExportFormat.KML
        R.id.radioGpx -> ExportFormat.GPX
        R.id.radioGeojson -> ExportFormat.GEOJSON
        R.id.radioDxf -> ExportFormat.DXF
        else -> ExportFormat.CSV
    }

    private fun export() {
        val points = PointRepository.points.value.orEmpty()
        if (points.isEmpty()) return
        val format = selectedFormat()

        // Para CSV/DXF usamos el CRS de trabajo; si la faja es automática,
        // se elige según la longitud del primer punto para todo el archivo.
        val crs = CrsCatalog.effective(Prefs.crs, points.firstOrNull()?.lon, Prefs.autoFaja)

        try {
            val content = Exporters.generate(format, points, crs)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = File(requireContext().cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "topoarg_$stamp.${format.extension}")
            file.writeText(content, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.export_error, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
