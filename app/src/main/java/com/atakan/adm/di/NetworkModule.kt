package com.atakan.adm.di

import com.atakan.adm.download.SafeRedirectInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Segments are long-lived streams: no call timeout, only connect/read limits.
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // Redirection handled by SafeRedirectInterceptor to prevent cross-host credential leaks.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(SafeRedirectInterceptor())
        .build()
}
