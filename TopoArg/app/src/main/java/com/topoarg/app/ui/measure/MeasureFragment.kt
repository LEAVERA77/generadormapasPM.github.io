package com.topoarg.app.ui.measure

import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.topoarg.app.R
import com.topoarg.app.crs.CoordinateConverter
import com.topoarg.app.crs.CrsCatalog
import com.topoarg.app.data.PointRepository
import com.topoarg.app.data.SurveyPoint
import com.topoarg.app.databinding.DialogSavePointBinding
import com.topoarg.app.databinding.FragmentMeasureBinding
import com.topoarg.app.settings.Prefs
import com.topoarg.app.ui.MainActivity
import com.topoarg.app.util.Format

class MeasureFragment : Fragment() {

    private var _binding: FragmentMeasureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MeasureViewModel by viewModels()

    private var lastLocation: Location? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeasureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.engine.location.observe(viewLifecycleOwner) { loc ->
            lastLocation = loc
            viewModel.onLocation(loc)
            renderLocation(loc)
        }
        viewModel.engine.satellites.observe(viewLifecycleOwner) { sat ->
            binding.tvSatellites.text =
                getString(R.string.satellites_format, sat.usedInFix, sat.visible)
        }
        viewModel.averaging.observe(viewLifecycleOwner) { state ->
            renderAveraging(state)
        }
        viewModel.finished.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                viewModel.consumeFinished()
                showSaveDialog(
                    lat = result.lat, lon = result.lon, alt = result.alt,
                    accuracy = result.accuracy, vAccuracy = result.verticalAccuracy,
                    samples = result.samples, std = result.stdHorizontal
                )
            }
        }

        binding.btnTakePoint.setOnClickListener {
            val loc = lastLocation
            if (loc == null) {
                Toast.makeText(requireContext(), R.string.no_fix_yet, Toast.LENGTH_SHORT).show()
            } else {
                showSaveDialog(
                    lat = loc.latitude, lon = loc.longitude, alt = loc.altitude,
                    accuracy = loc.accuracy,
                    vAccuracy = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else 0f,
                    samples = 1, std = 0.0
                )
            }
        }

        binding.btnAverage.setOnClickListener {
            if (viewModel.isAveraging) {
                viewModel.cancelAveraging()
            } else {
                viewModel.startAveraging()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val activity = requireActivity() as MainActivity
        if (activity.hasLocationPermission()) {
            viewModel.engine.start()
        } else {
            binding.tvSatellites.text = getString(R.string.permission_needed)
        }
    }

    override fun onStop() {
        viewModel.engine.stop()
        super.onStop()
    }

    private fun renderLocation(loc: Location) {
        binding.tvLatDms.text = Format.dms(loc.latitude, true)
        binding.tvLonDms.text = Format.dms(loc.longitude, false)
        binding.tvLatDec.text = Format.deg(loc.latitude)
        binding.tvLonDec.text = Format.deg(loc.longitude)
        binding.tvAlt.text = Format.m(loc.altitude)
        binding.tvAccuracy.text = getString(
            R.string.accuracy_format,
            Format.acc(loc.accuracy),
            if (loc.hasVerticalAccuracy()) Format.acc(loc.verticalAccuracyMeters) else "—"
        )

        val crs = CrsCatalog.effective(Prefs.crs, loc.longitude, Prefs.autoFaja)
        binding.tvCrsName.text = crs.toString()
        if (crs.projected) {
            val c = CoordinateConverter.fromWgs84(loc.latitude, loc.longitude, crs)
            binding.tvNorth.text = getString(R.string.north_format, Format.m(c.north))
            binding.tvEast.text = getString(R.string.east_format, Format.m(c.east))
        } else {
            val c = CoordinateConverter.fromWgs84(loc.latitude, loc.longitude, crs)
            binding.tvNorth.text = getString(R.string.lat_format, Format.deg(c.north))
            binding.tvEast.text = getString(R.string.lon_format, Format.deg(c.east))
        }
    }

    private fun renderAveraging(state: AveragingState?) {
        val active = state != null
        binding.cardAveraging.isVisible = active
        binding.btnAverage.text = getString(
            if (active) R.string.stop_averaging else R.string.start_averaging
        )
        binding.btnTakePoint.isEnabled = !active
        if (state == null) return

        binding.progressAvg.max = state.target
        binding.progressAvg.progress = state.count
        binding.tvAvgStatus.text =
            getString(R.string.averaging_status, state.count, state.target, state.rejected)
        if (state.count > 0) {
            binding.tvAvgDetail.text = getString(
                R.string.averaging_detail,
                Format.dms(state.meanLat, true),
                Format.dms(state.meanLon, false),
                Format.m(state.stdHorizontal)
            )
        } else {
            binding.tvAvgDetail.text = getString(R.string.averaging_waiting)
        }
    }

    private fun showSaveDialog(
        lat: Double, lon: Double, alt: Double,
        accuracy: Float, vAccuracy: Float, samples: Int, std: Double
    ) {
        val dialogBinding = DialogSavePointBinding.inflate(layoutInflater)
        dialogBinding.etName.setText(PointRepository.nextDefaultName())

        val crs = CrsCatalog.effective(Prefs.crs, lon, Prefs.autoFaja)
        val c = CoordinateConverter.fromWgs84(lat, lon, crs)
        dialogBinding.tvSummary.text = if (crs.projected) {
            getString(
                R.string.save_summary_projected,
                crs.toString(), Format.m(c.north), Format.m(c.east), Format.m(alt), samples
            )
        } else {
            getString(
                R.string.save_summary_geographic,
                crs.toString(), Format.dms(lat, true), Format.dms(lon, false), Format.m(alt), samples
            )
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.save_point_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etName.text.toString().ifBlank {
                    PointRepository.nextDefaultName()
                }
                PointRepository.add(
                    SurveyPoint(
                        name = name,
                        description = dialogBinding.etDescription.text.toString().trim(),
                        lat = lat, lon = lon, altitude = alt,
                        accuracy = accuracy, verticalAccuracy = vAccuracy,
                        samples = samples, stdHorizontal = std,
                        timestamp = System.currentTimeMillis()
                    )
                )
                Toast.makeText(requireContext(), R.string.point_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
