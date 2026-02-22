package com.adsamcik.temperaturedashboard.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SnackbarManager {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun showMessage(message: String) {
        _messages.tryEmit(message)
    }
}
