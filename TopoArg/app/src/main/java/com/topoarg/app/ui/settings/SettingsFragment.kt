package com.topoarg.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.topoarg.app.crs.CrsCatalog
import com.topoarg.app.databinding.FragmentSettingsBinding
import com.topoarg.app.settings.Prefs

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val items = CrsCatalog.all
        val labels = items.map { "${it.group}  →  ${it.name}" }
        binding.spinnerCrs.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, labels
        )
        binding.spinnerCrs.setSelection(items.indexOfFirst { it.epsg == Prefs.crs.epsg }.coerceAtLeast(0))
        binding.spinnerCrs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.crs = items[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.switchAutoFaja.isChecked = Prefs.autoFaja
        binding.switchAutoFaja.setOnCheckedChangeListener { _, checked ->
            Prefs.autoFaja = checked
        }

        binding.etSamples.setText(Prefs.averagingSamples.toString())
        binding.etSamples.doAfterTextChanged { text ->
            text?.toString()?.toIntOrNull()?.let { Prefs.averagingSamples = it }
        }

        binding.etMaxAccuracy.setText(Prefs.maxAccuracy.toString())
        binding.etMaxAccuracy.doAfterTextChanged { text ->
            text?.toString()?.toFloatOrNull()?.let { Prefs.maxAccuracy = it }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
