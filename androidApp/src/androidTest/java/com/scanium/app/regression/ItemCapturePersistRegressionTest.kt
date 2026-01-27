import android.content.Context
import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scanium.app.ItemCategory
import com.scanium.app.items.CaptureType
import com.scanium.app.items.ItemsListScreen
import com.scanium.app.items.RawDetection
import com.scanium.app.items.createAndroidTestItemsViewModel
import com.scanium.app.items.export.bundle.BundleZipExporter
import com.scanium.app.items.export.bundle.ExportBundleRepository
import com.scanium.app.items.export.bundle.ExportViewModel
import com.scanium.app.items.export.bundle.PlainTextExporter
import com.scanium.app.items.photos.ItemPhotoManager
import com.scanium.app.items.photos.PerItemDedupeHelper
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.CloudClassifier
import com.scanium.app.selling.persistence.NoopListingDraftStore
import com.scanium.app.testing.TestBridge
import com.scanium.app.testing.TestConfigOverride
import com.scanium.app.ui.theme.ScaniumTheme
import com.scanium.shared.core.models.model.NormalizedRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ItemCapturePersistRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val args =
            Bundle().apply {
                putString(TestConfigOverride.ARG_BASE_URL, mockWebServer.url("/").toString())
                putString(TestConfigOverride.ARG_API_KEY, "test-key")
                putString(TestConfigOverride.ARG_FORCE_CLOUD_MODE, "true")
            }
        TestConfigOverride.initFromArguments(args)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        TestConfigOverride.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun itemIsSavedImmediatelyAfterCapture() {
        enqueueMultiHypothesisResponse(
            hypothesesJson =
                """
                [
                  {
                    "categoryId": "fashion_tops",
                    "categoryName": "T-Shirt",
                    "explanation": "",
                    "confidence": 0.92,
                    "confidenceBand": "HIGH",
                    "attributes": {}
                  }
                ]
                """.trimIndent(),
            delayMs = 1500,
        )

        val itemsViewModel = createItemsViewModel()
        val exportViewModel = createExportViewModel(context)

        composeTestRule.setContent {
            ScaniumTheme {
                ItemsListScreen(
                    onNavigateBack = {},
                    onNavigateToAssistant = {},
                    onNavigateToEdit = {},
                    draftStore = NoopListingDraftStore,
                    itemsViewModel = itemsViewModel,
                    exportViewModel = exportViewModel,
                )
            }
        }

        composeTestRule.runOnIdle {
            itemsViewModel.onDetectionReady(createRawDetection(label = "Test Chair"))
        }

        composeTestRule.waitUntil(1_000) {
            itemsViewModel.items.value.any { it.labelText == "Test Chair" }
        }

        composeTestRule.onNodeWithText("Test Chair").assertIsDisplayed()
    }

    @Test
    fun unknownClassificationDoesNotBlockSaving() {
        enqueueMultiHypothesisResponse(hypothesesJson = "[]")

        val itemsViewModel = createItemsViewModel()
        val exportViewModel = createExportViewModel(context)

        composeTestRule.setContent {
            ScaniumTheme {
                ItemsListScreen(
                    onNavigateBack = {},
                    onNavigateToAssistant = {},
                    onNavigateToEdit = {},
                    draftStore = NoopListingDraftStore,
                    itemsViewModel = itemsViewModel,
                    exportViewModel = exportViewModel,
                )
            }
        }

        composeTestRule.runOnIdle {
            itemsViewModel.onDetectionReady(createRawDetection(label = "Mystery Item"))
        }

        composeTestRule.waitUntil(2_000) {
            itemsViewModel.items.value.firstOrNull()?.classificationStatus == "NEEDS_REVIEW"
        }

        assertThat(itemsViewModel.showCorrectionDialog.value).isFalse()
        composeTestRule.onNodeWithText("Mystery Item").assertIsDisplayed()
    }

    @Test
    fun correctionDialogCancelDoesNotRemoveItem() {
        enqueueMultiHypothesisResponse(hypothesesJson = "[]")

        val itemsViewModel = createItemsViewModel()
        val exportViewModel = createExportViewModel(context)

        composeTestRule.setContent {
            ScaniumTheme {
                ItemsListScreen(
                    onNavigateBack = {},
                    onNavigateToAssistant = {},
                    onNavigateToEdit = {},
                    draftStore = NoopListingDraftStore,
                    itemsViewModel = itemsViewModel,
                    exportViewModel = exportViewModel,
                )
            }
        }

        composeTestRule.runOnIdle {
            itemsViewModel.onDetectionReady(createRawDetection(label = "Camera Bag"))
        }

        composeTestRule.waitUntil(1_000) {
            itemsViewModel.items.value.isNotEmpty()
        }

        val itemId = itemsViewModel.items.value.first().id

        composeTestRule.runOnIdle {
            itemsViewModel.showCorrectionDialog(itemId, "", "Camera Bag", 0.4f)
            itemsViewModel.dismissCorrectionDialog()
        }

        composeTestRule.waitUntil(1_000) {
            itemsViewModel.items.value.any { it.id == itemId }
        }

        assertThat(itemsViewModel.showCorrectionDialog.value).isFalse()
        composeTestRule.onNodeWithText("Camera Bag").assertIsDisplayed()
    }

    private fun createItemsViewModel() =
        createAndroidTestItemsViewModel(
            classificationMode = MutableStateFlow(ClassificationMode.CLOUD),
            cloudClassificationEnabled = MutableStateFlow(true),
            cloudClassifier = CloudClassifier(context),
            workerDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
        )

    private fun enqueueMultiHypothesisResponse(
        hypothesesJson: String,
        delayMs: Long = 0,
    ) {
        val body =
            """
            {
              "hypotheses": $hypothesesJson,
              "globalConfidence": 0.2,
              "needsRefinement": true,
              "requestId": "req-1"
            }
            """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .apply {
                    if (delayMs > 0) {
                        setBodyDelay(delayMs, TimeUnit.MILLISECONDS)
                    }
                },
        )
    }

    private fun createRawDetection(label: String): RawDetection {
        val imageRef = requireNotNull(TestBridge.createTestImageRef())
        val bitmap = requireNotNull(TestBridge.generateTestBitmap())

        return RawDetection(
            boundingBox = NormalizedRect(0.1f, 0.1f, 0.6f, 0.6f),
            confidence = 0.78f,
            onDeviceLabel = label,
            onDeviceCategory = ItemCategory.UNKNOWN,
            trackingId = null,
            frameSharpness = 0.9f,
            captureType = CaptureType.SINGLE_SHOT,
            thumbnailRef = imageRef,
            fullFrameBitmap = bitmap,
        )
    }

    private fun createExportViewModel(context: Context): ExportViewModel {
        val dedupeHelper = PerItemDedupeHelper()
        val photoManager = ItemPhotoManager(context, dedupeHelper)
        val bundleRepository = ExportBundleRepository(context, photoManager)
        val zipExporter = BundleZipExporter(context)
        val textExporter = PlainTextExporter(context)

        return ExportViewModel(context, bundleRepository, zipExporter, textExporter)
    }
}
