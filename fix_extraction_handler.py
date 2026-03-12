#!/usr/bin/env python3
"""Fix ReminderExtractionHandler.kt on disk to apply all pending changes."""
import re

fp = "app/src/main/java/org/muilab/notigpt/database/server/workers/n8n/ReminderExtractionHandler.kt"
with open(fp, 'r') as f:
    content = f.read()

changes = 0

# 1. Fix r.associatedNotis.toList() -> r.associatedNotiRecords.toList()
old = 'r.associatedNotis.toList()'
new = 'r.associatedNotiRecords.toList()'
if old in content:
    content = content.replace(old, new)
    changes += 1

# 2. Fix associatedNotis = assocKeys.toSet() -> associatedNotiRecords = assocKeys.toSet()
old = 'associatedNotis = assocKeys.toSet()'
new = 'associatedNotiRecords = assocKeys.toSet()'
if old in content:
    content = content.replace(old, new)
    changes += 1

# 3. Add isEvent, startTime, endTime to the user-triggered remindersForPayload
# Look for the pattern in user-triggered section
old_user_payload = '''"isTask" to r.isTask,
                    "deadlineTimeString" to deadlineIso,
                    "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                    "associatedNotiRecords" to r.associatedNotiRecords.toList(),'''
new_user_payload = '''"isTask" to r.isTask,
                    "isEvent" to r.isEvent,
                    "deadlineTimeString" to deadlineIso,
                    "startTimeString" to startTimeIso,
                    "endTimeString" to endTimeIso,
                    "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                    "associatedNotiRecords" to r.associatedNotiRecords.toList(),'''
if old_user_payload in content:
    content = content.replace(old_user_payload, new_user_payload)
    changes += 1

# Also need to add startTimeIso/endTimeIso declarations in user-triggered section
old_user_sdf = '''val deadlineIso = if (r.deadlineTimestamp > 0L) sdf.format(Date(r.deadlineTimestamp)) else -1L
                mapOf('''
new_user_sdf = '''val deadlineIso = if (r.deadlineTimestamp > 0L) sdf.format(Date(r.deadlineTimestamp)) else -1L
                val startTimeIso = if (r.startTime > 0L) sdf.format(Date(r.startTime)) else -1L
                val endTimeIso = if (r.endTime > 0L) sdf.format(Date(r.endTime)) else -1L
                mapOf('''
if old_user_sdf in content:
    content = content.replace(old_user_sdf, new_user_sdf, 1)  # first occurrence = user-triggered
    changes += 1

# 4. Same for periodic remindersForPayload
old_periodic_payload = '''"isTask" to r.isTask,
                "deadlineTimeString" to deadlineIso,
                "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                "associatedNotiRecords" to r.associatedNotiRecords.toList(),'''
new_periodic_payload = '''"isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "deadlineTimeString" to deadlineIso,
                "startTimeString" to startTimeIso,
                "endTimeString" to endTimeIso,
                "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                "associatedNotiRecords" to r.associatedNotiRecords.toList(),'''
if old_periodic_payload in content:
    content = content.replace(old_periodic_payload, new_periodic_payload)
    changes += 1

# Add startTimeIso/endTimeIso to periodic section (second occurrence of the sdf pattern)
# This is trickier since the first was already replaced. Find the remaining one.
old_periodic_sdf = '''val deadlineIso = if (r.deadlineTimestamp > 0L) sdf.format(Date(r.deadlineTimestamp)) else -1L
            mapOf('''
new_periodic_sdf = '''val deadlineIso = if (r.deadlineTimestamp > 0L) sdf.format(Date(r.deadlineTimestamp)) else -1L
            val startTimeIso = if (r.startTime > 0L) sdf.format(Date(r.startTime)) else -1L
            val endTimeIso = if (r.endTime > 0L) sdf.format(Date(r.endTime)) else -1L
            mapOf('''
if old_periodic_sdf in content:
    content = content.replace(old_periodic_sdf, new_periodic_sdf)
    changes += 1

# 5. Add extractionPreferences to both payloads
# User-triggered payload
old_ut_payload = '''"currentReminders" to remindersForPayload
        )

        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d("N8nWebhook", "JSON Payload (user-triggered):'''
new_ut_payload = '''"currentReminders" to remindersForPayload,
            "extractionPreferences" to ctx.getExtractionPreferencesPayload()
        )

        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d("N8nWebhook", "JSON Payload (user-triggered):'''
if old_ut_payload in content:
    content = content.replace(old_ut_payload, new_ut_payload)
    changes += 1

# Periodic payload
old_per_payload = '''"currentReminders" to remindersForPayload
        )

        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d("N8nWebhook", "JSON Payload (claimed):'''
new_per_payload = '''"currentReminders" to remindersForPayload,
            "extractionPreferences" to ctx.getExtractionPreferencesPayload()
        )

        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d("N8nWebhook", "JSON Payload (claimed):'''
if old_per_payload in content:
    content = content.replace(old_per_payload, new_per_payload)
    changes += 1

# 6. Parse isEvent, startTime, endTime from inbound JSON in user-triggered section
old_ut_parse = '''val isTask = it.optBoolean("isTask", true)
                        val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))'''
new_ut_parse = '''val isTask = it.optBoolean("isTask", true)
                        val isEvent = it.optBoolean("isEvent", false)
                        val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))
                        val startTimeMs = isoToUnixMillis(it.optString("startTimeString", "-1")).let { v -> if (v == -1L) 0L else v }
                        val endTimeMs = isoToUnixMillis(it.optString("endTimeString", "-1")).let { v -> if (v == -1L) 0L else v }'''
if old_ut_parse in content:
    content = content.replace(old_ut_parse, new_ut_parse, 1)
    changes += 1

# And add isEvent/startTime/endTime to the user-triggered ReminderUnit construction
old_ut_unit = '''isTask = isTask,
                            isCompleted = isCompleted,
                            lastUpdateTimestamp = System.currentTimeMillis(),
                            deadlineTimestamp = deadlineMs,
                            estimatedCompletionTime = estimate,
                            associatedNotiRecords = assocKeys.toSet(),
                            extractionSnapshotId = snapshotId,
                            origin = "llm_manual_extraction",'''
new_ut_unit = '''isTask = isTask,
                            isEvent = isEvent,
                            isCompleted = isCompleted,
                            lastUpdateTimestamp = System.currentTimeMillis(),
                            deadlineTimestamp = deadlineMs,
                            startTime = startTimeMs,
                            endTime = endTimeMs,
                            estimatedCompletionTime = estimate,
                            associatedNotiRecords = assocKeys.toSet(),
                            extractionSnapshotId = snapshotId,
                            origin = "llm_manual_extraction",'''
if old_ut_unit in content:
    content = content.replace(old_ut_unit, new_ut_unit)
    changes += 1

# 7. Parse isEvent, startTime, endTime from inbound JSON in periodic section
old_per_parse = '''val isTask = it.optBoolean("isTask", true)
                    val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))'''
new_per_parse = '''val isTask = it.optBoolean("isTask", true)
                    val isEvent = it.optBoolean("isEvent", false)
                    val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))
                    val startTimeMs = isoToUnixMillis(it.optString("startTimeString", "-1")).let { v -> if (v == -1L) 0L else v }
                    val endTimeMs = isoToUnixMillis(it.optString("endTimeString", "-1")).let { v -> if (v == -1L) 0L else v }'''
if old_per_parse in content:
    content = content.replace(old_per_parse, new_per_parse)
    changes += 1

# And add to periodic ReminderUnit construction
old_per_unit = '''isTask = isTask,
                        isCompleted = isCompleted,
                        lastUpdateTimestamp = System.currentTimeMillis(),
                        deadlineTimestamp = deadlineMs,
                        estimatedCompletionTime = estimate,
                        associatedNotiRecords = assocKeys.toSet(),
                        extractionSnapshotId = snapshotId,
                        origin = "llm_auto_extraction",'''
new_per_unit = '''isTask = isTask,
                        isEvent = isEvent,
                        isCompleted = isCompleted,
                        lastUpdateTimestamp = System.currentTimeMillis(),
                        deadlineTimestamp = deadlineMs,
                        startTime = startTimeMs,
                        endTime = endTimeMs,
                        estimatedCompletionTime = estimate,
                        associatedNotiRecords = assocKeys.toSet(),
                        extractionSnapshotId = snapshotId,
                        origin = "llm_auto_extraction",'''
if old_per_unit in content:
    content = content.replace(old_per_unit, new_per_unit)
    changes += 1

# 8. Prefer associatedNotiRecords in inbound parsing (user-triggered)
old_ut_assoc = '''val assocKeys = mutableSetOf<String>()
                        val assoc = it.optJSONArray("associatedNotis")'''
new_ut_assoc = '''val assocIds = mutableSetOf<String>()
                        // Prefer associatedNotiRecords; fall back to associatedNotis for backward compat
                        val assoc = it.optJSONArray("associatedNotiRecords") ?: it.optJSONArray("associatedNotis")'''
if old_ut_assoc in content:
    content = content.replace(old_ut_assoc, new_ut_assoc)
    # Also fix assocKeys -> assocIds in user-triggered section
    # But be careful not to break periodic section
    changes += 1

# 9. Prefer associatedNotiRecords in inbound parsing (periodic)
old_per_assoc = '''val assocKeys = mutableSetOf<String>()
                    val assoc = it.optJSONArray("associatedNotis")'''
new_per_assoc = '''val assocIds = mutableSetOf<String>()
                    // Prefer associatedNotiRecords; fall back to associatedNotis for backward compat
                    val assoc = it.optJSONArray("associatedNotiRecords") ?: it.optJSONArray("associatedNotis")'''
if old_per_assoc in content:
    content = content.replace(old_per_assoc, new_per_assoc)
    changes += 1

# 10. Add hasEvent to both outbound notisPayload sections
old_ut_noti = '''"hasTask" to unit.hasTask,
                    "hasMemo" to unit.hasMemo'''
new_ut_noti = '''"hasTask" to unit.hasTask,
                    "hasMemo" to unit.hasMemo,
                    "hasEvent" to unit.hasEvent'''
content = content.replace(old_ut_noti, new_ut_noti)

old_per_noti = '''"hasTask" to unit.hasTask,
                "hasMemo" to unit.hasMemo'''
new_per_noti = '''"hasTask" to unit.hasTask,
                "hasMemo" to unit.hasMemo,
                "hasEvent" to unit.hasEvent'''
content = content.replace(old_per_noti, new_per_noti)

with open(fp, 'w') as f:
    f.write(content)

# Verify
with open(fp) as f:
    final = f.read()

with open('/tmp/fix_result.txt', 'w') as f:
    f.write(f"Changes attempted: {changes}\n")
    f.write(f"r.associatedNotis remaining: {len(re.findall(r'r\\.associatedNotis', final))}\n")
    f.write(f"associatedNotis = assocKeys remaining: {len(re.findall('associatedNotis = assocKeys', final))}\n")
    f.write(f"isEvent count: {len(re.findall('isEvent', final))}\n")
    f.write(f"extractionPreferences count: {len(re.findall('extractionPreferences', final))}\n")
    f.write(f"hasEvent count: {len(re.findall('hasEvent', final))}\n")
    f.write(f"startTimeIso count: {len(re.findall('startTimeIso', final))}\n")

