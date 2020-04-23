package org.nypl.simplified.cardcreator.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.location.Geocoder
import android.location.Address
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import org.nypl.simplified.cardcreator.R
import org.nypl.simplified.cardcreator.databinding.FragmentLocationBinding
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * The Location screens that gates users who are not in New York
 */
class LocationFragment : Fragment(), LocationListener {

  private val logger = LoggerFactory.getLogger(LocationFragment::class.java)

  private var _binding: FragmentLocationBinding? = null
  private val binding get() = _binding!!

  private lateinit var navController: NavController
  private lateinit var nextAction: NavDirections
  private var isNewYork = false

  private val locationRequestCode = 102

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    _binding = FragmentLocationBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    navController = Navigation.findNavController(requireActivity(), R.id.card_creator_nav_host_fragment)
    checkIfInNewYorkState()

    binding.checkLocationBtn.setOnClickListener {
      checkIfInNewYorkState()
    }

    // Go to next screen
    binding.nextBtn.setOnClickListener {
      if (isNewYork) {
        logger.debug("User navigated to the next screen")
        nextAction = LocationFragmentDirections.actionNext()
        navController.navigate(nextAction)
      } else {
        val data = Intent()
        requireActivity().setResult(Activity.RESULT_CANCELED, data)
        requireActivity().finish()
      }
    }

    // Go to previous screen
    binding.prevBtn.setOnClickListener {
      requireActivity().onBackPressed()
    }
  }

  /**
   * Show settings prompt for location
   */
  private fun showLocationSettingsPrompt() {
    val dialogBuilder = AlertDialog.Builder(requireContext())
    dialogBuilder.setMessage(getString(R.string.location_access_error))
      .setCancelable(false)
      .setPositiveButton(getString(R.string.settings)) { _, _ ->
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
      }
      .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
        dialog.cancel()
      }
    val alert = dialogBuilder.create()
    alert.show()
  }

  /**
   * User denied location prompt, show this to ask again
   */
  private fun showLocationPrompt() {
    logger.debug("Showing location prompt...")
    val dialogBuilder = AlertDialog.Builder(requireContext())
    dialogBuilder.setMessage(getString(R.string.location_permission_prompt))
      .setCancelable(false)
      .setPositiveButton(getString(R.string.try_again)) { _, _ ->
        checkIfInNewYorkState()
      }
      .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
        dialog.cancel()
      }
    val alert = dialogBuilder.create()
    alert.show()
  }

  /**
   * Checks to see if the user has granted required location permissions
   */
  private fun getLocation(): Location? {
    var location: Location? = null
    logger.debug("Checking for location permission...")
    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
      != PackageManager.PERMISSION_GRANTED
    ) {
      logger.debug("Requesting location permission...")
      requestPermissions(
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
        locationRequestCode
      )
    } else {
      logger.debug("Location permission granted")
      try {
        logger.debug("Getting current location")
        val locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isNetworkLocationEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isNetworkLocationEnabled) {
          val looper = Looper.myLooper()
          locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, looper)
          location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
      } catch (e: Exception) {
        logger.error("Error getting current location", e)
        showLocationSettingsPrompt()
      }
    }
    return location
  }

  /**
   * Checks to see if a give location is in the state of New York
   */
  // TODO: This function is doing to much, break it up into smaller pieces
  private fun checkIfInNewYorkState() {
    logger.debug("Checking to see if user is in New York")
    val locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    binding.nextBtn.isEnabled = false
    val maxResults = 1
    val location = getLocation()
    val geocoder = Geocoder(requireContext(), Locale.getDefault())

    // Address found using the Geocoder.
    val address: Address?

    try {
      if (location != null) {
        address = geocoder.getFromLocation(location.latitude, location.longitude, maxResults)[0]
        logger.debug("Region is: ${address.adminArea} ${address.countryCode} ")
        binding.regionEt.setText("${address.adminArea} ${address.countryCode}", TextView.BufferType.EDITABLE)

        if (address.countryCode == "US" && (address.adminArea == "New York" || address.adminArea == "NY")) {
          logger.debug("User is in New York")
          enableNext(true)
        } else {
          enableNext(false)
        }
      }
    } catch (e: Exception) {
      logger.error("Error checking to see if user is in New York", e)
      enableNext(false)
      showLocationSettingsPrompt()
    }

    logger.debug("Stopping location updates")
    locationManager.removeUpdates(this)
  }

  /**
   * Listen for result from location permission request, this method is a callback provided by
   * Android for the requestPermissions() method
   *
   * @param requestCode - String user defined request code to identify the request
   * @param permissions - String Array of permissions requested
   * @param grantResults - Integer array of what the user has granted/denied
   */
  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    when (requestCode) {
      locationRequestCode -> {
        // If request is cancelled, the result arrays are empty.
        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
          logger.debug("Location permission granted")
          checkIfInNewYorkState()
        } else {
          logger.debug("Location permission NOT granted")
          showLocationPrompt()
        }
        return
      }
    }
  }

  override fun onLocationChanged(location: Location?) {
    logger.debug("Location has changed")
    val locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (location != null) {
      logger.debug("Checking to see if user is in New York")
      binding.nextBtn.isEnabled = false
      val maxResults = 1
      val geocoder = Geocoder(requireActivity(), Locale.getDefault())

      // Address found using the Geocoder.
      val address: Address?

      try {
          address = geocoder.getFromLocation(location.latitude, location.longitude, maxResults)[0]
          logger.debug("Region is: ${address.adminArea} ${address.countryCode} ")
          binding.regionEt.setText("${address.adminArea} ${address.countryCode}", TextView.BufferType.EDITABLE)

          if (address.countryCode == "US" && (address.adminArea == "New York" || address.adminArea == "NY")) {
            logger.debug("User is in New York")
            enableNext(true)
          }
      } catch (e: Exception) {
        logger.error("Error checking to see if user is in New York", e)
        enableNext(false)
        showLocationSettingsPrompt()
      }
    }
    logger.debug("Stopping location updates")
    locationManager.removeUpdates(this)
  }

  /**
   * Enables the next button and update header status
   */
  fun enableNext(enable: Boolean) {
    isNewYork = enable
    binding.nextBtn.isEnabled = enable
    if (enable) {
      binding.nextBtn.text = getString(R.string.next)
      binding.headerStatusDescTv.text = getString(R.string.new_york_success)
      binding.headerStatusDescTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.trans_black))
    } else {
      logger.debug("User is NOT in New York")
      binding.nextBtn.text = getString(R.string.done)
      binding.headerStatusDescTv.text = getString(R.string.location_error)
      binding.headerStatusDescTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
    }
  }

  // These are not needed but were invited to the party by Google
  override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { logger.debug("location status changed") }
  override fun onProviderEnabled(provider: String?) { logger.debug("location provider enabled") }
  override fun onProviderDisabled(provider: String?) { logger.debug("location provider disabled") }
}
