package com.example.android.humanfollowingrobot

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import app.akexorcist.bluetotohspp.library.BluetoothSPP
import app.akexorcist.bluetotohspp.library.BluetoothState
import kotlinx.android.synthetic.main.activity_main.*
import android.location.LocationManager
import android.provider.Settings
import android.support.multidex.MultiDex
import android.support.v4.app.ActivityCompat
import android.widget.Toast
import app.akexorcist.bluetotohspp.library.DeviceList
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity(), LocationListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {
    private lateinit var bluetooth: BluetoothSPP
    private lateinit var locationManager: LocationManager
    private lateinit var googleApiClient: GoogleApiClient
    private lateinit var currentLocation: Location
    private var locationRequest: LocationRequest? = null
    private val UPDATE_INTERVAL = (1000).toLong()
    private val FASTEST_INTERVAL: Long = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // LOCATION
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        MultiDex.install(this)

        googleApiClient = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build()

        locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        checkLocation()

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // BLUETOOTH
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        bluetooth = BluetoothSPP(this)

        if (!bluetooth.isBluetoothAvailable()) {
            Toast.makeText(applicationContext, "Bluetooth is not available", Toast.LENGTH_SHORT).show()
            finish()
        }

        bluetooth.setBluetoothConnectionListener(object : BluetoothSPP.BluetoothConnectionListener {
            override fun onDeviceConnected(name: String, address: String) {
                btn_connect.text = "Connected to $name"
            }

            override fun onDeviceDisconnected() {
                btn_connect.text = "Connection lost"
            }

            override fun onDeviceConnectionFailed() {
                btn_connect.text = "Unable to connect"
            }
        })

        btn_connect.setOnClickListener {
            if (bluetooth.getServiceState() == BluetoothState.STATE_CONNECTED)
                bluetooth.disconnect()
            else {
                val intent = Intent(applicationContext, DeviceList::class.java)
                startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE)
            }
        }

        btn_start.setOnClickListener {
            bluetooth.send("Start", true)
        }

        btn_stop.setOnClickListener {
            bluetooth.send("Stop", true)
        }
    }

    override fun onStart() {
        super.onStart()

        googleApiClient.connect()

        if (!bluetooth.isBluetoothEnabled)
            bluetooth.enable()
        else {
            if (!bluetooth.isServiceAvailable) {
                bluetooth.setupService()
                bluetooth.startService(BluetoothState.DEVICE_OTHER)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (googleApiClient.isConnected)
            googleApiClient.disconnect()

        bluetooth.stopService()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if (resultCode == Activity.RESULT_OK)
                bluetooth.connect(data)
        }
        else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK)
                bluetooth.setupService()
            else {
                println("Bluetooth was not enabled")
                finish()
            }
        }
    }

    //==================================================================================
    // IMPLEMENT LOCATIONLISTENER
    //==================================================================================

    override fun onLocationChanged(location: Location?) {
        updateLatLong(location)
        println("*************************************************")
        println(location?.latitude)
        println(location?.longitude)
    }

    override fun onProviderDisabled(provider: String?) {
        Toast.makeText(this, "Please enable GPS and Internet", Toast.LENGTH_SHORT).show()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String?) {}

    override fun onConnected(p0: Bundle?) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return

        startLocationUpdates()

        var fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationProviderClient.lastLocation
                .addOnSuccessListener(this, { location ->
                    if (location != null) {
                        currentLocation = location
                        updateLatLong(currentLocation)
                    }
                })
    }

    override fun onConnectionSuspended(p0: Int) {
        println("Connection Suspended")
        googleApiClient.connect()
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        println("Connection failed. Error: " + p0.errorCode)
    }

    //==================================================================================
    // HELPER METHODS
    //==================================================================================

    private fun checkLocation(): Boolean {
        if (!isLocationEnabled())
            showAlert()
        return isLocationEnabled()
    }

    private fun isLocationEnabled(): Boolean {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showAlert() {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Enable Location")
                .setMessage("Please enable Location")
                .setPositiveButton("Location Settings", { paramDialogInterface, paramInt ->
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                })
                .setNegativeButton("Cancel", { paramDialogInterface, paramInt -> })
        dialog.show()
    }

    private fun updateLatLong(location: Location?) {
        tv_latitude.text = location?.latitude.toString()
        tv_longitude.text = location?.longitude.toString()
    }

    private fun startLocationUpdates() {
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this)
    }
}