package com.kairon.android.core.network

import com.kairon.android.about.AboutApi
import com.kairon.android.client.api.AuthControllerApi
import com.kairon.android.client.api.MeControllerApi
import com.kairon.android.client.api.PlanningControllerApi
import com.kairon.android.client.api.SyncControllerApi
import com.kairon.android.client.api.TodoControllerApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Singleton

/**
 * Wires the generated `:openapi-client` interfaces to a real [Retrofit]
 * instance: one [Json], one authenticator-free [OkHttpClient] for
 * [AuthAuthenticator]'s own refresh call, and the real client with
 * [AuthInterceptor]/[AuthAuthenticator] installed (M11 D11/D12).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // Every `@Contextual` field the generated client emits for a
        // non-multiplatform JDK type needs its serializer registered here —
        // see JsonContextualSerializers.kt.
        serializersModule = SerializersModule {
            contextual(UUID::class, UuidSerializer)
            contextual(LocalDate::class, LocalDateSerializer)
            contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
            contextual(BigDecimal::class, BigDecimalSerializer)
        }
    }

    @Provides
    @Singleton
    @RawHttpClient
    fun rawHttpClient(httpErrorLoggingInterceptor: HttpErrorLoggingInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(httpErrorLoggingInterceptor)
        .build()

    @Provides
    @Singleton
    fun httpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
        httpErrorLoggingInterceptor: HttpErrorLoggingInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authAuthenticator)
            .addInterceptor(logging)
            .addInterceptor(httpErrorLoggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun retrofit(httpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(NetworkConfig.BASE_URL)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthControllerApi = retrofit.create(AuthControllerApi::class.java)

    @Provides
    @Singleton
    fun todoApi(retrofit: Retrofit): TodoControllerApi = retrofit.create(TodoControllerApi::class.java)

    @Provides
    @Singleton
    fun planningApi(retrofit: Retrofit): PlanningControllerApi = retrofit.create(PlanningControllerApi::class.java)

    @Provides
    @Singleton
    fun syncApi(retrofit: Retrofit): SyncControllerApi = retrofit.create(SyncControllerApi::class.java)

    @Provides
    @Singleton
    fun meApi(retrofit: Retrofit): MeControllerApi = retrofit.create(MeControllerApi::class.java)

    @Provides
    @Singleton
    fun aboutApi(retrofit: Retrofit): AboutApi = retrofit.create(AboutApi::class.java)
}
