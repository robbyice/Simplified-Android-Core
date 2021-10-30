package org.nypl.simplified.vanilla.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CatalogViewModel : ViewModel() {

  private val _searchStatus = MutableStateFlow<SearchState>(SearchState.Finished(emptyList()))
  val searchStatus: StateFlow<SearchState>
    get() = _searchStatus

  // These should be injected (Hilt/Dagger, Koin, Katana...)
  // We need to make some of these dependencies mutable for testing purposes
  companion object {
    val moshi = Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()

    var service = Retrofit.Builder()
      .baseUrl("http://openlibrary.org")
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(OpenLibraryService::class.java)

    var dispatcher : CoroutineDispatcher = Dispatchers.Main
  }

  fun submitSearch(searchText: String) {
    if (searchText.isNotEmpty()) {
      _searchStatus.value = SearchState.Loading
      viewModelScope.launch {
        withContext(dispatcher) {
          val results = service.searchForBooks(searchText)
          _searchStatus.emit(SearchState.Finished(results.toBookItems()))
        }
      }
    }
  }
}

fun OpenLibraryServiceResponse.toBookItems(): List<BookItem> {
  return docs.map { BookItem(it.title) }
}

sealed class SearchState {
  object Loading: SearchState()
  data class Finished(val books: List<BookItem>): SearchState()
}
