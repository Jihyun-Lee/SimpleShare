package com.theone.simpleshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.theone.simpleshare.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private val TAG = "MainActivity"
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(getLayoutInflater())
        setContentView(binding!!.root)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration: AppBarConfiguration =
            AppBarConfiguration.Builder( //R.id.navigation_pairing, R.id.navigation_paired, R.id.navigation_coc_server, R.id.navigation_coc_client)
                R.id.navigation_pairing, R.id.navigation_paired
            )
                .build()
        val navController: NavController =
            Navigation.findNavController(this, R.id.nav_host_fragment_activity_main)
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(binding!!.navView, navController)
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                !== PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "ACCESS_FINE_LOCATION is not granted")
                ActivityCompat.requestPermissions(
                    this,
                    perms,
                    CTS_VERIFIER_PERMISSION_REQUEST
                )
            }
        }

        // getSupportActionBar().hide();
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CTS_VERIFIER_PERMISSION_REQUEST) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "Permission granted.")
                return
            } else {
                Log.v(TAG, "Permission not granted.")
                Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val CTS_VERIFIER_PERMISSION_REQUEST = 1
    }
}