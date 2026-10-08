package com.kairon.android.core.network

import javax.inject.Qualifier

/** The authenticator-free [okhttp3.OkHttpClient] — see [AuthAuthenticator]'s own doc comment. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RawHttpClient
