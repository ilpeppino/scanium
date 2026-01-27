package com.scanium.app.items.edit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AiAssistantChooserLocalizationTest {
    @Test
    fun aiChooser_isLocalized_inDutch() {
        Locale.setDefault(Locale("nl"))
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("AI-assistent", context.getString(R.string.ai_chooser_title))
        assertEquals("Prijs mijn item", context.getString(R.string.ai_chooser_price_title))
        assertEquals("Listingtekst genereren", context.getString(R.string.ai_chooser_listing_title))
    }
}
