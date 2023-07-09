

import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


class MapHelper {
    private val directionsApiKey = "YOUR_API_KEY"

    suspend fun getRouteCoordinates(context: Context, origin: LatLng, destination: LatLng): List<LatLng> {
        return withContext(Dispatchers.IO) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            try {
                val locationResult = fusedLocationClient.lastLocation.await()

                val origin = LatLng(locationResult.latitude, locationResult.longitude)

                val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&key=$directionsApiKey"

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                val jsonData = response.body?.string()
                response.body?.close()

                val routeCoordinates = mutableListOf<LatLng>()

                if (jsonData != null) {
                    val jsonObject = JSONObject(jsonData)
                    val routesArray = jsonObject.getJSONArray("routes")

                    if (routesArray.length() > 0) {
                        val routeObject = routesArray.getJSONObject(0)
                        val legsArray = routeObject.getJSONArray("legs")

                        if (legsArray.length() > 0) {
                            val legObject = legsArray.getJSONObject(0)
                            val stepsArray = legObject.getJSONArray("steps")

                            for (i in 0 until stepsArray.length()) {
                                val stepObject = stepsArray.getJSONObject(i)
                                val polylineObject = stepObject.getJSONObject("polyline")
                                val points = polylineObject.getString("points")

                                val decodedPoints = decodePolyline(points)

                                for (point in decodedPoints) {
                                    val lat = point[0]
                                    val lng = point[1]
                                    routeCoordinates.add(LatLng(lat, lng))
                                }
                            }
                        }
                    }
                }

                routeCoordinates
            } catch (exception: SecurityException) {
                // Обработка ошибки отсутствия разрешения на доступ к местоположению
                emptyList<LatLng>()
            } catch (exception: Exception) {
                // Обработка других ошибок
                emptyList<LatLng>()
            }
        }
    }


    fun getPolylineOptions(routeCoordinates: List<LatLng>): PolylineOptions {
        val polylineOptions = PolylineOptions()

        for (latLng in routeCoordinates) {
            polylineOptions.add(latLng)
        }

        return polylineOptions
    }

    private fun decodePolyline(encodedPolyline: String): List<DoubleArray> {
        val poly = mutableListOf<DoubleArray>()
        var index = 0
        val len = encodedPolyline.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0

            do {
                b = encodedPolyline[index++].toInt() - 63
                result = result or (b and 0x1F shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0

            do {
                b = encodedPolyline[index++].toInt() - 63
                result = result or (b and 0x1F shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = doubleArrayOf(lat / 1E5, lng / 1E5)
            poly.add(latLng)
        }

        return poly
    }
}


