package com.stickertransfer.app.ui.viewmodels

data class SnackbarMessage(
    val text: String,
    val id: Long = System.currentTimeMillis()
)
