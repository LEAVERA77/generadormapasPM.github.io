package com.topoarg.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.topoarg.app.R
import com.topoarg.app.databinding.ActivityMainBinding
import com.topoarg.app.ui.export.ExportFragment
import com.topoarg.app.ui.measure.MeasureFragment
import com.topoarg.app.ui.points.PointsFragment
import com.topoarg.app.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
        } else {
            // Reinicia el fragment de medición para que arranque el GNSS.
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current is MeasureFragment) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, MeasureFragment())
                    .commit()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_measure -> MeasureFragment()
                R.id.nav_points -> PointsFragment()
                R.id.nav_export -> ExportFragment()
                else -> SettingsFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_measure
        }
        ensurePermissions()
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
