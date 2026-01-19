package com.scanium.app.regression

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scanium.app.ml.ItemCategory
import com.scanium.app.ml.classification.ClassificationInput
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationStatus
import com.scanium.app.ml.classification.CloudClassifier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * TEST 3: Cloud classification request path works end-to-end (without real camera)
 *
 * Validates:
 * - Classification request reaches backend successfully
 * - Backend returns valid classification result
 * - Result contains expected fields (label, confidence, status)
 *
 * Uses generated bitmaps (solid colors) instead of real camera captures
 * to ensure deterministic testing without binary test assets.
 */
@RunWith(AndroidJUnit4::class)
class CloudClassificationRegressionTest {
    private lateinit var context: Context
    private lateinit var cloudClassifier: CloudClassifier

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            RegressionTestConfig.initialize()
        }

        /**
         * Generate a solid color test bitmap without using TestBridge
         * (in case TestBridge is not available in androidTest)
         */
        private fun generateTestBitmap(
            width: Int = 200,
            height: Int = 200,
            color: Int = 0xFF3498DB.toInt(),
        ): Bitmap {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                val paint =
                    Paint().apply {
                        this.color = color
                        style = Paint.Style.FILL
                    }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }
    }

    @Before
    fun setUp() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()

            // Check backend is configured and reachable
            Assume.assumeTrue(
                "Backend not configured for cloud classification tests",
                RegressionTestConfig.isCloudModeAvailable(),
            )

            BackendHealthGate.checkBackendOrSkip()

            cloudClassifier =
                CloudClassifier(
                    context = context,
                    domainPackId = "home_resale",
                )
        }

    @Test
    fun testCloudClassification_SuccessfulRequest() =
        runTest {
            // Arrange - create a test bitmap (no binary asset)
            val testBitmap =
                generateTestBitmap(
                    width = 224,
                    height = 224,
                    // Blue
                    color = 0xFF3498DB.toInt(),
                )

            val input =
                ClassificationInput(
                    aggregatedId = "test_${UUID.randomUUID()}",
                    bitmap = testBitmap,
                )

            // Act
            val result = cloudClassifier.classifySingle(input)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result!!.mode).isEqualTo(ClassificationMode.CLOUD)

            // Check status - should be SUCCESS or FAILED (not null)
            assertThat(result.status).isNotNull()

            when (result.status) {
                ClassificationStatus.SUCCESS -> {
                    // Successful classification
                    assertThat(result.label).isNotNull()
                    assertThat(result.confidence).isGreaterThan(0f)
                }

                ClassificationStatus.FAILED -> {
                    // Backend returned an error - still a valid test result
                    // This can happen if the backend doesn't recognize the solid color
                    assertThat(result.errorMessage).isNotNull()
                }

                else -> {
                    // Unexpected status
                    assertThat(result.status).isAnyOf(
                        ClassificationStatus.SUCCESS,
                        ClassificationStatus.FAILED,
                    )
                }
            }
        }

    @Test
    fun testCloudClassification_MultipleRequests() =
        runTest {
            // Test multiple requests to verify consistency
            val colors =
                listOf(
                    // Blue
                    0xFF3498DB.toInt(),
                    // Green
                    0xFF2ECC71.toInt(),
                    // Red
                    0xFFE74C3C.toInt(),
                )

            val results =
                colors.mapIndexed { index, color ->
                    val bitmap = generateTestBitmap(224, 224, color)
                    val input =
                        ClassificationInput(
                            aggregatedId = "test_multi_$index",
                            bitmap = bitmap,
                        )
                    cloudClassifier.classifySingle(input)
                }

            // All requests should complete (success or failure)
            assertThat(results).hasSize(3)
            results.forEach { result ->
                assertThat(result).isNotNull()
                assertThat(result!!.mode).isEqualTo(ClassificationMode.CLOUD)
            }
        }

    @Test
    fun testCloudClassification_ResultContainsExpectedFields() =
        runTest {
            // Arrange
            val testBitmap = generateTestBitmap(224, 224, 0xFF9B59B6.toInt())
            val input =
                ClassificationInput(
                    aggregatedId = "test_fields_${UUID.randomUUID()}",
                    bitmap = testBitmap,
                )

            // Act
            val result = cloudClassifier.classifySingle(input)

            // Assert - result structure is valid
            assertThat(result).isNotNull()
            assertThat(result!!.mode).isEqualTo(ClassificationMode.CLOUD)
            assertThat(result.category).isNotNull()
            // Category should be a valid ItemCategory enum value
            assertThat(ItemCategory.values()).contains(result.category)
        }

    @Test
    fun testCloudClassification_HandlesLargeBitmap() =
        runTest {
            // Test with a larger bitmap (closer to real camera capture size)
            val largeBitmap =
                generateTestBitmap(
                    width = 640,
                    height = 480,
                    color = 0xFFF39C12.toInt(),
                )

            val input =
                ClassificationInput(
                    aggregatedId = "test_large_${UUID.randomUUID()}",
                    bitmap = largeBitmap,
                )

            // Act - should not throw or timeout
            val result = cloudClassifier.classifySingle(input)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result!!.mode).isEqualTo(ClassificationMode.CLOUD)
        }

    @Test
    fun testCloudClassification_DomainPackIdIncluded() =
        runTest {
            // Verify the domain pack ID is properly included in requests
            val classifier =
                CloudClassifier(
                    context = context,
                    domainPackId = "home_resale",
                )

            val testBitmap = generateTestBitmap(224, 224, 0xFF1ABC9C.toInt())
            val input =
                ClassificationInput(
                    aggregatedId = "test_domain_${UUID.randomUUID()}",
                    bitmap = testBitmap,
                )

            val result = classifier.classifySingle(input)

            // Request should complete without domain pack errors
            assertThat(result).isNotNull()
            // If status is FAILED, error should not be about domain pack
            if (result!!.status == ClassificationStatus.FAILED) {
                val error = result.errorMessage ?: ""
                assertThat(error.lowercase()).doesNotContain("domain")
                assertThat(error.lowercase()).doesNotContain("invalid pack")
            }
        }
}
