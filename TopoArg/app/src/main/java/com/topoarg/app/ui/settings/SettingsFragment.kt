package com.topoarg.app.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.topoarg.app.R
import com.topoarg.app.crs.CrsCatalog
import com.topoarg.app.databinding.FragmentSettingsBinding
import com.topoarg.app.settings.Prefs

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val btPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showDevicePicker()
        } else {
            Toast.makeText(requireContext(), R.string.bt_permission_needed, Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fuente GNSS
        binding.radioInternal.isChecked = Prefs.gnssSource == Prefs.SOURCE_INTERNAL
        binding.radioBluetooth.isChecked = Prefs.gnssSource == Prefs.SOURCE_BLUETOOTH
        binding.radioSource.setOnCheckedChangeListener { _, checkedId ->
            Prefs.gnssSource = if (checkedId == R.id.radioBluetooth) {
                Prefs.SOURCE_BLUETOOTH
            } else {
                Prefs.SOURCE_INTERNAL
            }
        }
        binding.btnPickDevice.setOnClickListener { pickDeviceWithPermission() }
        renderBtDevice()

        // Sistema de coordenadas
        val items = CrsCatalog.all
        val labels = items.map { "${it.group}  →  ${it.name}" }
        binding.spinnerCrs.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, labels
        )
        binding.spinnerCrs.setSelection(
            items.indexOfFirst { it.epsg == Prefs.crs.epsg }.coerceAtLeast(0)
        )
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

    private fun renderBtDevice() {
        binding.tvBtDevice.text = if (Prefs.btDeviceAddress.isEmpty()) {
            getString(R.string.bt_none_selected)
        } else {
            getString(
                R.string.bt_selected,
                Prefs.btDeviceName.ifBlank { Prefs.btDeviceAddress }
            )
        }
    }

    private fun pickDeviceWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        showDevicePicker()
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePicker() {
        val manager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter == null) {
            Toast.makeText(requireContext(), R.string.bt_no_adapter, Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(requireContext(), R.string.bt_disabled, Toast.LENGTH_LONG).show()
            return
        }
        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            Toast.makeText(requireContext(), R.string.bt_no_paired, Toast.LENGTH_LONG).show()
            return
        }
        val names = devices.map { "${it.name ?: "(sin nombre)"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pick_bt_device)
            .setItems(names) { _, which ->
                val device = devices[which]
                Prefs.btDeviceAddress = device.address
                Prefs.btDeviceName = device.name ?: device.address
                Prefs.gnssSource = Prefs.SOURCE_BLUETOOTH
                binding.radioBluetooth.isChecked = true
                renderBtDevice()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
