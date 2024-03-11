package com.example.tomtomnav

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tomtom.quantity.Distance
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.RoutePlanningCallback
import com.tomtom.sdk.routing.RoutePlanningResponse
import com.tomtom.sdk.routing.RoutingFailure
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.options.calculation.AlternativeRoutesOptions
import com.tomtom.sdk.routing.options.calculation.CostModel
import com.tomtom.sdk.routing.options.calculation.RouteType
import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.search.Search
import com.tomtom.sdk.search.online.OnlineSearch
import com.tomtom.sdk.search.ui.SearchFragment
import com.tomtom.sdk.search.ui.SearchFragmentListener
import com.tomtom.sdk.search.ui.model.PlaceDetails
import com.tomtom.sdk.search.ui.model.SearchProperties
import com.tomtom.sdk.vehicle.Vehicle


class MainActivity : AppCompatActivity() {
    private lateinit var mapFragment: MapFragment
    private lateinit var searchFragment: SearchFragment
    private lateinit var tomtomMap: TomTomMap
    private lateinit var locationProvider: LocationProvider
    private lateinit var onLocationUpdateListener: OnLocationUpdateListener
    private lateinit var searchApi: Search
    private lateinit var routePlanner: RoutePlanner
    private lateinit var routePlanView: RecyclerView
    private lateinit var routePlanAdaptor: RoutePlanAdaptor
    private lateinit var searchFab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initLocation()
        initMap()
        initSearch()
        initRoutePlanner()
    }

    private fun initLocation() {
        // init location provider
        locationProvider = AndroidLocationProvider(
            context = applicationContext
        )
        onLocationUpdateListener = OnLocationUpdateListener { location ->
            // on receiving the very first location update event, move camera to
            // current location with zoom level of 9 (city view)
            tomtomMap.moveCamera(
                options = CameraOptions(location.position, zoom = 9.0)
            )
            locationProvider.removeOnLocationUpdateListener(onLocationUpdateListener)
        }
    }

    private fun initMap() {
        // init map
        val mapOptions = MapOptions(
            mapKey = BuildConfig.TOMTOM_API_KEY
        )
        mapFragment = MapFragment.newInstance(mapOptions)
        supportFragmentManager.beginTransaction()
            .replace(R.id.map_container, mapFragment)
            .commit()
        mapFragment.getMapAsync { map: TomTomMap ->
            /* Your code goes here */
            tomtomMap = map
            enableLocationService()
        }
    }

    private fun initSearch() {
        // init fab
        searchFab = findViewById(R.id.search_fab)
        searchFab.setOnClickListener { showSearchFragment() }
        // init search api
        searchApi = OnlineSearch.create(this, BuildConfig.TOMTOM_API_KEY)
        // init search fragment
        searchFragment = SearchFragment.newInstance(
            SearchProperties(
                searchApiKey = BuildConfig.TOMTOM_API_KEY
            )
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.search_fragment_container, searchFragment)
            .commitNow()
        searchFragment.setSearchApi(searchApi)
        searchFragment.setFragmentListener(searchFragmentListener)
        hideSearchFragment()
    }

    private fun hideSearchFragment() {
        hideSoftInput()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left)
            .hide(searchFragment)
            .commitNow()
        searchFab.show()
        searchFab.requestFocus()
    }

    private fun showSearchFragment() {
        searchFab.hide()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left)
            .show(searchFragment)
            .commitNow()
        searchFragment.view?.setBackgroundColor(Color.WHITE)
    }

    private fun initRoutePlanner() {
        routePlanView = findViewById(R.id.route_plan_view)
        routePlanView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        routePlanAdaptor = RoutePlanAdaptor(ArrayList()) {route -> startRouting(route)}
        routePlanView.adapter = routePlanAdaptor
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(routePlanView)
        routePlanner = OnlineRoutePlanner.create(applicationContext, BuildConfig.TOMTOM_API_KEY)
    }

    private fun hideSoftInput() {
        val inputMethodManager =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: View(this)
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private val searchFragmentListener = object : SearchFragmentListener {
        override fun onSearchBackButtonClick() {
            hideSearchFragment()
        }

        override fun onSearchResultClick(placeDetails: PlaceDetails) {
            hideSearchFragment()
            routePlanAdaptor.clear()
            val currentLocation = locationProvider.lastKnownLocation
            if (currentLocation != null) {
                val routePlanningOptions = RoutePlanningOptions(
                    itinerary = Itinerary(
                        origin = currentLocation.position,
                        destination = placeDetails.position
                    ),
                    costModel = CostModel(routeType = RouteType.Efficient),
                    vehicle = Vehicle.Car(),
                    alternativeRoutesOptions = AlternativeRoutesOptions(maxAlternatives = 2)
                )
                routePlanner.planRoute(
                    routePlanningOptions,
                    routePlanCallback
                )
            } else {
                tomtomMap.moveCamera(
                    options = CameraOptions(placeDetails.position)
                )
            }

        }

        override fun onSearchError(throwable: Throwable) {}

        override fun onSearchQueryChanged(input: String) {}

        override fun onCommandInsert(command: String) {}
    }

    private fun startRouting(route: Route) {
        searchFab.hide()
        val routeOptions = RouteOptions(
            geometry = route.geometry,
            progress = Distance.meters(1000.0),
            departureMarkerVisible = true,
            destinationMarkerVisible = true
        )
        tomtomMap.addRoute(routeOptions)
        tomtomMap.cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
        tomtomMap.disableLocationMarker()
        routePlanView.visibility = View.INVISIBLE
    }

    private val routePlanCallback = object : RoutePlanningCallback {
        override fun onSuccess(result: RoutePlanningResponse) {}

        override fun onFailure(failure: RoutingFailure) {
            Toast.makeText(applicationContext, R.string.plan_route_error, Toast.LENGTH_LONG)
                .show()
        }

        override fun onRoutePlanned(route: Route) {
            runOnUiThread {
                routePlanAdaptor.addRoute(route)
            }
        }
    }

    private fun showCurrentLocation() {
        locationProvider.addOnLocationUpdateListener(onLocationUpdateListener)
        locationProvider.enable()
        tomtomMap.setLocationProvider(locationProvider)
        val locationMarker = LocationMarkerOptions(
            type = LocationMarkerOptions.Type.Pointer
        )
        tomtomMap.enableLocationMarker(locationMarker)
    }

    private fun enableLocationService() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // permissions granted already
            showCurrentLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
                showCurrentLocation()
            }

            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
                Toast.makeText(this, R.string.precise_location_error, Toast.LENGTH_LONG)
                    .show()
            }

            else -> {
                // No location access granted.
                Toast.makeText(this, R.string.location_permission_error, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}