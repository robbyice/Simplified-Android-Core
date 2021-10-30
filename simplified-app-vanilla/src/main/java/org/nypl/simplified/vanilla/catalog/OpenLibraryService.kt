package org.nypl.simplified.vanilla.catalog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenLibraryService {
  @GET("/search.json")
  suspend fun searchForBooks(@Query("title") title: String): OpenLibraryServiceResponse
}

@JsonClass(generateAdapter = true)
data class OpenLibraryServiceResponse(
  @field:Json(name="docs") val docs: List<OpenLibraryDoc>
)

@JsonClass(generateAdapter = true)
data class OpenLibraryDoc(
  @field:Json(name = "title") val title: String,
)
