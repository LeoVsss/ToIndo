package br.com.toindo.data.remote

import br.com.toindo.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object Rede {

    fun criarApi(): ToIndoApi {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val cliente = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.URL_BASE_API)
            .client(cliente)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ToIndoApi::class.java)
    }
}
