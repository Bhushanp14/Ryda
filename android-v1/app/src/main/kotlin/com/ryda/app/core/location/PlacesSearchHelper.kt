package com.ryda.app.core.location

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

data class ResolvedPlace(
    val name: String,
    val lat: Double,
    val lng: Double
)

object PlacesSearchHelper {

    private fun getClient(context: Context): PlacesClient? {
        val appContext = context.applicationContext
        if (!Places.isInitialized()) {
            val apiKey = try {
                val appInfo = appContext.packageManager.getApplicationInfo(
                    appContext.packageName,
                    PackageManager.GET_META_DATA
                )
                appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
            } catch (_: Exception) {
                ""
            }
            if (apiKey.isBlank()) return null
            Places.initializeWithNewPlacesApiEnabled(appContext, apiKey)
        }
        return Places.createClient(appContext)
    }

    suspend fun searchPlaces(context: Context, query: String): Result<List<PlaceSuggestion>> {
        if (query.isBlank()) return Result.success(emptyList())
        val client = getClient(context)
            ?: return Result.failure(IllegalStateException("Google Places API key is not configured."))

        return suspendCancellableCoroutine { cont ->
            val token = AutocompleteSessionToken.newInstance()
            val req = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(token)
                .setQuery(query)
                .build()

            client.findAutocompletePredictions(req)
                .addOnSuccessListener { resp ->
                    if (cont.isActive) {
                        val list = resp.autocompletePredictions.map { pred ->
                            PlaceSuggestion(
                                placeId = pred.placeId,
                                primaryText = pred.getPrimaryText(null).toString(),
                                secondaryText = pred.getSecondaryText(null).toString()
                            )
                        }
                        cont.resume(Result.success(list))
                    }
                }
                .addOnFailureListener { err ->
                    if (cont.isActive) {
                        cont.resume(Result.failure(RuntimeException(err.message ?: "Places search failed.")))
                    }
                }
        }
    }

    suspend fun fetchPlaceCoordinates(context: Context, placeId: String, fallbackName: String): Result<ResolvedPlace> {
        val client = getClient(context)
            ?: return Result.failure(IllegalStateException("Google Places API key is not configured."))

        return suspendCancellableCoroutine { cont ->
            val fields = listOf(Place.Field.DISPLAY_NAME, Place.Field.LOCATION)
            val req = FetchPlaceRequest.newInstance(placeId, fields)
            client.fetchPlace(req)
                .addOnSuccessListener { resp ->
                    val loc = resp.place.location
                    if (loc == null) {
                        if (cont.isActive) {
                            cont.resume(Result.failure(RuntimeException("Selected place has no coordinates.")))
                        }
                        return@addOnSuccessListener
                    }
                    val displayName = resp.place.displayName ?: fallbackName
                    if (cont.isActive) {
                        cont.resume(Result.success(ResolvedPlace(displayName, loc.latitude, loc.longitude)))
                    }
                }
                .addOnFailureListener { err ->
                    if (cont.isActive) {
                        cont.resume(Result.failure(RuntimeException(err.message ?: "Failed to load place details.")))
                    }
                }
        }
    }
}
