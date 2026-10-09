package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.DayHospitals
import com.avinash.yatramitra.model.Hospital
import com.avinash.yatramitra.model.SosAlert
import com.avinash.yatramitra.model.SosType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosRulesTest {

    @Test
    fun `every kind of SOS has a label and a one-line explanation`() {
        SosType.entries.forEach {
            assertTrue(SosRules.info(it).label.isNotBlank())
            assertTrue(SosRules.info(it).help.isNotBlank())
        }
        assertEquals("Accident", SosRules.info(SosType.ACCIDENT).label)
    }

    @Test
    fun `the chat text says what, who and where`() {
        assertEquals(
            "SOS: Breakdown. Ravi needs help. Location: Near NH 275, Srirangapatna.",
            SosRules.messageText(SosType.BREAKDOWN, "Ravi", "Near NH 275, Srirangapatna")
        )
        assertEquals("SOS: Need fuel. Someone needs help. Location not available.", SosRules.messageText(SosType.FUEL, "", null))
        assertEquals("SOS: Need fuel. Ravi needs help. Location not available.", SosRules.messageText(SosType.FUEL, "Ravi", "  "))
    }

    @Test
    fun `each kind offers the right numbers and searches`() {
        val accident = SosRules.actions(SosType.ACCIDENT)
        assertEquals("108", (accident.first() as SosRules.Action.Dial).number)
        assertTrue(accident.any { it is SosRules.Action.Dial && it.number == "112" })
        assertTrue(SosRules.actions(SosType.POLICE).any { it is SosRules.Action.Dial && it.number == "100" })
        assertTrue(SosRules.actions(SosType.BREAKDOWN).any { it is SosRules.Action.Dial && it.number == "1033" })
        assertEquals("petrol pump", (SosRules.actions(SosType.FUEL).first() as SosRules.Action.SearchMaps).query)
        assertEquals("ATM", (SosRules.actions(SosType.ATM).first() as SosRules.Action.SearchMaps).query)
        SosType.entries.forEach { assertTrue(SosRules.actions(it).isNotEmpty()) }
    }

    @Test
    fun `hospitals are offered for accidents and medical help only`() {
        assertTrue(SosRules.showsHospitals(SosType.ACCIDENT))
        assertTrue(SosRules.showsHospitals(SosType.MEDICAL))
        assertFalse(SosRules.showsHospitals(SosType.FUEL))
    }

    @Test
    fun `nearest saved hospitals across all days, closest first, each once`() {
        val near = Hospital(id = "a", name = "Near", lat = 12.30, lng = 76.65)
        val mid = Hospital(id = "b", name = "Mid", lat = 12.40, lng = 76.65)
        val far = Hospital(id = "c", name = "Far", lat = 13.00, lng = 77.60)
        val saved = listOf(DayHospitals(0, hospitals = listOf(far, mid)), DayHospitals(1, hospitals = listOf(near, mid)))
        val result = SosRules.nearestHospitals(saved, 12.29, 76.64, count = 2)
        assertEquals(listOf("Near", "Mid"), result.map { it.first.name })
        assertEquals(1.5, result[0].second, 0.5)
        assertTrue(SosRules.nearestHospitals(emptyList(), 1.0, 1.0).isEmpty())
    }

    @Test
    fun `an SOS is stored on the message and read back`() {
        val alert = SosAlert(SosType.MEDICAL, 12.9, 77.6, "Near Jayanagar")
        val fields = mapOf<String, Any?>("text" to "x") + SosRules.toFields(alert)
        assertEquals("sos", fields["kind"])
        assertEquals(alert, SosRules.fromFields(fields))
        assertEquals(SosAlert(SosType.FUEL), SosRules.fromFields(mapOf("kind" to "sos", "sosType" to "FUEL")))
    }

    @Test
    fun `ordinary or odd messages are not SOS`() {
        assertNull(SosRules.fromFields(mapOf("text" to "hello")))
        assertNull(SosRules.fromFields(mapOf("kind" to "sos", "sosType" to "ALIENS")))
        assertNull(SosRules.fromFields(mapOf("kind" to "chat", "sosType" to "FUEL")))
    }
}
