package com.fairshare.android.feature.collaboration

import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.repository.AttachmentRepository
import com.fairshare.android.core.database.repository.CollaborationRepository
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.evidence.EvidenceAttachment
import com.fairshare.android.core.domain.export.ExportEngine
import com.fairshare.android.core.domain.export.ExportFormat
import com.fairshare.android.core.domain.export.ExportScope
import com.fairshare.android.core.domain.export.ShareEngine
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.search.SearchEngine
import com.fairshare.android.core.domain.search.SearchFilter
import com.fairshare.android.core.domain.search.SearchSortBy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase12CollaborationIntegrationTest {

    private lateinit var db: FairShareDatabase
    private lateinit var collabRepo: CollaborationRepository
    private lateinit var attachmentRepo: AttachmentRepository

    private val inr = Currency("INR", "₹", 2)
    private val userA = Member(id = "user_a", name = "Aarav", isCurrentUser = true)
    private val userB = Member(id = "user_b", name = "Bhavna")
    private val userC = Member(id = "user_c", name = "Chirag")
    private val members = listOf(userA, userB, userC)
    private val groupId = "group_trip_1"

    @Before
    fun setUp() = runBlocking {
        db = FairShareDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        collabRepo = CollaborationRepository(db)
        attachmentRepo = AttachmentRepository(db)

        // Seed Users and Group
        db.userDao().insertUsers(
            listOf(
                UserEntity(id = userA.id, phoneNumber = "+919876543210", displayName = userA.name),
                UserEntity(id = userB.id, phoneNumber = "+919876543211", displayName = userB.name),
                UserEntity(id = userC.id, phoneNumber = "+919876543212", displayName = userC.name)
            )
        )
        db.groupDao().insertGroup(
            GroupEntity(
                id = groupId,
                name = "Goa Trip",
                createdById = userA.id
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------
    // 1. Group Activity Tests
    // -------------------------------------------------------------

    @Test
    fun testRecordAndRetrieveGroupActivity() = runBlocking {
        collabRepo.recordActivity(
            groupId = groupId,
            actorId = userA.id,
            eventType = "EXPENSE_CREATED",
            summaryText = "Aarav added \"Beach Resort\" (₹5,000)",
            entityType = "EXPENSE",
            entityId = "exp_101",
            timestamp = 1000L
        )

        collabRepo.recordActivity(
            groupId = groupId,
            actorId = userB.id,
            eventType = "PAYMENT_RECORDED",
            summaryText = "Bhavna paid Aarav ₹2,000",
            entityType = "PAYMENT",
            entityId = "pay_201",
            timestamp = 2000L
        )

        val activities = collabRepo.getActivities(groupId)
        assertEquals(2, activities.size)
        // Descending order
        assertEquals("Bhavna", activities[0].actorName)
        assertEquals("PAYMENT", activities[0].entityType)
        assertEquals("Aarav", activities[1].actorName)
        assertEquals("EXPENSE", activities[1].entityType)
    }

    // -------------------------------------------------------------
    // 2. Group Chat & Expense Link Tests
    // -------------------------------------------------------------

    @Test
    fun testSendAndReceiveChatMessages() = runBlocking {
        val msg1 = collabRepo.sendMessage(
            groupId = groupId,
            senderId = userA.id,
            content = "Hey everyone, welcome to Goa!"
        )
        assertNotNull(msg1.id)
        assertEquals("Aarav", msg1.senderName)

        val msg2 = collabRepo.sendMessage(
            groupId = groupId,
            senderId = userB.id,
            content = "Did you pay for the taxi?",
            messageType = "EXPENSE_LINK",
            referencedEntityId = "exp_taxi"
        )
        assertEquals("EXPENSE_LINK", msg2.messageType)
        assertEquals("exp_taxi", msg2.referencedEntityId)

        val list = collabRepo.getMessages(groupId, currentUserId = userA.id)
        assertEquals(2, list.size)
        assertTrue(list[0].isFromCurrentUser)
        assertEquals(false, list[1].isFromCurrentUser)
    }

    // -------------------------------------------------------------
    // 3. Expense Comments & Dispute Context Tests
    // -------------------------------------------------------------

    @Test
    fun testExpenseDiscussionAndDisputeTagging() = runBlocking {
        val comment1 = collabRepo.addExpenseComment(
            expenseId = "exp_dinner",
            groupId = groupId,
            authorId = userC.id,
            text = "Please exclude me from this split, I wasn't there"
        )
        assertTrue(comment1.isDisputeOrClarification)
        assertEquals("Exclude Request", comment1.disputeTag)
        assertTrue(comment1.text.startsWith("[Exclude Request]"))

        val comment2 = collabRepo.addExpenseComment(
            expenseId = "exp_dinner",
            groupId = groupId,
            authorId = userA.id,
            text = "I paid this personally"
        )
        assertTrue(comment2.isDisputeOrClarification)
        assertEquals("Paid Personally", comment2.disputeTag)

        val comments = collabRepo.getExpenseComments("exp_dinner")
        assertEquals(2, comments.size)
    }

    // -------------------------------------------------------------
    // 4. Evidence Attachments Tests
    // -------------------------------------------------------------

    @Test
    fun testEvidenceAttachmentLifecycle() = runBlocking {
        val attachment = EvidenceAttachment(
            id = "att_1",
            groupId = groupId,
            expenseId = "exp_hotel",
            localUri = "file:///storage/emulated/0/receipt.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 204800L,
            fileName = "receipt.jpg"
        )

        attachmentRepo.saveAttachment(attachment)

        val list = attachmentRepo.getAttachmentsForGroup(groupId)
        assertEquals(1, list.size)
        assertEquals("att_1", list[0].id)
        assertEquals(204800L, list[0].sizeBytes)

        val fetched = attachmentRepo.getAttachmentById("att_1")
        assertNotNull(fetched)
        assertEquals("receipt.jpg", fetched!!.fileName)

        attachmentRepo.deleteAttachment("att_1")
        val afterDelete = attachmentRepo.getAttachmentById("att_1")
        assertEquals(null, afterDelete)
    }

    // -------------------------------------------------------------
    // 5. Scoped Search & Filtering Tests
    // -------------------------------------------------------------

    @Test
    fun testSearchEngineFiltersAndSorting() {
        val exp1 = Expense(
            id = "e1",
            groupId = groupId,
            description = "Seafood Dinner at Fisherman's Wharf",
            totalAmount = Money(350000L, inr), // ₹3,500
            payers = listOf(ExpensePayer(userA.id, Money(350000L, inr))),
            participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id)),
            category = ExpenseCategory.FOOD,
            timestamp = 1000L
        )

        val exp2 = Expense(
            id = "e2",
            groupId = groupId,
            description = "Airport Taxi",
            totalAmount = Money(120000L, inr), // ₹1,200
            payers = listOf(ExpensePayer(userB.id, Money(120000L, inr))),
            participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id), ExpenseParticipant(userC.id)),
            category = ExpenseCategory.TRANSPORT,
            timestamp = 2000L
        )

        val payment = SettlementPayment(
            id = "p1",
            groupId = groupId,
            fromMemberId = userB.id,
            toMemberId = userA.id,
            amount = Money(150000L, inr), // ₹1,500
            timestamp = 3000L,
            note = "Settling taxi"
        )

        val allExpenses = listOf(exp1, exp2)
        val allPayments = listOf(payment)

        // Query by text "Taxi"
        val queryTaxi = SearchEngine.executeSearch(
            filter = SearchFilter(query = "taxi"),
            expenses = allExpenses,
            payments = allPayments,
            members = members
        )
        // Matches expense 2 and payment note "taxi"
        assertEquals(2, queryTaxi.size)

        // Filter by category FOOD
        val foodResults = SearchEngine.executeSearch(
            filter = SearchFilter(category = ExpenseCategory.FOOD),
            expenses = allExpenses,
            payments = allPayments,
            members = members
        )
        assertEquals(1, foodResults.size)
        assertEquals("Seafood Dinner at Fisherman's Wharf", foodResults[0].title)

        // Sort by amount descending
        val amountSorted = SearchEngine.executeSearch(
            filter = SearchFilter(sortBy = SearchSortBy.AMOUNT_DESC),
            expenses = allExpenses,
            payments = allPayments,
            members = members
        )
        assertEquals(3, amountSorted.size)
        assertEquals("Seafood Dinner at Fisherman's Wharf", amountSorted[0].title) // ₹3,500
    }

    // -------------------------------------------------------------
    // 6. Export Engine Tests (CSV & Text)
    // -------------------------------------------------------------

    @Test
    fun testExportEngineCsvAndTextGeneration() {
        val exp = Expense(
            id = "e_exp",
            groupId = groupId,
            description = "Resort Stay",
            totalAmount = Money(1000000L, inr), // ₹10,000
            payers = listOf(ExpensePayer(userA.id, Money(1000000L, inr))),
            participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id)),
            category = ExpenseCategory.ACCOMMODATION,
            timestamp = 1000L
        )

        val pay = SettlementPayment(
            id = "p_exp",
            groupId = groupId,
            fromMemberId = userB.id,
            toMemberId = userA.id,
            amount = Money(500000L, inr), // ₹5,000
            timestamp = 2000L
        )

        // CSV Export
        val csvData = ExportEngine.generateExport(
            groupName = "Goa Trip",
            scope = ExportScope.FULL_LEDGER,
            format = ExportFormat.CSV,
            expenses = listOf(exp),
            payments = listOf(pay),
            members = members
        )
        assertEquals("text/csv", csvData.mimeType)
        assertTrue(csvData.fileName.endsWith(".csv"))
        assertTrue(csvData.content.contains("Type,Date,Description,Category,Paid By,Amount"))
        assertTrue(csvData.content.contains("\"Resort Stay\""))
        assertTrue(csvData.content.contains("10000.00"))
        assertEquals(2, csvData.recordCount)

        // Text Summary Export
        val txtData = ExportEngine.generateExport(
            groupName = "Goa Trip",
            scope = ExportScope.FULL_LEDGER,
            format = ExportFormat.TEXT_SUMMARY,
            expenses = listOf(exp),
            payments = listOf(pay),
            members = members
        )
        assertEquals("text/plain", txtData.mimeType)
        assertTrue(txtData.content.contains("FairShare Group Summary: Goa Trip"))
        assertTrue(txtData.content.contains("Resort Stay"))
    }

    // -------------------------------------------------------------
    // 7. Share Engine Tests
    // -------------------------------------------------------------

    @Test
    fun testShareEngineFormatting() {
        val exp = Expense(
            id = "e_share",
            groupId = groupId,
            description = "Dinner",
            totalAmount = Money(150000L, inr), // ₹1,500
            payers = listOf(ExpensePayer(userA.id, Money(150000L, inr))),
            participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id), ExpenseParticipant(userC.id)),
            splitMethod = SplitMethod.EQUAL
        )

        val shareText = ShareEngine.formatExpenseShare(exp, members)
        assertTrue(shareText.contains("FairShare Expense Details:"))
        assertTrue(shareText.contains("Dinner"))
        assertTrue(shareText.contains("Amount: ₹1,500"))
        assertTrue(shareText.contains("Paid by: Aarav"))
        assertTrue(shareText.contains("Aarav: ₹500"))
        assertTrue(shareText.contains("Bhavna: ₹500"))
        assertTrue(shareText.contains("Chirag: ₹500"))
        assertTrue(shareText.contains("Shared via FairShare"))
    }
}
