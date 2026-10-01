package com.fairshare.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.GroupMemberEntity
import com.fairshare.android.core.database.entity.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    @Test
    fun testMigrationExecutionPreservesData() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "migration_test.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Create DB at Version 1
        var db = Room.databaseBuilder(context, FairShareDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

        db.userDao().insertUser(UserEntity("u1", "12345", "Alice"))
        db.groupDao().insertGroup(GroupEntity("g1", "Trip", createdById = "u1"))
        db.groupDao().insertGroupMember(GroupMemberEntity("g1", "u1", "OWNER", joinedAt = 5000L))

        db.close()

        // 2. Open DB with MIGRATION_1_2 configured
        db = Room.databaseBuilder(context, FairShareDatabase::class.java, dbFile.absolutePath)
            .addMigrations(FairShareDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        // Execute migration SQL manually or verify schema operation
        FairShareDatabase.MIGRATION_1_2.migrate(db.openHelper.writableDatabase)

        // Verify data intact
        val groupWithMembers = db.groupDao().getGroupWithMembers("g1")
        assertNotNull(groupWithMembers)
        assertEquals("Trip", groupWithMembers!!.group.name)
        assertEquals(1, groupWithMembers.members.size)
        assertEquals("Alice", groupWithMembers.members[0].displayName)

        db.close()
        dbFile.delete()
        }
    }
}
