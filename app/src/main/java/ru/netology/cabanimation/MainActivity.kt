package ru.netology.cabanimation


import MapHelper
import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var googleMap: GoogleMap
    private lateinit var mapHelper: MapHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var destinationMarker: Marker
    private lateinit var carMarker: Marker
    private val carIcon: BitmapDescriptor by lazy {
        BitmapDescriptorFactory.fromResource(R.drawable.car_icon1)
    }

    private var currentRouteCoordinates: List<LatLng> = emptyList()
    private var carAnimation: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapHelper = MapHelper()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        setupMap()
    }

    private fun setupMap() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_LOCATION
            )
        } else {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val origin = LatLng(location.latitude, location.longitude)
                    drawRoute(origin)
                }
            }
        }

        googleMap.setOnMapClickListener { latLng ->
            updateDestinationMarker(latLng)
            drawRoute(latLng)
        }
    }

    private fun updateDestinationMarker(latLng: LatLng) {
        if (::destinationMarker.isInitialized) {
            destinationMarker.position = latLng
        } else {
            val markerOptions = MarkerOptions().position(latLng).title("Конечная точка")
            destinationMarker = googleMap.addMarker(markerOptions)
        }
    }

    private fun drawRoute(destination: LatLng) {
        GlobalScope.launch(Dispatchers.Main) {
            val origin = getOrigin()

            if (origin != null) {
                val routeCoordinates = mapHelper.getRouteCoordinates(
                    this@MainActivity,
                    origin,
                    destination
                )

                // Clear previous route and markers
                googleMap.clear()

                // Display route on map
                val polylineOptions = mapHelper.getPolylineOptions(routeCoordinates)
                googleMap.addPolyline(polylineOptions)

                // Display car marker at the starting point
                val carMarkerOptions =
                    MarkerOptions().position(origin).title("Автомобиль").icon(carIcon)
                carMarker = googleMap.addMarker(carMarkerOptions)

                // Move camera to the starting point
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, DEFAULT_ZOOM))

                // Start car animation
                animateCarOnRoute(routeCoordinates)
            }
        }
    }

    private suspend fun getOrigin(): LatLng? {
        return withContext(Dispatchers.IO) {
            try {
                val locationResult = fusedLocationClient.lastLocation.await()
                locationResult?.let { LatLng(it.latitude, it.longitude) }
            } catch (exception: SecurityException) {
                null
            }
        }
    }

    private fun animateCarOnRoute(routeCoordinates: List<LatLng>) {
        currentRouteCoordinates = routeCoordinates

        carAnimation?.cancel()

        val duration = calculateAnimationDuration(routeCoordinates)

        carAnimation = ValueAnimator.ofInt(0, routeCoordinates.size - 1).apply {
            setDuration(duration)
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val index = animator.animatedValue as Int
                val newPosition = routeCoordinates[index]
                carMarker.position = newPosition

                // Обновление положения камеры
                googleMap.moveCamera(CameraUpdateFactory.newLatLng(newPosition))
            }
            start()
        }

        // Добавление слушателя окончания анимации
        carAnimation?.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                // Показ всплывающего окна "Вы прибыли"
                showArrivedPopup()
            }

            override fun onAnimationCancel(animation: Animator) {}

            override fun onAnimationRepeat(animation: Animator) {}
        })
    }

    private fun calculateAnimationDuration(routeCoordinates: List<LatLng>): Long {
        val distance = SphericalUtil.computeLength(routeCoordinates)
        val speed = 60.0 // Assume car speed of 60 units per minute
        return (distance / speed * 500).toLong()
    }

    override fun onDestroy() {
        carAnimation?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_LOCATION = 1
        private const val DEFAULT_ZOOM = 15.5f
    }
    private fun showArrivedPopup() {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_arrival, null)
        val popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )

        // Настройка анимации для всплывающего окна
        popupWindow.animationStyle = R.style.PopupAnimation

        // Отображение всплывающего окна в центре экрана
        popupWindow.showAtLocation(
            findViewById(android.R.id.content),
            Gravity.CENTER,
            0,
            0
        )

        // Задержка в 3 секунды перед скрытием всплывающего окна
        Handler().postDelayed({
            popupWindow.dismiss()
        }, 3000)
    }



}
