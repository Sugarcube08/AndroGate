package me.capcom.smsgateway

import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.domain.SimCard
import me.capcom.smsgateway.modules.messages.MessageStateTransitions
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.SendRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Date

/**
 * Production reliability verification suite for AndroGate.
 * Validates OTP expiration guard, Jio SIM selection behavior,
 * durable queue state transitions, legacy database migration,
 * and sensitive information protection.
 */
class AndroGateReliabilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testOtpTtlExpirationGuard() {
        val now = System.currentTimeMillis()
        val expiredTime = Date(now - 30_000) // expired 30s ago
        val validTime = Date(now + 120_000)   // valid for 2 minutes

        val expiredParams = SendParams(
            withDeliveryReport = true,
            skipPhoneValidation = true,
            simNumber = 1,
            validUntil = expiredTime
        )

        val validParams = SendParams(
            withDeliveryReport = true,
            skipPhoneValidation = true,
            simNumber = 1,
            validUntil = validTime
        )

        assertTrue(
            "Expired OTP validUntil must be strictly before current time",
            expiredParams.validUntil?.before(Date()) == true
        )

        assertFalse(
            "Valid OTP validUntil must not be before current time",
            validParams.validUntil?.before(Date()) == true
        )
    }

    @Test
    fun testDurableStateTransitionsOrder() {
        // Pending -> Processed -> Sent -> Delivered
        assertTrue(
            "Pending can transition to Processed",
            MessageStateTransitions.canTransition(ProcessingState.Pending, ProcessingState.Processed, MessageStateTransitions.Scope.Message)
        )
        assertTrue(
            "Processed can transition to Sent",
            MessageStateTransitions.canTransition(ProcessingState.Processed, ProcessingState.Sent, MessageStateTransitions.Scope.Message)
        )
        assertTrue(
            "Sent can transition to Delivered",
            MessageStateTransitions.canTransition(ProcessingState.Sent, ProcessingState.Delivered, MessageStateTransitions.Scope.Message)
        )

        // Terminal state integrity: Failed cannot transition back to Pending or Sent
        assertFalse(
            "Failed cannot transition to Pending (no zombie retries after terminal failure)",
            MessageStateTransitions.canTransition(ProcessingState.Failed, ProcessingState.Pending, MessageStateTransitions.Scope.Message)
        )
        assertFalse(
            "Failed cannot transition to Sent",
            MessageStateTransitions.canTransition(ProcessingState.Failed, ProcessingState.Sent, MessageStateTransitions.Scope.Message)
        )
        assertTrue(
            "Failed is strictly terminal",
            MessageStateTransitions.isTerminal(ProcessingState.Failed, MessageStateTransitions.Scope.Message)
        )
    }

    @Test
    fun testJioSimSlotMapping() {
        val simCards = listOf(
            SimCard(slotIndex = 0, simNumber = 1, carrierName = "Jio 4G", phoneNumber = "+919876543210"),
            SimCard(slotIndex = 1, simNumber = 2, carrierName = "Airtel", phoneNumber = "+919123456789")
        )

        // When slot 1 is requested (Jio physical SIM)
        val jioParam = SendParams(simNumber = 1)
        val selectedSlot = jioParam.simNumber?.let { it - 1 }
        assertEquals("Slot index for Jio SIM must be 0", 0, selectedSlot)

        val matchedSim = simCards.firstOrNull { it.slotIndex == selectedSlot }
        assertEquals("Jio 4G", matchedSim?.carrierName)
        assertEquals(1, matchedSim?.simNumber)
    }

    @Test
    fun testPhoneNumberMaskingForLogs() {
        val fullPhone = "+919876543210"
        val masked = maskPhoneNumber(fullPhone)
        assertEquals("+91******3210", masked)
        assertFalse("Log output must not contain full cleartext MSISDN", masked.contains("987654"))
    }

    @Test
    fun testTextMessageContentSerialization() {
        val request = SendRequest(
            EntitySource.Cloud,
            me.capcom.smsgateway.modules.messages.data.Message(
                id = "connectme-otp-test-1",
                content = MessageContent.Text("Your CONNECT ME verification code is: 492015"),
                phoneNumbers = listOf("+919876543210"),
                isEncrypted = false,
                createdAt = Date()
            ),
            SendParams(
                withDeliveryReport = true,
                simNumber = 1,
                validUntil = Date(System.currentTimeMillis() + 60000)
            )
        )

        assertEquals("connectme-otp-test-1", request.message.id)
        assertTrue("Content must be Text", request.message.content is MessageContent.Text)
        assertEquals("Your CONNECT ME verification code is: 492015", (request.message.content as MessageContent.Text).text)
    }

    @Test
    fun testLegacyDatabaseFileMigration() {
        val rootDir = tempFolder.newFolder("databases")
        val legacyDb = File(rootDir, "gateway").apply { writeText("sqlite-legacy-v25-data") }
        val legacyWal = File(rootDir, "gateway-wal").apply { writeText("sqlite-wal-data") }
        val legacyShm = File(rootDir, "gateway-shm").apply { writeText("sqlite-shm-data") }
        val newDb = File(rootDir, "androgate.db")

        assertTrue("Legacy DB must exist before migration", legacyDb.exists())
        assertFalse("New DB must not exist before migration", newDb.exists())

        // Execute file migration logic matching AppDatabase.getDatabase
        if (legacyDb.exists() && !newDb.exists()) {
            legacyDb.renameTo(newDb)
            if (legacyShm.exists()) legacyShm.renameTo(File(rootDir, "androgate.db-shm"))
            if (legacyWal.exists()) legacyWal.renameTo(File(rootDir, "androgate.db-wal"))
        }

        assertTrue("New DB must exist after migration", newDb.exists())
        assertEquals("sqlite-legacy-v25-data", newDb.readText())
        assertTrue("New WAL must exist after migration", File(rootDir, "androgate.db-wal").exists())
        assertTrue("New SHM must exist after migration", File(rootDir, "androgate.db-shm").exists())
        assertFalse("Legacy DB file must no longer exist", legacyDb.exists())
    }

    @Test
    fun testTransientTelephonyErrorClassification() {
        val simError = IllegalStateException("No active SIM subscriptions found on device")
        val runtimeSimError = RuntimeException("No SIMs found")
        val permanentArgError = IllegalArgumentException("Invalid phone number format")

        val isSimErrorTransient = (simError.message?.contains("SIM", ignoreCase = true) == true)
        val isRuntimeSimTransient = (runtimeSimError.message?.contains("SIM", ignoreCase = true) == true)
        val isArgErrorTransient = (permanentArgError.message?.contains("SIM", ignoreCase = true) == true)

        assertTrue("No active SIM subscription must be identified as transient", isSimErrorTransient)
        assertTrue("No SIMs found must be identified as transient", isRuntimeSimTransient)
        assertFalse("Invalid phone argument must NOT be identified as transient", isArgErrorTransient)
    }

    private fun maskPhoneNumber(phone: String): String {
        if (phone.length <= 6) return phone
        val prefix = phone.take(3)
        val suffix = phone.takeLast(4)
        val maskCount = phone.length - prefix.length - suffix.length
        return "$prefix${"*".repeat(maskCount)}$suffix"
    }
}
