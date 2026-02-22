package com.adsamcik.temperaturedashboard.ui.state

import com.adsamcik.temperaturedashboard.data.ViewDevice

sealed interface MainScreenState {
    data object Loading : MainScreenState
    data class Success(val devices: List<ViewDevice>) : MainScreenState
    data object Empty : MainScreenState
    data class Error(val message: String) : MainScreenState
}
