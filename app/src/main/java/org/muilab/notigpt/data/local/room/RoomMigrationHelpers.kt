package org.muilab.notigpt.data.local.room

import androidx.room.migration.Migration

internal fun sqlMigration(
    from: Int,
    to: Int,
    vararg statements: String,
): Migration = Migration(from, to) { db ->
    statements.forEach { statement -> db.execSQL(statement) }
}
