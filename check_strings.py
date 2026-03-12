#!/usr/bin/env python3
"""Force-sync strings.xml files from editor's expected content to disk."""
import os

base = "/Users/udchen/Documents/Projects/NotiLLM/NotiGPT/app/src/main/res"

# Check what's actually on disk
en_path = os.path.join(base, "values/strings.xml")
with open(en_path, 'r') as f:
    content = f.read()

# Check if the pref_snackbar_deleted string is present on disk
missing = "pref_snackbar_deleted" not in content
print(f"en strings.xml: missing pref strings = {missing}")
print(f"en strings.xml: has export_dialog_title_tasks = {'export_dialog_title_tasks' in content}")
print(f"en strings.xml: has tab_preferences = {'tab_preferences' in content}")
print(f"en strings.xml: has google_calendar_no_app = {'google_calendar_no_app' in content}")
print(f"en strings.xml: has a11y_quick_export_tasks = {'a11y_quick_export_tasks' in content}")
print(f"en strings.xml length = {len(content)}")

