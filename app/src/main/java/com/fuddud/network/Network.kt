package com.fuddud.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SearchResponse(
    @SerializedName("products") val products: List<ProductDto>?
)

// Ответ API при поиске по штрих-коду
data class BarcodeResponse(
    @SerializedName("code") val code: String?,
    @SerializedName("product") val product: ProductDto?,
    @SerializedName("status") val status: Int? // 1 - найден, 0 - не найден
)

data class ProductDto(
    @SerializedName("code") val code: String?,
    @SerializedName("product_name") val productName: String?,
    @SerializedName("nutriments") val nutriments: NutrimentsDto?,
    @SerializedName("image_thumb_url") val imageThumbUrl: String?
)

data class NutrimentsDto(
    @SerializedName("energy-kcal_100g") val energyKcal100g: Double?,
    @SerializedName("proteins_100g") val proteins100g: Double?,
    @SerializedName("carbohydrates_100g") val carbohydrates100g: Double?,
    @SerializedName("fat_100g") val fat100g: Double?
)

interface OpenFoodFactsApi {
    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") terms: String,
        @Query("search_simple") simple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("fields") fields: String = "product_name,nutriments,image_thumb_url,code"
    ): SearchResponse

    // Новый метод поиска по штрих-коду v2
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,nutriments,image_thumb_url,code"
    ): BarcodeResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://world.openfoodfacts.org/"

    val api: OpenFoodFactsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenFoodFactsApi::class.java)
    }
}
