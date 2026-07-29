#!/usr/bin/env python3
"""Batch rename com/topjohnwu/magisk -> andro/pluginsuite in app/ source files."""

import os
import shutil
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent

MODULE_DIRS = [
    "app/apk/src/main",
    "app/apk-ng/src/main",
    "app/core/src/main",
    "app/shared/src/main",
    "app/stub/src/main",
    "app/test/src/main",
]

OLD_DIR = "com/topjohnwu/magisk"
NEW_DIR = "andro/pluginsuite"
OLD_PKG = "com.topjohnwu.magisk"
NEW_PKG = "andro.pluginsuite"

# 1. Rename directories
for mod in MODULE_DIRS:
    for sub in ["java", "aidl"]:
        base_dir = BASE / mod / sub
        if not base_dir.exists():
            continue
        old = base_dir / OLD_DIR
        new = base_dir / NEW_DIR
        if not old.exists():
            continue
        print(f"[MOVE] {old} -> {new}")
        new.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(old), str(new))
        # Clean up empty parent dirs
        p = old.parent
        while p != base_dir:
            try:
                if any(p.iterdir()):
                    break
                p.rmdir()
                p = p.parent
            except:
                break

# 2. Update content in all relevant files
exts = ('.kt', '.java', '.aidl', '.xml')
updated = 0
for root, dirs, files in os.walk(str(BASE / "app")):
    for fname in files:
        if not fname.endswith(exts):
            continue
        fpath = os.path.join(root, fname)
        try:
            with open(fpath, 'r', encoding='utf-8') as f:
                content = f.read()
            orig = content
            # package declarations: "package com.topjohnwu.magisk" -> "package andro.pluginsuite"
            content = content.replace(f"package {OLD_PKG}", f"package {NEW_PKG}")
            # import statements
            content = content.replace(f"import {OLD_PKG}", f"import {NEW_PKG}")
            # full references (like com.topjohnwu.magisk.R)
            content = content.replace(OLD_PKG, NEW_PKG)
            if content != orig:
                with open(fpath, 'w', encoding='utf-8') as f:
                    f.write(content)
                updated += 1
                print(f"[UPD] {fpath}")
        except Exception as e:
            print(f"[ERR] {fpath}: {e}")

print(f"\nDone. {updated} files updated.")
