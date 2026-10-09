package com.avinash.yatramitra.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.model.ChatMessage
import com.avinash.yatramitra.model.Hospital
import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSource
import com.avinash.yatramitra.model.SosAlert
import com.avinash.yatramitra.model.SosType
import com.avinash.yatramitra.ui.theme.YatraMitraTheme
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Renders the new SOS, hospital and From/To pieces to PNG files under app/build/screens so they
 * can be looked at without a device. Opt-in: skipped unless run with -PrenderScreens=1.
 *
 * Compose's captureToImage waits for a frame Robolectric's virtual clock never delivers, so this
 * advances the clock itself and draws the activity's view hierarchy into a bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h1500dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, dark: Boolean, content: @Composable () -> Unit) {
        assumeTrue(System.getProperty("renderScreens").orEmpty().isNotEmpty())
        rule.setContent {
            YatraMitraTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                }
            }
        }
        rule.waitForIdle()
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        assertTrue("view not laid out", root.width > 0 && root.height > 0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val cityHospital = Hospital("node/1", "Apollo Hospital, Jayanagar", "21/2, 14th Cross, 3rd Block, Jayanagar, Bengaluru, 560011", "+91 80 4612 4444", 12.93, 77.58, 2.0, true)
    private val districtHospital = Hospital("way/2", "District Hospital Mandya", "MC Road, Mandya, 571401", "", 12.52, 76.90, 98.0, false)
    private val taluk = Hospital("way/3", "General Hospital Srirangapatna", "Srirangapatna, 571438", "08236 252 100", 12.42, 76.69, 121.0, true)

    @Test fun sosChooserLight() = render("sos-chooser-light", false) { SosChooser(onChoose = {}) }

    @Test fun sosChooserDark() = render("sos-chooser-dark", true) { SosChooser(onChoose = {}) }

    @Test fun sosSentAccident() = render("sos-sent-accident-light", false) {
        SosSentContent(
            alert = SosAlert(SosType.ACCIDENT, 12.42, 76.68, "Near NH 275, Srirangapatna"),
            nearestHospitals = listOf(taluk to 1.4, districtHospital to 12.6),
            onDone = {}
        )
    }

    @Test fun sosSentFuelNoLocation() = render("sos-sent-fuel-light", false) {
        SosSentContent(alert = SosAlert(SosType.FUEL), nearestHospitals = emptyList(), onDone = {})
    }

    private fun sosMessage(type: SosType, author: String, located: Boolean) = ChatMessage(
        id = type.name,
        authorMemberId = author,
        authorName = author,
        text = "SOS",
        createdAtMillis = 1_791_100_000_000L,
        sos = if (located) SosAlert(type, 12.42, 76.68, "Near NH 275, Srirangapatna") else SosAlert(type)
    )

    @Composable
    private fun bubbles() {
        SosBubble(sosMessage(SosType.ACCIDENT, "Ravi Kumar", true), isMine = false, authorPhone = "+919000000001")
        SosBubble(sosMessage(SosType.BREAKDOWN, "Asha", true), isMine = true, authorPhone = null)
        SosBubble(sosMessage(SosType.FUEL, "Meera", false), isMine = false, authorPhone = null)
    }

    @Test fun chatSosBubblesLight() = render("chat-sos-bubbles-light", false) { bubbles() }

    @Test fun chatSosBubblesDark() = render("chat-sos-bubbles-dark", true) { bubbles() }

    @Composable
    private fun hospitals(list: List<Hospital>?, finding: Boolean = false, failure: String? = null) = HospitalsCardBody(
        dayLabel = "Day 1",
        updatedAtMillis = if (list != null) 1_791_100_000_000L else null,
        hasRoute = true,
        finding = finding,
        failure = failure,
        hospitals = list,
        routeChanged = false,
        canUpdate = true,
        multiDay = true,
        onFind = {},
        onShowAllDays = {},
        onSearchMaps = {}
    )

    @Test fun hospitalsLight() = render("hospitals-card-light", false) { hospitals(listOf(cityHospital, districtHospital, taluk)) }

    @Test fun hospitalsDark() = render("hospitals-card-dark", true) { hospitals(listOf(cityHospital, districtHospital, taluk)) }

    @Test fun hospitalsStates() = render("hospitals-card-states-light", false) {
        hospitals(null, finding = true)
        hospitals(null, failure = "Couldn't look up hospitals (HTTP 429). Check your internet and try again.")
        hospitals(emptyList())
    }

    @Test fun placeFields() = render("place-fields-light", false) {
        AutocompletePlaceField(label = "From (empty)", value = Place(), onValueChange = {}, onMessage = {}, allowCurrentLocation = true)
        AutocompletePlaceField(
            label = "From (my location)",
            value = Place("Near 10th Main Road, Jayanagar", "10th Main Rd, Jayanagar 5th Block, Bengaluru 560041", 12.925, 77.594, null, PlaceSource.CURRENT_LOCATION, 1L),
            onValueChange = {}, onMessage = {}, allowCurrentLocation = true
        )
        AutocompletePlaceField(
            label = "To (picked)",
            value = Place("House of Commons", "Jayanagar 5th Block, Bengaluru, Karnataka", 12.925, 77.594, "ChIJ-x", PlaceSource.GOOGLE, System.currentTimeMillis()),
            onValueChange = {}, onMessage = {}
        )
        AutocompletePlaceField(label = "To (typed only)", value = Place(name = "Mysuru Palace"), onValueChange = {}, onMessage = {})
    }
}
