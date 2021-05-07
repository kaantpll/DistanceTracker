package com.example.distancetrackerapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.media.audiofx.BassBoost
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.distancetrackerapp.databinding.FragmentMapsBinding
import com.example.distancetrackerapp.model.Result
import com.example.distancetrackerapp.service.TrackerService
import com.example.distancetrackerapp.util.Constants.ACTION_SERVICE_START
import com.example.distancetrackerapp.util.Constants.ACTION_SERVICE_STOP
import com.example.distancetrackerapp.util.ExtensionFunction.disable
import com.example.distancetrackerapp.util.ExtensionFunction.enable
import com.example.distancetrackerapp.util.ExtensionFunction.hide
import com.example.distancetrackerapp.util.ExtensionFunction.show
import com.example.distancetrackerapp.util.MapUtil
import com.example.distancetrackerapp.util.MapUtil.calculateElapsedTime
import com.example.distancetrackerapp.util.MapUtil.calculateTheDistance
import com.example.distancetrackerapp.util.MapUtil.setCameraPosition
import com.example.distancetrackerapp.util.Permissions.hasBackgroundLocationPermission
import com.example.distancetrackerapp.util.Permissions.requestBackgroundLocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*

import com.google.android.gms.maps.model.*
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MapsFragment : Fragment(),OnMapReadyCallback , GoogleMap.OnMarkerClickListener,GoogleMap.OnMyLocationButtonClickListener,EasyPermissions.PermissionCallbacks {

    private var _binding : FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private var startTime = 0L
    private var stopTime = 0L
    private lateinit var map : GoogleMap
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient

    private var markerList = mutableListOf<Marker>()

    private var polylineList = mutableListOf<Polyline>()

    val started = MutableLiveData(false)
    private var locationList = mutableListOf<LatLng>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
       _binding = FragmentMapsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.tracking = this
        binding.startButton.setOnClickListener {
            onStartButtonClicked()
        }
        binding.stopButton.setOnClickListener {
            onStopButtonClicked()
        }
        binding.resetButton.setOnClickListener {
            onResetButton()
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        return binding.root
    }



    private fun onStopButtonClicked() {
        stopForegroundService()
        binding.stopButton.hide()
        binding.startButton.show()
    }

    private fun onResetButton() {
        mapReset()
    }

    @SuppressLint("MissingPermission")
    private fun mapReset() {
    fusedLocationProviderClient.lastLocation.addOnCompleteListener {
        val lastKnowLocation = LatLng(
            it.result.latitude,
            it.result.longitude,
        )
        for(polyline in polylineList){
            polyline.remove()
        }
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                setCameraPosition(lastKnowLocation)
            )
        )
        locationList.clear()
        for(marker in markerList) {
            marker.remove()
        }
        binding.resetButton.hide()
        binding.startButton.show()
    }
    }

    private fun onStartButtonClicked() {
        if(hasBackgroundLocationPermission(requireContext())){
            startCountDown()
            binding.startButton.disable()
            binding.startButton.hide()
            binding.stopButton.show()

        }else{
            requestBackgroundLocationPermission(this)
        }
    }

    private fun startCountDown() {
        binding.timerTextView.show()
        binding.stopButton.disable()
        val timer : CountDownTimer = object : CountDownTimer(4000,1000){
            override fun onTick(millisUntilFinished: Long) {
                val currentSecond = millisUntilFinished / 1000
                if(currentSecond.toString() == "0"){
                    binding.timerTextView.text = "GO"
                    binding.timerTextView.setTextColor(ContextCompat.getColor(requireContext(),R.color.black))
                }
                else{
                    binding.timerTextView.text = currentSecond.toString()
                    binding.timerTextView.setTextColor(ContextCompat.getColor(requireContext(),R.color.red))
                }
            }

            override fun onFinish() {
                binding.timerTextView.hide()
                sendActionCommandToService(ACTION_SERVICE_START)
            }

        }
        timer.start()
    }

    private fun stopForegroundService() {
        sendActionCommandToService(ACTION_SERVICE_STOP)
    }



    private fun sendActionCommandToService(action:String){
        Intent(
                requireContext(),
                TrackerService::class.java
        ).apply {
            this.action = action
            requireContext().startService(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap?) {
        map = googleMap!!
        map.isMyLocationEnabled = true
        map.setOnMyLocationButtonClickListener(this)
        map.setOnMarkerClickListener(this)
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isScrollGesturesEnabled = false

        }

        observeTrackerService()

    }

    private fun observeTrackerService(){
        TrackerService.locationList.observe(viewLifecycleOwner,{
            if(it!= null){
                locationList = it
                if(locationList.size >1){
                    binding.stopButton.enable()
                }
               drawPolyline()
                followPolyLine()
            }
        })
        TrackerService.started.observe(viewLifecycleOwner,{
            started.value = it
        })

        TrackerService.startTime.observe(viewLifecycleOwner,{
            startTime = it
        })
        TrackerService.stopTime.observe(viewLifecycleOwner,{
            stopTime = it
            if(stopTime != 0L){
                showBiggerPicture()
                displayResults()
            }
        })

    }

    private fun showBiggerPicture() {
        val bounds = LatLngBounds.Builder()
        for(location in locationList){
            bounds.include(location)
        }
        map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                        bounds.build(),100
                ),2000,null
        )
        addMarker(locationList.first())
        addMarker(locationList.last())
    }

    private fun addMarker(position : LatLng){
        val marker = map.addMarker(MarkerOptions().position(position))
        markerList.add(marker)

    }

    private fun drawPolyline(){
        val polyLine = map.addPolyline(
                PolylineOptions().apply {
                    width(12f)
                    color(Color.BLUE)
                    jointType(JointType.ROUND)
                    startCap(ButtCap())
                    endCap(ButtCap())
                    addAll(locationList)
                }
        )
        polylineList.add(polyLine)
    }

    private fun followPolyLine(){
        if(locationList.isNotEmpty()){
           map.animateCamera((CameraUpdateFactory.newCameraPosition(
                   MapUtil.setCameraPosition(
                           locationList.last()))),1000,null)
        }
    }
    private fun displayResults(){
        val result = Result(
            calculateTheDistance(locationList),
            calculateElapsedTime(startTime,stopTime)
        )
        lifecycleScope.launch {
            delay(2500)
            val directions= MapsFragmentDirections.actionMapsFragmentToResultFragment(result)
            findNavController().navigate(directions)
            binding.startButton.apply {
                hide()
                enable()
            }
            binding.stopButton.hide()
            binding.resetButton.show()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if(EasyPermissions.somePermissionPermanentlyDenied(this,perms[0])){
            SettingsDialog.Builder(requireActivity()).build().show()
        }
        else{
            requestBackgroundLocationPermission(this)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
       onStartButtonClicked()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMyLocationButtonClick(): Boolean {

        binding.hintTextView.animate().alpha(0f).duration = 1500
        lifecycleScope.launch {
            delay(2000)
            binding.hintTextView.hide()
            binding.startButton.show()
        }
        return false
    }

    override fun onMarkerClick(p0: Marker?): Boolean {
        return true
    }


}