package com.example.distancetrackerapp

import android.annotation.SuppressLint
import android.media.audiofx.BassBoost
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.distancetrackerapp.databinding.FragmentMapsBinding
import com.example.distancetrackerapp.util.ExtensionFunction.disable
import com.example.distancetrackerapp.util.ExtensionFunction.hide
import com.example.distancetrackerapp.util.ExtensionFunction.show
import com.example.distancetrackerapp.util.Permissions.hasBackgroundLocationPermission
import com.example.distancetrackerapp.util.Permissions.requestBackgroundLocationPermission

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapsFragment : Fragment(),OnMapReadyCallback ,GoogleMap.OnMyLocationButtonClickListener,EasyPermissions.PermissionCallbacks {

    private var _binding : FragmentMapsBinding? = null
    private val binding get() = _binding!!

    private lateinit var map : GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
       _binding = FragmentMapsBinding.inflate(inflater, container, false)

        binding.startButton.setOnClickListener {
            onStartButtonClicked()
        }
        binding.stopButton.setOnClickListener {  }
        binding.resetButton.setOnClickListener {  }


        return binding.root
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
            }

        }
        timer.start()
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
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isScrollGesturesEnabled = false
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
        TODO("Not yet implemented")
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
            delay(3000)
            binding.hintTextView.hide()
            binding.startButton.show()
        }
        return false
    }


}