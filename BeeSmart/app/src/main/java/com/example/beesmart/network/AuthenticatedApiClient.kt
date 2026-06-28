package com.example.beesmart.network

import android.content.Context
import com.example.beesmart.BuildConfig
import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskStatus
import com.example.beesmart.network.models.TreatmentType
import com.example.beesmart.utils.NetworkConfig
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HiveTypeAdapter {
    @FromJson
    fun fromJson(value: Int): HiveType = when (value) {
        0 -> HiveType.Langstroth
        1 -> HiveType.Dadant
        2 -> HiveType.TopBar
        3 -> HiveType.Warre
        4 -> HiveType.Other
        else -> HiveType.Other
    }

    @ToJson
    fun toJson(type: HiveType): Int = when (type) {
        HiveType.Langstroth -> 0
        HiveType.Dadant -> 1
        HiveType.TopBar -> 2
        HiveType.Warre -> 3
        HiveType.Other -> 4
    }
}

class HiveStatusAdapter {
    @FromJson
    fun fromJson(value: Int): HiveStatus = when (value) {
        0 -> HiveStatus.Active
        1 -> HiveStatus.Queenless
        2 -> HiveStatus.Weak
        3 -> HiveStatus.Sick
        4 -> HiveStatus.Preparing
        5 -> HiveStatus.Inactive
        else -> HiveStatus.Active
    }

    @ToJson
    fun toJson(status: HiveStatus): Int = when (status) {
        HiveStatus.Active -> 0
        HiveStatus.Queenless -> 1
        HiveStatus.Weak -> 2
        HiveStatus.Sick -> 3
        HiveStatus.Preparing -> 4
        HiveStatus.Inactive -> 5
    }
}

class TaskPriorityAdapter {
    @FromJson
    fun fromJson(value: Int): TaskPriority = when (value) {
        0 -> TaskPriority.Low
        1 -> TaskPriority.Normal
        2 -> TaskPriority.High
        3 -> TaskPriority.Critical
        else -> TaskPriority.Normal
    }

    @ToJson
    fun toJson(priority: TaskPriority): Int = when (priority) {
        TaskPriority.Low -> 0
        TaskPriority.Normal -> 1
        TaskPriority.High -> 2
        TaskPriority.Critical -> 3
    }
}

class TaskStatusAdapter {
    @FromJson
    fun fromJson(value: Int): TaskStatus = when (value) {
        0 -> TaskStatus.Pending
        1 -> TaskStatus.InProgress
        2 -> TaskStatus.Completed
        3 -> TaskStatus.Cancelled
        else -> TaskStatus.Pending
    }

    @ToJson
    fun toJson(status: TaskStatus): Int = when (status) {
        TaskStatus.Pending -> 0
        TaskStatus.InProgress -> 1
        TaskStatus.Completed -> 2
        TaskStatus.Cancelled -> 3
    }
}

class TreatmentTypeAdapter {
    @FromJson
    fun fromJson(value: Int): TreatmentType = when (value) {
        0 -> TreatmentType.Varroa
        1 -> TreatmentType.Nosema
        2 -> TreatmentType.Fungal
        3 -> TreatmentType.Viral
        4 -> TreatmentType.Bacterial
        5 -> TreatmentType.Preventive
        6 -> TreatmentType.Other
        else -> TreatmentType.Other
    }

    @ToJson
    fun toJson(type: TreatmentType): Int = when (type) {
        TreatmentType.Varroa -> 0
        TreatmentType.Nosema -> 1
        TreatmentType.Fungal -> 2
        TreatmentType.Viral -> 3
        TreatmentType.Bacterial -> 4
        TreatmentType.Preventive -> 5
        TreatmentType.Other -> 6
    }
}

class ExtractionTypeAdapter {
    @FromJson
    fun fromJson(value: Int): ExtractionType = when (value) {
        0 -> ExtractionType.Honey
        1 -> ExtractionType.Pollen
        2 -> ExtractionType.Propolis
        3 -> ExtractionType.RoyalJelly
        4 -> ExtractionType.Wax
        5 -> ExtractionType.Other
        else -> ExtractionType.Other
    }

    @ToJson
    fun toJson(type: ExtractionType): Int = when (type) {
        ExtractionType.Honey -> 0
        ExtractionType.Pollen -> 1
        ExtractionType.Propolis -> 2
        ExtractionType.RoyalJelly -> 3
        ExtractionType.Wax -> 4
        ExtractionType.Other -> 5
    }
}

object AuthenticatedApiClient {

    private val moshi = Moshi.Builder()
        .add(HiveTypeAdapter())
        .add(HiveStatusAdapter())
        .add(TaskPriorityAdapter())
        .add(TaskStatusAdapter())
        .add(TreatmentTypeAdapter())
        .add(ExtractionTypeAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }

    private fun getOkHttpClient(context: Context): OkHttpClient {
        val sessionManager = SessionManager(context)
        val reachability = BackendReachability()
        val authInterceptor = AuthInterceptor(sessionManager, reachability)

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))

        if (BuildConfig.DEBUG) {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    fun createRetrofit(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NetworkConfig.baseUrl)
            .client(getOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    fun createApiaryApi(context: Context): ApiaryApi = createRetrofit(context).create(ApiaryApi::class.java)

    fun createHiveApi(context: Context): HiveApi = createRetrofit(context).create(HiveApi::class.java)

    fun createTaskApi(context: Context): TaskApi = createRetrofit(context).create(TaskApi::class.java)

    fun createInspectionApi(context: Context): InspectionApi = createRetrofit(context).create(InspectionApi::class.java)

    fun createTreatmentApi(context: Context): TreatmentApi = createRetrofit(context).create(TreatmentApi::class.java)

    fun createExtractionApi(context: Context): ExtractionApi = createRetrofit(context).create(ExtractionApi::class.java)
}
