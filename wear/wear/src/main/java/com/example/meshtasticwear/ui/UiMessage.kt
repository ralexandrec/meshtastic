package com.example.meshtasticwear.ui

data class UiMessage(
    val sender: String,
    val text: String,
    val fullDisplay: String,
    val isGps: Boolean = false,
    val latitude: String? = null,
    val longitude: String? = null
)
