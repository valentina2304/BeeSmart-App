package com.example.beesmart.utils

import android.os.Build

/**
 * Centralized network configuration.
 * For Azure we use the public API URL, not the laptop's IP address.
 */
object NetworkConfig {

    /**
     * Public backend URL on Azure Container Apps.
     * Must be the URL for beesmart-api, NOT beesmart-ai.
     *
     * Note:
     * - no :8080
     * - must end with /
     */
    private const val AZURE_API_BASE_URL =
        "https://beesmart-api.purpleriver-77715631.francecentral.azurecontainerapps.io/"

    /**
     * Base URL used by Retrofit/OkHttp.
     */
    val baseUrl: String
        get() = AZURE_API_BASE_URL

    /**
     * Logs the current configuration for debugging.
     */
    fun getDebugInfo(): String {
        return """
            Network Configuration:
            - Running on Azure backend
            - Base URL: $baseUrl
            - Build Info:
              * BRAND: ${Build.BRAND}
              * DEVICE: ${Build.DEVICE}
              * FINGERPRINT: ${Build.FINGERPRINT}
              * HARDWARE: ${Build.HARDWARE}
              * MODEL: ${Build.MODEL}
              * MANUFACTURER: ${Build.MANUFACTURER}
              * PRODUCT: ${Build.PRODUCT}
        """.trimIndent()
    }
}