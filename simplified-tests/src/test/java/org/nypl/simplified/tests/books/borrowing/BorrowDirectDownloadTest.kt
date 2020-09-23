package org.nypl.simplified.tests.books.borrowing

import android.content.Context
import io.reactivex.disposables.Disposable
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.joda.time.Instant
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.bearer_token.LSHTTPBearerTokenInterceptors
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.Downloading
import org.nypl.simplified.books.book_registry.BookStatus.FailedDownload
import org.nypl.simplified.books.book_registry.BookStatus.Loaned
import org.nypl.simplified.books.book_registry.BookStatus.Loaned.LoanedDownloaded
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.borrowing.internal.BorrowDirectDownload
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpConnectionFailed
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpContentTypeIncompatible
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpRequestFailed
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.requiredURIMissing
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericEPUBFiles
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericPDFFiles
import org.nypl.simplified.opds.core.OPDSAcquisitionPathElement
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.tests.MockAccountProviders
import org.nypl.simplified.tests.MockBookDatabase
import org.nypl.simplified.tests.MockBookDatabaseEntry
import org.nypl.simplified.tests.MockBookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.tests.MockBookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.tests.TestDirectories
import org.slf4j.LoggerFactory

class BorrowDirectDownloadTest {

  private lateinit var account: AccountType
  private lateinit var accountId: AccountID
  private lateinit var accountProvider: AccountProvider
  private lateinit var bookDatabase: BookDatabaseType
  private lateinit var bookDatabaseEntry: MockBookDatabaseEntry
  private lateinit var bookEvents: MutableList<BookStatusEvent>
  private lateinit var bookFormatSupport: BookFormatSupportType
  private lateinit var bookID: BookID
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bookStates: MutableList<BookStatus>
  private lateinit var context: MockBorrowContext
  private lateinit var epubHandle: MockBookDatabaseEntryFormatHandleEPUB
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var pdfHandle: MockBookDatabaseEntryFormatHandlePDF
  private lateinit var profile: ProfileReadableType
  private lateinit var taskRecorder: TaskRecorderType
  private lateinit var webServer: MockWebServer
  private var bookRegistrySub: Disposable? = null

  private val logger = LoggerFactory.getLogger(BorrowDirectDownloadTest::class.java)

  private fun verifyBookRegistryHasStatus(clazz: Class<*>) {
    val registryStatus = this.bookRegistry.bookStatusOrNull(this.bookID)!!
    assertEquals(clazz, registryStatus.javaClass)
  }

  private fun <T> anyNonNull(): T =
    Mockito.argThat { x -> x != null }

  @Before
  fun testSetup() {
    this.webServer = MockWebServer()
    this.webServer.start(20000)

    this.taskRecorder =
      TaskRecorder.create()

    this.bookFormatSupport =
      Mockito.mock(BookFormatSupportType::class.java)
    this.bookRegistry =
      BookRegistry.create()
    this.bookStates =
      mutableListOf()
    this.bookEvents =
      mutableListOf()
    this.bookRegistrySub =
      this.bookRegistry.bookEvents()
        .subscribe(this::recordBookEvent)

    this.account =
      Mockito.mock(AccountType::class.java)
    this.accountProvider =
      MockAccountProviders.fakeProvider("urn:uuid:ea9480d4-5479-4ef1-b1d1-84ccbedb680f")

    val androidContext =
      Mockito.mock(Context::class.java)

    this.httpClient =
      LSHTTPClients()
        .create(
          context = androidContext,
          configuration = LSHTTPClientConfiguration(
            "simplified-tests",
            "999.999.0"
          )
        )

    this.accountId =
      AccountID.generate()

    val initialFeedEntry =
      BorrowTests.opdsLoanedFeedEntryOfType(this.webServer, genericEPUBFiles.fullType)
    this.bookID =
      BookIDs.newFromOPDSEntry(initialFeedEntry)

    val bookInitial =
      Book(
        id = this.bookID,
        account = this.accountId,
        cover = null,
        thumbnail = null,
        entry = initialFeedEntry,
        formats = listOf()
      )

    this.bookDatabase =
      MockBookDatabase(this.accountId)
    this.bookDatabaseEntry =
      MockBookDatabaseEntry(bookInitial)
    this.pdfHandle =
      MockBookDatabaseEntryFormatHandlePDF(this.bookID)
    this.epubHandle =
      MockBookDatabaseEntryFormatHandleEPUB(this.bookID)

    this.context =
      MockBorrowContext(
        logger = this.logger,
        bookRegistry = this.bookRegistry,
        temporaryDirectory = TestDirectories.temporaryDirectory(),
        account = this.account,
        clock = { Instant.now() },
        httpClient = this.httpClient,
        taskRecorder = this.taskRecorder,
        isCancelled = false,
        bookDatabaseEntry = this.bookDatabaseEntry,
        bookInitial = bookInitial
      )
  }

  private fun recordBookEvent(event: BookStatusEvent) {
    this.logger.debug("event: {}", event)
    val status = this.bookRegistry.bookStatusOrNull(event.book())!!
    this.logger.debug("status: {}", status)
    this.bookStates.add(status)
    this.bookEvents.add(event)
  }

  @After
  fun tearDown() {
    this.webServer.close()
  }

  /**
   * A direct download can't be performed if no URI is available.
   */

  @Test
  fun testNoURI() {
    val task = BorrowDirectDownload.createSubtask()

    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null)

    try {
      task.execute(this.context)
      Assert.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(requiredURIMissing, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A failing HTTP connection fails the download.
   */

  @Test
  fun testHTTPConnectionFails() {
    val task = BorrowDirectDownload.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()

    try {
      task.execute(this.context)
      Assert.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(httpConnectionFailed, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A 404 fails the download.
   */

  @Test
  fun testHTTP404Fails() {
    val task = BorrowDirectDownload.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()

    this.webServer.enqueue(MockResponse().setResponseCode(404))

    try {
      task.execute(this.context)
      Assert.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(httpRequestFailed, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * An incompatible MIME type fails the download.
   */

  @Test
  fun testMIMEIncompatibleFails() {
    val task = BorrowDirectDownload.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null)

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/plain")

    this.webServer.enqueue(response)

    try {
      task.execute(this.context)
      Assert.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(httpContentTypeIncompatible, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A file is downloaded.
   */

  @Test
  fun testDownloadOkPDF() {
    val task = BorrowDirectDownload.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null)

    this.bookDatabaseEntry.writeOPDSEntry(
      BorrowTests.opdsLoanedFeedEntryOfType(this.webServer, genericPDFFiles.fullType)
    )
    this.bookDatabaseEntry.entryWrites = 0
    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(this.pdfHandle)
    check(this.bookDatabaseEntry.formatHandlesField.size == 1)
    check(BookStatus.fromBook(this.bookDatabaseEntry.book) is Loaned)

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/pdf")
        .setBody("PDF!")

    this.webServer.enqueue(response)

    task.execute(this.context)

    this.verifyBookRegistryHasStatus(LoanedDownloaded::class.java)
    assertEquals("PDF!", this.pdfHandle.bookData)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(LoanedDownloaded::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A file is downloaded.
   */

  @Test
  fun testDownloadOkEPUB() {
    val task = BorrowDirectDownload.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericEPUBFiles, null)

    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(this.epubHandle)
    check(this.bookDatabaseEntry.formatHandlesField.size == 1)
    check(BookStatus.fromBook(this.bookDatabaseEntry.book) is Loaned)

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/epub+zip")
        .setBody("EPUB!")

    this.webServer.enqueue(response)

    task.execute(this.context)

    this.verifyBookRegistryHasStatus(LoanedDownloaded::class.java)
    assertEquals("EPUB!", this.epubHandle.bookData)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(LoanedDownloaded::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A file is downloaded even if it has to go through a bearer token.
   */

  @Test
  fun testDownloadOkEPUBBearerToken() {
    val task = BorrowDirectDownload.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericEPUBFiles, null)

    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(this.epubHandle)
    check(this.bookDatabaseEntry.formatHandlesField.size == 1)
    check(BookStatus.fromBook(this.bookDatabaseEntry.book) is Loaned)

    val response0 =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", LSHTTPBearerTokenInterceptors.bearerTokenContentType)
        .setBody(
          """{
          "access_token": "abcd",
          "expires_in": 1000,
          "location": "http://localhost:20000/book.epub"
        }
          """.trimIndent()
        )

    val response1 =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/epub+zip")
        .setBody("EPUB!")

    this.webServer.enqueue(response0)
    this.webServer.enqueue(response1)

    task.execute(this.context)

    val sent0 = this.webServer.takeRequest()
    assertEquals(null, sent0.getHeader("Authorization"))
    val sent1 = this.webServer.takeRequest()
    assertEquals("Bearer abcd", sent1.getHeader("Authorization"))

    this.verifyBookRegistryHasStatus(LoanedDownloaded::class.java)
    assertEquals("EPUB!", this.epubHandle.bookData)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(LoanedDownloaded::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }
}
