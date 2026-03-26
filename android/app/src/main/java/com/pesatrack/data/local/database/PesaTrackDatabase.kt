package com.pesatrack.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.CategoryRuleDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.IncomeDao
import com.pesatrack.data.local.database.dao.RecipientCategoryMappingDao
import com.pesatrack.data.local.database.entities.BudgetEntity
import com.pesatrack.data.local.database.entities.CategoryEntity
import com.pesatrack.data.local.database.entities.CategoryRuleEntity
import com.pesatrack.data.local.database.entities.ExpenseEntity
import com.pesatrack.data.local.database.entities.IncomeEntity
import com.pesatrack.data.local.database.entities.RecipientCategoryMappingEntity

/**
 * PesaTrack Room Database
 *
 * Version history:
 * - v2→v3: Moved Seed category from Shopping to Faith & Giving
 * - v3→v4: Added rawSms column to expenses, added recipient_category_mapping table
 * - v4→v5: Changed recipient_category_mapping PK from recipientKey to (recipientKey, categoryId)
 *           to support multi-category mappings per recipient
 * - v5→v6: Category restructure — alphabetical groups, renames, merges, additions.
 *           All category IDs changed; expenses & mappings updated via ID migration.
 * - v6→v7: Added isExcluded column to expenses for pass-through money filtering.
 * - v7→v8: Investment deep-dive — moved investment sub-categories from Financial (6)
 *           to new Investment & Savings group (18). Remapped IDs 602→1811, 605→1805,
 *           607→1806, 610→1809, 611→1810, 612→1812.
 * - v8→v9: Added budgets table for category-based budget tracking (M7).
 * - v9→v10: Added category_rules table for user-defined auto-categorization rules.
 * - v10→v11: Converted Beekeeping group + sub-categories from default to custom
 *            (isDefault = false). Beekeeping is no longer shipped as a built-in category;
 * - v11→v12: Sub-category budgets — renamed budgets.categoryGroupId → categoryId,
 *            added isGroupBudget column (default true for existing rows), rebuilt indices.
 *            existing users keep theirs as an editable/deletable custom category.
 * - v12→v13: Added income table for manual monthly income tracking.
 *            Used by Budget screen to compare total budgeted vs income.
 * - v13→v14: Added customStartDate/customEndDate columns to budgets table
 *            for CUSTOM period support (user-defined date ranges).
 */
@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        RecipientCategoryMappingEntity::class,
        BudgetEntity::class,
        CategoryRuleEntity::class,
        IncomeEntity::class
    ],
    version = 14,
    exportSchema = true
)
abstract class PesaTrackDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recipientCategoryMappingDao(): RecipientCategoryMappingDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun incomeDao(): IncomeDao

    companion object {
        /**
         * Migration from version 2 to 3:
         * Move "Seed" category from Shopping (parentId=5) to Faith & Giving (parentId=9)
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Delete old Seed entry under Shopping
                database.execSQL("DELETE FROM categories WHERE id = 506 AND name = 'Seed'")
                // Insert new Seed entry under Faith & Giving
                database.execSQL(
                    """INSERT OR REPLACE INTO categories (id, name, icon, color, parentId, isGroup, isDefault, sortOrder) 
                       VALUES (905, 'Seed', 'grass', '#673AB7', 9, 0, 1, 5)"""
                )
            }
        }

        /**
         * Migration from version 3 to 4:
         * 1. Add rawSms column to expenses table
         * 2. Create recipient_category_mapping table (single-key PK)
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Add rawSms column to expenses (nullable TEXT, default NULL)
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN rawSms TEXT DEFAULT NULL"
                )

                // 2. Create recipient_category_mapping table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recipient_category_mapping (
                        recipientKey TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        recipientDisplayName TEXT DEFAULT NULL,
                        timesUsed INTEGER NOT NULL DEFAULT 1,
                        lastUsed INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (recipientKey),
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)

                // 3. Create index on categoryId for the mapping table
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipient_category_mapping_categoryId ON recipient_category_mapping(categoryId)"
                )
            }
        }

        /**
         * Migration from version 4 to 5:
         * Change recipient_category_mapping PK from (recipientKey) to (recipientKey, categoryId)
         * to support multi-category mappings per recipient.
         *
         * SQLite doesn't support ALTER TABLE to change primary key, so we:
         * 1. Create new table with composite PK
         * 2. Copy existing data
         * 3. Drop old table
         * 4. Rename new table
         * 5. Recreate indexes
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create new table with composite primary key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recipient_category_mapping_new (
                        recipientKey TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        recipientDisplayName TEXT DEFAULT NULL,
                        timesUsed INTEGER NOT NULL DEFAULT 1,
                        lastUsed INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (recipientKey, categoryId),
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)

                // 2. Copy existing data from old table
                database.execSQL("""
                    INSERT OR IGNORE INTO recipient_category_mapping_new 
                    (recipientKey, categoryId, recipientDisplayName, timesUsed, lastUsed)
                    SELECT recipientKey, categoryId, recipientDisplayName, timesUsed, lastUsed
                    FROM recipient_category_mapping
                """)

                // 3. Drop old table
                database.execSQL("DROP TABLE IF EXISTS recipient_category_mapping")

                // 4. Rename new table
                database.execSQL("ALTER TABLE recipient_category_mapping_new RENAME TO recipient_category_mapping")

                // 5. Recreate indexes
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipient_category_mapping_categoryId ON recipient_category_mapping(categoryId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipient_category_mapping_recipientKey ON recipient_category_mapping(recipientKey)"
                )
            }
        }

        /**
         * Migration from version 5 to 6:
         * Category restructure — alphabetical groups, renames, merges, additions.
         *
         * Strategy:
         * 1. Create temp table mapping old category IDs → new category IDs
         * 2. Disable FK constraints
         * 3. Update categoryId in expenses table
         * 4. Recreate recipient_category_mapping with updated categoryIds
         * 5. Delete ALL old default categories (groups + subcategories)
         * 6. Insert ALL new default categories
         * 7. Re-enable FK constraints
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ── Step 1: Create temp mapping table ──
                database.execSQL("CREATE TEMP TABLE IF NOT EXISTS _id_map (old_id INTEGER PRIMARY KEY, new_id INTEGER NOT NULL)")

                // Groups: old → new
                val groupMap = mapOf(
                    1 to 17, 2 to 10, 3 to 7, 4 to 16, 5 to 15, 6 to 13,
                    7 to 9, 8 to 6, 9 to 5, 10 to 2, 11 to 1, 12 to 14,
                    13 to 4, 14 to 3, 15 to 8, 16 to 11, 17 to 12
                )

                // Subcategories: old → new (includes merges)
                val subcatMap = mapOf(
                    // Vehicle (old 1xx → new 17xx)
                    101 to 1705, 102 to 1704, 103 to 1706, 104 to 1707,
                    105 to 1708, 106 to 1709, 107 to 1702, 108 to 1708,
                    109 to 1701, 110 to 1710, 111 to 1703, 112 to 1712,
                    113 to 1713, 114 to 1711,
                    // Home & Utilities (old 2xx → new 10xx)
                    201 to 1009, 202 to 1002, 203 to 1012, 204 to 1004,
                    205 to 1007, 206 to 1001, 207 to 1006, 208 to 1005,
                    209 to 1006, 210 to 1010, 211 to 1008, 212 to 1011,
                    213 to 1010, 214 to 1010, 215 to 1003,
                    // Food & Dining (old 3xx → new 7xx)
                    301 to 702, 302 to 702, 303 to 703,
                    // Transport (old 4xx → new 16xx)
                    401 to 1604, 402 to 1608, 403 to 1602, 404 to 1605,
                    405 to 1607, 406 to 1606, 407 to 1601,
                    // Shopping (old 5xx → new 15xx)
                    501 to 1505, 502 to 1503, 503 to 1508, 504 to 1502,
                    505 to 1501, 507 to 1509,
                    // Personal Care (old 6xx → new 13xx)
                    601 to 1301, 602 to 1301, 603 to 1303,
                    // Health (old 7xx → new 9xx)
                    701 to 904, 702 to 906, 703 to 901, 704 to 905,
                    705 to 902, 706 to 903,
                    // Financial (old 8xx → new 6xx)
                    801 to 611, 802 to 602, 803 to 604, 804 to 603,
                    805 to 612, 806 to 612, 807 to 610, 808 to 607,
                    809 to 605, 810 to 601, 811 to 606, 812 to 601,
                    813 to 608, 814 to 609,
                    // Faith & Giving (old 9xx → new 5xx)
                    901 to 506, 902 to 504, 903 to 503, 904 to 501,
                    905 to 505,
                    // Digital & Tech (old 10xx → new 2xx)
                    1001 to 202, 1002 to 205, 1003 to 207, 1004 to 207,
                    1005 to 208, 1006 to 208, 1007 to 201, 1008 to 209,
                    1009 to 204, 1010 to 206, 1011 to 211, 1012 to 210,
                    // Beekeeping (old 11xx → new 1xx)
                    1101 to 104, 1102 to 103, 1103 to 101, 1104 to 108,
                    1105 to 106, 1106 to 107, 1107 to 102, 1108 to 105,
                    1109 to 109, 1110 to 110,
                    // Pets (old 12xx → new 14xx)
                    1201 to 1404, 1202 to 1404, 1203 to 1401, 1204 to 1403,
                    1205 to 1402,
                    // Entertainment (old 13xx → new 4xx)
                    1301 to 405, 1302 to 404, 1303 to 402, 1304 to 401,
                    // Education (old 14xx → new 3xx)
                    1401 to 303, 1402 to 301, 1403 to 302,
                    // Government & Legal (old 15xx → new 8xx)
                    1501 to 802, 1502 to 803, 1503 to 803, 1504 to 813,
                    1505 to 811, 1506 to 806, 1507 to 801, 1508 to 807,
                    1509 to 810, 1510 to 804, 1511 to 809, 1512 to 812,
                    // Life Events (old 16xx → new 11xx)
                    1601 to 1102, 1602 to 1108, 1603 to 1103, 1604 to 1101,
                    1605 to 1105, 1606 to 1104,
                    // Miscellaneous (old 17xx → new)
                    1701 to 1201, 1702 to 701  // Water → Drinking Water in Food
                )

                // Insert all mappings
                val allMappings = groupMap + subcatMap
                for ((oldId, newId) in allMappings) {
                    database.execSQL("INSERT INTO _id_map (old_id, new_id) VALUES ($oldId, $newId)")
                }

                // ── Step 2: Disable FK constraints ──
                database.execSQL("PRAGMA foreign_keys = OFF")

                // ── Step 3: Update expenses.categoryId ──
                database.execSQL("""
                    UPDATE expenses SET categoryId = (
                        SELECT new_id FROM _id_map WHERE old_id = expenses.categoryId
                    ) WHERE categoryId IN (SELECT old_id FROM _id_map)
                """)

                // ── Step 4: Recreate recipient_category_mapping with updated categoryIds ──
                // Since categoryId is part of the composite PK, we need to rebuild the table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recipient_category_mapping_v6 (
                        recipientKey TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        recipientDisplayName TEXT DEFAULT NULL,
                        timesUsed INTEGER NOT NULL DEFAULT 1,
                        lastUsed INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (recipientKey, categoryId),
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                // Copy data with updated categoryIds; aggregate timesUsed for merges
                database.execSQL("""
                    INSERT OR REPLACE INTO recipient_category_mapping_v6
                    (recipientKey, categoryId, recipientDisplayName, timesUsed, lastUsed)
                    SELECT
                        m.recipientKey,
                        COALESCE(im.new_id, m.categoryId),
                        m.recipientDisplayName,
                        SUM(m.timesUsed),
                        MAX(m.lastUsed)
                    FROM recipient_category_mapping m
                    LEFT JOIN _id_map im ON im.old_id = m.categoryId
                    GROUP BY m.recipientKey, COALESCE(im.new_id, m.categoryId)
                """)
                database.execSQL("DROP TABLE IF EXISTS recipient_category_mapping")
                database.execSQL("ALTER TABLE recipient_category_mapping_v6 RENAME TO recipient_category_mapping")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recipient_category_mapping_categoryId ON recipient_category_mapping(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recipient_category_mapping_recipientKey ON recipient_category_mapping(recipientKey)")

                // ── Step 5: Delete ALL old default categories ──
                database.execSQL("DELETE FROM categories WHERE isDefault = 1")

                // ── Step 6: Insert ALL new default categories ──
                // Groups (17 alphabetical groups)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1,'Beekeeping','hive','#FFC107',NULL,1,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (2,'Digital & Tech','devices','#607D8B',NULL,1,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (3,'Education','school','#3F51B5',NULL,1,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (4,'Entertainment','movie','#E91E63',NULL,1,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (5,'Faith & Giving','volunteer_activism','#673AB7',NULL,1,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (6,'Financial','account_balance','#795548',NULL,1,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (7,'Food & Dining','restaurant','#FF5722',NULL,1,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (8,'Government & Legal','gavel','#455A64',NULL,1,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (9,'Health','local_hospital','#F44336',NULL,1,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (10,'Home & Utilities','home','#4CAF50',NULL,1,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (11,'Life Events','cake','#FF9800',NULL,1,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (12,'Miscellaneous','more_horiz','#9E9E9E',NULL,1,1,12)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (13,'Personal Care','face','#00BCD4',NULL,1,1,13)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (14,'Pets','pets','#8BC34A',NULL,1,1,14)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (15,'Shopping','shopping_bag','#E91E63',NULL,1,1,15)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (16,'Transport & Travel','commute','#9C27B0',NULL,1,1,16)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (17,'Vehicle','directions_car','#2196F3',NULL,1,1,17)")

                // Beekeeping subcategories (1xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (101,'Bee Equipment','construction','#FFC107',1,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (102,'Bee Feed','restaurant','#FFC107',1,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (103,'Bee Hive Tables','table_bar','#FFC107',1,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (104,'Bee Hives','hive','#FFC107',1,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (105,'Bee Medicine','medication','#FFC107',1,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (106,'Bee Swarmer','scatter_plot','#FFC107',1,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (107,'Bee Wax','hexagon','#FFC107',1,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (108,'Bees','emoji_nature','#FFC107',1,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (109,'Honey Harvesting','agriculture','#FFC107',1,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (110,'Honey Packaging','inventory_2','#FFC107',1,0,1,10)")

                // Digital & Tech subcategories (2xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (201,'AI Subscriptions','smart_toy','#607D8B',2,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (202,'Airtime','sim_card','#607D8B',2,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (203,'App Subscriptions','apps','#607D8B',2,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (204,'Cloud Storage','cloud_upload','#607D8B',2,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (205,'Data Bundles','signal_cellular_alt','#607D8B',2,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (206,'Device Repairs','laptop','#607D8B',2,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (207,'Domain','language','#607D8B',2,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (208,'Hosting','cloud','#607D8B',2,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (209,'Software License','verified','#607D8B',2,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (210,'Streaming','subscriptions','#607D8B',2,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (211,'VPN','vpn_key','#607D8B',2,0,1,11)")

                // Education subcategories (3xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (301,'Certifications','workspace_premium','#3F51B5',3,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (302,'Conferences','groups','#3F51B5',3,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (303,'Courses/Training','school','#3F51B5',3,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (304,'School Fees','account_balance','#3F51B5',3,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (305,'Stationery','edit','#3F51B5',3,0,1,5)")

                // Entertainment subcategories (4xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (401,'Events','event','#E91E63',4,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (402,'Games','sports_esports','#E91E63',4,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (403,'Hobbies','palette','#E91E63',4,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (404,'Movies','theaters','#E91E63',4,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (405,'Other Entertainment','movie','#E91E63',4,0,1,5)")

                // Faith & Giving subcategories (5xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (501,'Church Program','auto_awesome','#673AB7',5,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (502,'Community Program','diversity_3','#673AB7',5,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (503,'Give','volunteer_activism','#673AB7',5,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (504,'Offering','favorite','#673AB7',5,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (505,'Seed','grass','#673AB7',5,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (506,'Tithe','church','#673AB7',5,0,1,6)")

                // Financial subcategories (6xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (601,'Bank Charges','account_balance_wallet','#795548',6,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (602,'Investments','trending_up','#795548',6,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (603,'Loan Interest','percent','#795548',6,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (604,'Loan Repayment','account_balance','#795548',6,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (605,'Money Market Fund','show_chart','#795548',6,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (606,'Mpesa Transaction Cost','phone_android','#795548',6,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (607,'NSSF','elderly','#795548',6,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (608,'Pesalink Charges','swap_horiz','#795548',6,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (609,'RTGS Charges','sync_alt','#795548',6,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (610,'SACCO','groups','#795548',6,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (611,'Savings','savings','#795548',6,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (612,'Treasury Bill','receipt_long','#795548',6,0,1,12)")

                // Food & Dining subcategories (7xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (701,'Drinking Water','water','#FF5722',7,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (702,'Eating Out','restaurant','#FF5722',7,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (703,'Groceries','shopping_cart','#FF5722',7,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (704,'Snacks/Drinks','local_cafe','#FF5722',7,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (705,'Takeaway/Delivery','delivery_dining','#FF5722',7,0,1,5)")

                // Government & Legal subcategories (8xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (801,'County Rates','apartment','#455A64',8,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (802,'Driving License Renewal','badge','#455A64',8,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (803,'Excise Duty','request_quote','#455A64',8,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (804,'Good Conduct Certificate','verified_user','#455A64',8,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (805,'Income Tax/KRA Filing','description','#455A64',8,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (806,'KRA Penalties','warning','#455A64',8,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (807,'Land Rent','terrain','#455A64',8,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (808,'NTSA','directions_car','#455A64',8,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (809,'Passport Fees','flight','#455A64',8,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (810,'SHA','health_and_safety','#455A64',8,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (811,'Tax Consultancy','support_agent','#455A64',8,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (812,'Visa Fees','approval','#455A64',8,0,1,12)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (813,'Withholding Tax','money_off','#455A64',8,0,1,13)")

                // Health subcategories (9xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (901,'Dental','dentistry','#F44336',9,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (902,'Gym/Fitness','fitness_center','#F44336',9,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (903,'Health Insurance','health_and_safety','#F44336',9,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (904,'Medical Checkup','medical_services','#F44336',9,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (905,'Optical','visibility','#F44336',9,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (906,'Pharmacy','medication','#F44336',9,0,1,6)")

                // Home & Utilities subcategories (10xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1001,'Cleaning','cleaning_services','#4CAF50',10,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1002,'Electricity','bolt','#4CAF50',10,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1003,'Gardening','yard','#4CAF50',10,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1004,'Gas','propane_tank','#4CAF50',10,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1005,'Home Appliances','microwave','#4CAF50',10,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1006,'Home Furnishing','chair','#4CAF50',10,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1007,'Home WiFi','wifi','#4CAF50',10,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1008,'Pest Control','pest_control','#4CAF50',10,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1009,'Rent','house','#4CAF50',10,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1010,'Repairs','handyman','#4CAF50',10,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1011,'Security','shield','#4CAF50',10,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1012,'Water Bill','water_drop','#4CAF50',10,0,1,12)")

                // Life Events subcategories (11xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1101,'Baby Shower','child_friendly','#FF9800',11,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1102,'Birthday Gift','cake','#FF9800',11,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1103,'Funeral Contribution','sentiment_very_dissatisfied','#FF9800',11,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1104,'Graduation Gift','school','#FF9800',11,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1105,'Harambee/Fundraiser','handshake','#FF9800',11,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1106,'Holiday Gifts','card_giftcard','#FF9800',11,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1107,'House Warming','home','#FF9800',11,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1108,'Wedding Contribution','favorite','#FF9800',11,0,1,8)")

                // Miscellaneous subcategories (12xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1201,'Miscellaneous','more_horiz','#9E9E9E',12,0,1,1)")

                // Personal Care subcategories (13xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1301,'Haircut','content_cut','#00BCD4',13,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1302,'Laundry/Dry Cleaning','local_laundry_service','#00BCD4',13,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1303,'Salon','spa','#00BCD4',13,0,1,3)")

                // Pets subcategories (14xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1401,'Pet Food','restaurant','#8BC34A',14,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1402,'Pet Grooming','content_cut','#8BC34A',14,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1403,'Pet Supplies','category','#8BC34A',14,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1404,'Vet','pets','#8BC34A',14,0,1,4)")

                // Shopping subcategories (15xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1501,'Art','palette','#E91E63',15,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1502,'Books','menu_book','#E91E63',15,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1503,'Clothing','checkroom','#E91E63',15,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1504,'Electronics','devices_other','#E91E63',15,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1505,'General Shopping','shopping_bag','#E91E63',15,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1506,'Gifts','card_giftcard','#E91E63',15,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1507,'Household Items','house','#E91E63',15,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1508,'Phone / Accessories','smartphone','#E91E63',15,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1509,'Shipping','local_shipping','#E91E63',15,0,1,9)")

                // Transport & Travel subcategories (16xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1601,'Accommodation','hotel','#9C27B0',16,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1602,'Boda Boda','two_wheeler','#9C27B0',16,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1603,'Delivery Charges','local_shipping','#9C27B0',16,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1604,'Fare','directions_bus','#9C27B0',16,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1605,'Flight','flight','#9C27B0',16,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1606,'National Park Fees','park','#9C27B0',16,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1607,'SGR Train','train','#9C27B0',16,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1608,'Uber/Bolt','local_taxi','#9C27B0',16,0,1,8)")

                // Vehicle subcategories (17xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1701,'Car Accessories','settings','#2196F3',17,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1702,'Car Battery','battery_charging_full','#2196F3',17,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1703,'Car Hire','car_rental','#2196F3',17,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1704,'Car Insurance','security','#2196F3',17,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1705,'Car Payment','payments','#2196F3',17,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1706,'Car Registration','badge','#2196F3',17,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1707,'Car Repairs','build','#2196F3',17,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1708,'Car Service','car_repair','#2196F3',17,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1709,'Car Tyres','tire_repair','#2196F3',17,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1710,'Car Wash','local_car_wash','#2196F3',17,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1711,'Expressway','toll','#2196F3',17,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1712,'Fuel','local_gas_station','#2196F3',17,0,1,12)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1713,'Parking','local_parking','#2196F3',17,0,1,13)")

                // ── Step 7: Clean up and re-enable FK constraints ──
                database.execSQL("DROP TABLE IF EXISTS _id_map")
                database.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        /**
         * Migration from version 6 to 7:
         * Add isExcluded column to expenses table for pass-through money filtering.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN isExcluded INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Migration from version 7 to 8:
         * Investment deep-dive — move investment sub-categories from Financial (group 6)
         * to new Investment & Savings group (18).
         *
         * ID remapping:
         *   602 (Investments)       → 1811 (Stocks/Shares)
         *   605 (Money Market Fund) → 1805 (Money Market Fund)
         *   607 (NSSF)              → 1806 (NSSF)
         *   610 (SACCO)             → 1809 (SACCO)
         *   611 (Savings)           → 1810 (Savings)
         *   612 (Treasury Bill)     → 1812 (Treasury Bill/Bond)
         *
         * Strategy:
         * 1. Create temp ID mapping table
         * 2. Disable FK constraints
         * 3. Update expenses.categoryId
         * 4. Recreate recipient_category_mapping with updated IDs
         * 5. Delete ALL old default categories
         * 6. Insert ALL new default categories (18 groups + subcategories)
         * 7. Re-enable FK constraints
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ── Step 1: Create temp mapping table ──
                database.execSQL("CREATE TEMP TABLE IF NOT EXISTS _id_map (old_id INTEGER PRIMARY KEY, new_id INTEGER NOT NULL)")

                // Investment sub-categories: old Financial → new Investment & Savings
                val idMap = mapOf(
                    602 to 1811,  // Investments → Stocks/Shares
                    605 to 1805,  // Money Market Fund → Money Market Fund
                    607 to 1806,  // NSSF → NSSF
                    610 to 1809,  // SACCO → SACCO
                    611 to 1810,  // Savings → Savings
                    612 to 1812   // Treasury Bill → Treasury Bill/Bond
                )
                for ((oldId, newId) in idMap) {
                    database.execSQL("INSERT INTO _id_map (old_id, new_id) VALUES ($oldId, $newId)")
                }

                // ── Step 2: Disable FK constraints ──
                database.execSQL("PRAGMA foreign_keys = OFF")

                // ── Step 3: Update expenses.categoryId ──
                database.execSQL("""
                    UPDATE expenses SET categoryId = (
                        SELECT new_id FROM _id_map WHERE old_id = expenses.categoryId
                    ) WHERE categoryId IN (SELECT old_id FROM _id_map)
                """)

                // ── Step 4: Recreate recipient_category_mapping with updated IDs ──
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recipient_category_mapping_v8 (
                        recipientKey TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        recipientDisplayName TEXT DEFAULT NULL,
                        timesUsed INTEGER NOT NULL DEFAULT 1,
                        lastUsed INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (recipientKey, categoryId),
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT OR REPLACE INTO recipient_category_mapping_v8
                    (recipientKey, categoryId, recipientDisplayName, timesUsed, lastUsed)
                    SELECT
                        m.recipientKey,
                        COALESCE(im.new_id, m.categoryId),
                        m.recipientDisplayName,
                        SUM(m.timesUsed),
                        MAX(m.lastUsed)
                    FROM recipient_category_mapping m
                    LEFT JOIN _id_map im ON im.old_id = m.categoryId
                    GROUP BY m.recipientKey, COALESCE(im.new_id, m.categoryId)
                """)
                database.execSQL("DROP TABLE IF EXISTS recipient_category_mapping")
                database.execSQL("ALTER TABLE recipient_category_mapping_v8 RENAME TO recipient_category_mapping")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recipient_category_mapping_categoryId ON recipient_category_mapping(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recipient_category_mapping_recipientKey ON recipient_category_mapping(recipientKey)")

                // ── Step 5: Delete ALL old default categories ──
                database.execSQL("DELETE FROM categories WHERE isDefault = 1")

                // ── Step 6: Insert ALL new default categories (18 groups) ──

                // Groups (18 alphabetical groups — Investment & Savings added between Home & Life Events)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1,'Beekeeping','hive','#FFC107',NULL,1,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (2,'Digital & Tech','devices','#607D8B',NULL,1,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (3,'Education','school','#3F51B5',NULL,1,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (4,'Entertainment','movie','#E91E63',NULL,1,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (5,'Faith & Giving','volunteer_activism','#673AB7',NULL,1,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (6,'Financial','account_balance','#795548',NULL,1,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (7,'Food & Dining','restaurant','#FF5722',NULL,1,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (8,'Government & Legal','gavel','#455A64',NULL,1,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (9,'Health','local_hospital','#F44336',NULL,1,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (10,'Home & Utilities','home','#4CAF50',NULL,1,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (18,'Investment & Savings','trending_up','#006064',NULL,1,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (11,'Life Events','cake','#FF9800',NULL,1,1,12)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (12,'Miscellaneous','more_horiz','#9E9E9E',NULL,1,1,13)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (13,'Personal Care','face','#00BCD4',NULL,1,1,14)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (14,'Pets','pets','#8BC34A',NULL,1,1,15)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (15,'Shopping','shopping_bag','#E91E63',NULL,1,1,16)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (16,'Transport & Travel','commute','#9C27B0',NULL,1,1,17)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (17,'Vehicle','directions_car','#2196F3',NULL,1,1,18)")

                // Beekeeping subcategories (1xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (101,'Bee Equipment','construction','#FFC107',1,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (102,'Bee Feed','restaurant','#FFC107',1,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (103,'Bee Hive Tables','table_bar','#FFC107',1,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (104,'Bee Hives','hive','#FFC107',1,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (105,'Bee Medicine','medication','#FFC107',1,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (106,'Bee Swarmer','scatter_plot','#FFC107',1,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (107,'Bee Wax','hexagon','#FFC107',1,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (108,'Bees','emoji_nature','#FFC107',1,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (109,'Honey Harvesting','agriculture','#FFC107',1,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (110,'Honey Packaging','inventory_2','#FFC107',1,0,1,10)")

                // Digital & Tech subcategories (2xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (201,'AI Subscriptions','smart_toy','#607D8B',2,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (202,'Airtime','sim_card','#607D8B',2,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (203,'App Subscriptions','apps','#607D8B',2,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (204,'Cloud Storage','cloud_upload','#607D8B',2,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (205,'Data Bundles','signal_cellular_alt','#607D8B',2,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (206,'Device Repairs','laptop','#607D8B',2,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (207,'Domain','language','#607D8B',2,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (208,'Hosting','cloud','#607D8B',2,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (209,'Software License','verified','#607D8B',2,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (210,'Streaming','subscriptions','#607D8B',2,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (211,'VPN','vpn_key','#607D8B',2,0,1,11)")

                // Education subcategories (3xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (301,'Certifications','workspace_premium','#3F51B5',3,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (302,'Conferences','groups','#3F51B5',3,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (303,'Courses/Training','school','#3F51B5',3,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (304,'School Fees','account_balance','#3F51B5',3,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (305,'Stationery','edit','#3F51B5',3,0,1,5)")

                // Entertainment subcategories (4xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (401,'Events','event','#E91E63',4,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (402,'Games','sports_esports','#E91E63',4,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (403,'Hobbies','palette','#E91E63',4,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (404,'Movies','theaters','#E91E63',4,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (405,'Other Entertainment','movie','#E91E63',4,0,1,5)")

                // Faith & Giving subcategories (5xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (501,'Church Program','auto_awesome','#673AB7',5,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (502,'Community Program','diversity_3','#673AB7',5,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (503,'Give','volunteer_activism','#673AB7',5,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (504,'Offering','favorite','#673AB7',5,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (505,'Seed','grass','#673AB7',5,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (506,'Tithe','church','#673AB7',5,0,1,6)")

                // Financial subcategories (6xx — expense-only, investments moved to 18xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (601,'Bank Charges','account_balance_wallet','#795548',6,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (603,'Loan Interest','percent','#795548',6,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (604,'Loan Repayment','account_balance','#795548',6,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (606,'Mpesa Transaction Cost','phone_android','#795548',6,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (608,'Pesalink Charges','swap_horiz','#795548',6,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (609,'RTGS Charges','sync_alt','#795548',6,0,1,6)")

                // Food & Dining subcategories (7xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (701,'Drinking Water','water','#FF5722',7,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (702,'Eating Out','restaurant','#FF5722',7,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (703,'Groceries','shopping_cart','#FF5722',7,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (704,'Snacks/Drinks','local_cafe','#FF5722',7,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (705,'Takeaway/Delivery','delivery_dining','#FF5722',7,0,1,5)")

                // Government & Legal subcategories (8xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (801,'County Rates','apartment','#455A64',8,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (802,'Driving License Renewal','badge','#455A64',8,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (803,'Excise Duty','request_quote','#455A64',8,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (804,'Good Conduct Certificate','verified_user','#455A64',8,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (805,'Income Tax/KRA Filing','description','#455A64',8,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (806,'KRA Penalties','warning','#455A64',8,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (807,'Land Rent','terrain','#455A64',8,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (808,'NTSA','directions_car','#455A64',8,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (809,'Passport Fees','flight','#455A64',8,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (810,'SHA','health_and_safety','#455A64',8,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (811,'Tax Consultancy','support_agent','#455A64',8,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (812,'Visa Fees','approval','#455A64',8,0,1,12)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (813,'Withholding Tax','money_off','#455A64',8,0,1,13)")

                // Health subcategories (9xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (901,'Dental','dentistry','#F44336',9,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (902,'Gym/Fitness','fitness_center','#F44336',9,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (903,'Health Insurance','health_and_safety','#F44336',9,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (904,'Medical Checkup','medical_services','#F44336',9,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (905,'Optical','visibility','#F44336',9,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (906,'Pharmacy','medication','#F44336',9,0,1,6)")

                // Home & Utilities subcategories (10xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1001,'Cleaning','cleaning_services','#4CAF50',10,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1002,'Electricity','bolt','#4CAF50',10,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1003,'Gardening','yard','#4CAF50',10,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1004,'Gas','propane_tank','#4CAF50',10,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1005,'Home Appliances','microwave','#4CAF50',10,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1006,'Home Furnishing','chair','#4CAF50',10,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1007,'Home WiFi','wifi','#4CAF50',10,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1008,'Pest Control','pest_control','#4CAF50',10,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1009,'Rent','house','#4CAF50',10,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1010,'Repairs','handyman','#4CAF50',10,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1011,'Security','shield','#4CAF50',10,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1012,'Water Bill','water_drop','#4CAF50',10,0,1,12)")

                // Investment & Savings subcategories (18xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1801,'Chama Contributions','groups','#006064',18,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1802,'Crypto','currency_bitcoin','#006064',18,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1803,'Fixed Deposit','lock','#006064',18,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1804,'Insurance (Investment)','shield','#006064',18,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1805,'Money Market Fund','show_chart','#006064',18,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1806,'NSSF','elderly','#006064',18,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1807,'Pension/Retirement','account_balance','#006064',18,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1808,'Real Estate','apartment','#006064',18,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1809,'SACCO','handshake','#006064',18,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1810,'Savings','savings','#006064',18,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1811,'Stocks/Shares','candlestick_chart','#006064',18,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1812,'Treasury Bill/Bond','receipt_long','#006064',18,0,1,12)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1813,'Unit Trusts/Mutual Funds','pie_chart','#006064',18,0,1,13)")

                // Life Events subcategories (11xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1101,'Baby Shower','child_friendly','#FF9800',11,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1102,'Birthday Gift','cake','#FF9800',11,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1103,'Funeral Contribution','sentiment_very_dissatisfied','#FF9800',11,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1104,'Graduation Gift','school','#FF9800',11,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1105,'Harambee/Fundraiser','handshake','#FF9800',11,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1106,'Holiday Gifts','card_giftcard','#FF9800',11,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1107,'House Warming','home','#FF9800',11,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1108,'Wedding Contribution','favorite','#FF9800',11,0,1,8)")

                // Miscellaneous subcategories (12xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1201,'Miscellaneous','more_horiz','#9E9E9E',12,0,1,1)")

                // Personal Care subcategories (13xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1301,'Haircut','content_cut','#00BCD4',13,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1302,'Laundry/Dry Cleaning','local_laundry_service','#00BCD4',13,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1303,'Salon','spa','#00BCD4',13,0,1,3)")

                // Pets subcategories (14xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1401,'Pet Food','restaurant','#8BC34A',14,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1402,'Pet Grooming','content_cut','#8BC34A',14,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1403,'Pet Supplies','category','#8BC34A',14,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1404,'Vet','pets','#8BC34A',14,0,1,4)")

                // Shopping subcategories (15xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1501,'Art','palette','#E91E63',15,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1502,'Books','menu_book','#E91E63',15,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1503,'Clothing','checkroom','#E91E63',15,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1504,'Electronics','devices_other','#E91E63',15,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1505,'General Shopping','shopping_bag','#E91E63',15,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1506,'Gifts','card_giftcard','#E91E63',15,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1507,'Household Items','house','#E91E63',15,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1508,'Phone / Accessories','smartphone','#E91E63',15,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1509,'Shipping','local_shipping','#E91E63',15,0,1,9)")

                // Transport & Travel subcategories (16xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1601,'Accommodation','hotel','#9C27B0',16,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1602,'Boda Boda','two_wheeler','#9C27B0',16,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1603,'Delivery Charges','local_shipping','#9C27B0',16,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1604,'Fare','directions_bus','#9C27B0',16,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1605,'Flight','flight','#9C27B0',16,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1606,'National Park Fees','park','#9C27B0',16,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1607,'SGR Train','train','#9C27B0',16,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1608,'Uber/Bolt','local_taxi','#9C27B0',16,0,1,8)")

                // Vehicle subcategories (17xx)
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1701,'Car Accessories','settings','#2196F3',17,0,1,1)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1702,'Car Battery','battery_charging_full','#2196F3',17,0,1,2)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1703,'Car Hire','car_rental','#2196F3',17,0,1,3)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1704,'Car Insurance','security','#2196F3',17,0,1,4)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1705,'Car Payment','payments','#2196F3',17,0,1,5)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1706,'Car Registration','badge','#2196F3',17,0,1,6)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1707,'Car Repairs','build','#2196F3',17,0,1,7)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1708,'Car Service','car_repair','#2196F3',17,0,1,8)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1709,'Car Tyres','tire_repair','#2196F3',17,0,1,9)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1710,'Car Wash','local_car_wash','#2196F3',17,0,1,10)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1711,'Expressway','toll','#2196F3',17,0,1,11)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1712,'Fuel','local_gas_station','#2196F3',17,0,1,12)")
                database.execSQL("INSERT INTO categories (id,name,icon,color,parentId,isGroup,isDefault,sortOrder) VALUES (1713,'Parking','local_parking','#2196F3',17,0,1,13)")

                // ── Step 7: Clean up and re-enable FK constraints ──
                database.execSQL("DROP TABLE IF EXISTS _id_map")
                database.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        /**
         * Migration from version 8 to 9:
         * Add budgets table for category-based budget tracking (M7).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryGroupId INTEGER DEFAULT NULL,
                        amount REAL NOT NULL,
                        period TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (categoryGroupId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_categoryGroupId ON budgets(categoryGroupId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_isActive ON budgets(isActive)")
            }
        }

        /**
         * Migration from version 9 to 10:
         * Add category_rules table for user-defined auto-categorization rules.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pattern TEXT NOT NULL,
                        matchType TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_category_rules_categoryId ON category_rules(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_category_rules_pattern ON category_rules(pattern)")
            }
        }

        /**
         * Migration from version 10 to 11:
         * Convert Beekeeping group (id=1) and its sub-categories (id=101-110)
         * from default to custom categories (isDefault = 0).
         * Beekeeping is no longer a built-in category; existing users keep theirs
         * as editable/deletable custom categories.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Convert Beekeeping group to custom
                database.execSQL("UPDATE categories SET isDefault = 0 WHERE id = 1")
                // Convert all Beekeeping sub-categories to custom
                database.execSQL("UPDATE categories SET isDefault = 0 WHERE parentId = 1")
            }
        }

        /**
         * Migration from version 11 to 12:
         * Sub-category budgets support.
         * - Rename budgets.categoryGroupId → categoryId
         * - Add isGroupBudget column (default 1 = true for existing group-level rows)
         * - Rebuild indices for the new column name
         *
         * SQLite doesn't support ALTER TABLE RENAME COLUMN before 3.25.0 (API 30),
         * so we recreate the table for broad compatibility.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Step 1: Create the new budgets table with the updated schema
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS budgets_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER DEFAULT NULL,
                        isGroupBudget INTEGER NOT NULL DEFAULT 1,
                        amount REAL NOT NULL,
                        period TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)

                // Step 2: Copy data from old table, mapping categoryGroupId → categoryId
                // All existing budgets are group-level, so isGroupBudget = 1
                database.execSQL("""
                    INSERT INTO budgets_new (id, categoryId, isGroupBudget, amount, period, isActive, createdAt, updatedAt)
                    SELECT id, categoryGroupId, 1, amount, period, isActive, createdAt, updatedAt
                    FROM budgets
                """)

                // Step 3: Drop old table
                database.execSQL("DROP TABLE budgets")

                // Step 4: Rename new table
                database.execSQL("ALTER TABLE budgets_new RENAME TO budgets")

                // Step 5: Recreate indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_categoryId ON budgets(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_isActive ON budgets(isActive)")
            }
        }

        /**
         * Migration from version 12 to 13:
         * Add income table for manual monthly income tracking.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS income (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL,
                        yearMonth TEXT NOT NULL,
                        note TEXT DEFAULT NULL,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_income_yearMonth ON income(yearMonth)"
                )
            }
        }

        /**
         * Migration from version 13 to 14:
         * Add customStartDate and customEndDate columns to budgets table
         * for CUSTOM period support (user-defined date ranges).
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE budgets ADD COLUMN customStartDate INTEGER DEFAULT NULL"
                )
                database.execSQL(
                    "ALTER TABLE budgets ADD COLUMN customEndDate INTEGER DEFAULT NULL"
                )
            }
        }
    }
}
