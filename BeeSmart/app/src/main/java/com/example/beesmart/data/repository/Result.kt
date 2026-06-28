package com.example.beesmart.data.repository

/**
 * Generic wrapper for handling API responses.
 * Lets the UI represent Success, Error, and Loading states.
 */
sealed class Result<out T> {

    data class Success<T>(val data: T) : Result<T>()

    data class Error(
        val message: String,
        val code: Int? = null,
        val exception: Throwable? = null
    ) : Result<Nothing>()

    object Loading : Result<Nothing>()
}