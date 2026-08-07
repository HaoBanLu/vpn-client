package com.vpn.member.data.api

import com.vpn.member.data.local.TokenStore
import com.vpn.member.data.session.SessionAuth
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.vpn.member.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ApiClient {
    fun create(tokenStore: TokenStore): VpnApi {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val jwt = tokenStore.getJwt()
            val authedRequest =
                if (jwt.isNullOrBlank()) {
                    request
                } else {
                    request.newBuilder()
                        .header("Authorization", "Bearer $jwt")
                        .build()
                }
            chain.proceed(authedRequest)
        }

        val sessionInterceptor = Interceptor { chain ->
            val request = chain.request()
            val hadAuth = !request.header("Authorization").isNullOrBlank()
            val response = chain.proceed(request)
            if (response.code == 401) {
                val bodyText = runCatching { response.peekBody(2048).string() }.getOrNull().orEmpty()
                SessionAuth.invalidateIfNeeded(
                    tokenStore = tokenStore,
                    path = request.url.encodedPath,
                    hadAuth = hadAuth,
                    body = bodyText,
                )
            }
            response
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .addInterceptor { chain ->
                val request =
                    chain.request().newBuilder()
                        .header("User-Agent", "KuayunVPN-Android/${BuildConfig.VERSION_NAME}")
                        .build()
                chain.proceed(request)
            }
            .addInterceptor(authInterceptor)
            .addInterceptor(sessionInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VpnApi::class.java)
    }
}
