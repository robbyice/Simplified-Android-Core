package org.nypl.simplified.vanilla.catalog

import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.nypl.simplified.vanilla.TestCoroutineRule

@ExperimentalCoroutinesApi
class CatalogViewModelTest {
  private lateinit var subject: CatalogViewModel

  @get:Rule
  var mainCoroutineRule = TestCoroutineRule()

  private val mockOpenLibraryService: OpenLibraryService = mock()

  @Before
  fun setUp() {
    CatalogViewModel.service = mockOpenLibraryService
    CatalogViewModel.dispatcher = mainCoroutineRule.dispatcher

    subject = CatalogViewModel()
  }

  @Test
  fun `submitting search request sends search to service`() = runBlockingTest {
    subject.submitSearch("some book title")

    verify(mockOpenLibraryService).searchForBooks("some book title")
  }

  @Test
  fun `search status begins with empty finished state`() = runBlockingTest {
    subject.searchStatus.value shouldBeEqualTo SearchState.Finished(emptyList())
  }

  @Test
  fun `search request emits loading state and then finished with list of bookitems`() = runBlockingTest {
    val stubbedResponse = OpenLibraryServiceResponse(
      docs = listOf(
        OpenLibraryDoc("title0"),
        OpenLibraryDoc("title1")
      )
    )
    whenever(mockOpenLibraryService.searchForBooks(any())).thenReturn(stubbedResponse)
    mainCoroutineRule.pauseDispatcher()

    subject.submitSearch("some book title")

    subject.searchStatus.value shouldBeInstanceOf SearchState.Loading::class.java

    mainCoroutineRule.resumeDispatcher()

    subject.searchStatus.value shouldBeInstanceOf SearchState.Finished::class.java

    (subject.searchStatus.value as SearchState.Finished).books.size shouldBeEqualTo 2
    (subject.searchStatus.value as SearchState.Finished).books.forEachIndexed { index, bookItem ->
      bookItem.title shouldBeEqualTo "title${index}"
    }
  }

  @Test
  fun `search does nothing when query is empty`() = runBlockingTest {
    subject.submitSearch("")

    verify(mockOpenLibraryService, never()).searchForBooks(any())
  }
}
