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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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
    }

    @Provides
    @Singleton
    @RawHttpClient
    fun rawHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun httpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authAuthenticator)
            .addInterceptor(logging)
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
