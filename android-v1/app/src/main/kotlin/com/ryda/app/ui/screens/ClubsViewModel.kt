package com.ryda.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryda.app.core.models.Club
import com.ryda.app.data.repository.RydaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ClubsViewModel(private val repository: RydaRepository) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var city by mutableStateOf("Mumbai")
        private set
    var description by mutableStateOf("")
        private set
    var memberLimit by mutableStateOf("50")
        private set
    var policy by mutableStateOf("OPEN")
        private set

    var validationError by mutableStateOf<String?>(null)
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var isCheckingEligibility by mutableStateOf(false)
        private set

    private val _clubCreatedEvents = MutableSharedFlow<Club>()
    val clubCreatedEvents: SharedFlow<Club> = _clubCreatedEvents.asSharedFlow()

    fun checkEligibility(
        onEligible: () -> Unit,
        onIneligible: (com.ryda.app.core.network.ClubEligibilityResponse) -> Unit,
        onError: () -> Unit
    ) {
        isCheckingEligibility = true
        viewModelScope.launch {
            try {
                val eligibility = repository.getClubEligibility()
                isCheckingEligibility = false
                if (eligibility != null) {
                    if (eligibility.eligible) {
                        onEligible()
                    } else {
                        onIneligible(eligibility)
                    }
                } else {
                    onError()
                }
            } catch (e: CancellationException) {
                isCheckingEligibility = false
                throw e
            } catch (_: Exception) {
                isCheckingEligibility = false
                onError()
            }
        }
    }

    fun updateName(value: String) {
        name = value
        if (validationError != null) validationError = null
    }

    fun updateCity(value: String) {
        city = value
        if (validationError != null) validationError = null
    }

    fun updateDescription(value: String) {
        description = value
        if (validationError != null) validationError = null
    }

    fun updateMemberLimit(value: String) {
        memberLimit = value
        if (validationError != null) validationError = null
    }

    fun updatePolicy(value: String) {
        policy = value
    }

    fun createClub() {
        val limitInt = memberLimit.toIntOrNull()
        when {
            name.isBlank() -> {
                validationError = "Club name is required."
            }
            city.isBlank() -> {
                validationError = "City / Region is required."
            }
            description.isBlank() -> {
                validationError = "Description is required."
            }
            limitInt == null || limitInt <= 0 || limitInt > 100 -> {
                validationError = "Member limit must be a positive number up to 100."
            }
            else -> {
                validationError = null
                isSubmitting = true
                viewModelScope.launch {
                    try {
                        val newClub = repository.createClub(
                            name = name.trim(),
                            city = city.trim(),
                            description = description.trim(),
                            policy = policy
                        )
                        isSubmitting = false
                        if (newClub != null) {
                            _clubCreatedEvents.emit(newClub)
                        } else {
                            validationError = "Failed to create club. Please check network and try again."
                        }
                    } catch (e: CancellationException) {
                        isSubmitting = false
                        throw e
                    } catch (e: Exception) {
                        isSubmitting = false
                        validationError = "Error: ${e.localizedMessage ?: "Unknown error"}"
                    }
                }
            }
        }
    }
}
