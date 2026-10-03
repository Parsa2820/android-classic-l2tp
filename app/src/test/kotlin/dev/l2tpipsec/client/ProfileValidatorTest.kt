package dev.l2tpipsec.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    private val complete = ProfileInput(
        server = "203.0.113.10",
        psk = "lab-ipsec-psk",
        username = "labuser",
        password = "lab-password",
    )

    @Test
    fun acceptsACompleteLabProfile() {
        assertEquals(ProfileCheck.Ok, ProfileValidator.validate(complete))
    }

    @Test
    fun rejectsABlankServerAndAServerWithSpaces() {
        val blank = ProfileValidator.validate(complete.copy(server = "  "))
        val spaced = ProfileValidator.validate(complete.copy(server = "vpn host"))
        assertTrue(blank is ProfileCheck.Invalid)
        assertTrue(spaced is ProfileCheck.Invalid)
    }

    @Test
    fun requiresPskAndPppCredentials() {
        assertTrue(ProfileValidator.validate(complete.copy(psk = "")) is ProfileCheck.Invalid)
        assertTrue(ProfileValidator.validate(complete.copy(username = "")) is ProfileCheck.Invalid)
        assertTrue(ProfileValidator.validate(complete.copy(password = "")) is ProfileCheck.Invalid)
    }
}
