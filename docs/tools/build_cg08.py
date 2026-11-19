# -*- coding: utf-8 -*-
"""Reproducibly package the current reviewed source tree as CG08.

Includes current source, tests, migrations and documentation, including uncommitted
review fixes. Never includes local secrets, certificates, databases, dependencies,
build output or runtime evidence.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "05_交付文档" / "CG08-源代码.zip"
TOPS = ("app", "deploy", "docs", "prototype")
ROOT_FILES = ("README.md", "AGENTS.md", "start.bat", ".gitignore", ".dockerignore")
REQUIRED = (
    "app/student-web/package.json",
    "app/worker-web/package.json",
    "app/admin-web/package.json",
    "app/server/pom.xml",
    "app/server/src/main/java/com/zxp/dormrepair/statemachine/TransitionTable.java",
    "app/server/src/main/resources/db/schema.sql",
    "app/ai/llm.py",
    "docs/06_再次改进计划/README.md",
    "docs/07_审查文件夹/项目文档综合审查报告.md",
    "docs/tools/build_docs.py",
    "docs/tools/build_extras.py",
    "deploy/docker-compose.yml",
)
SKIP_DIRS = {
    ".git", "node_modules", "target", "dist", "dist2", "dist3", "__pycache__",
    ".pytest_cache", ".venv", ".tmp", ".build-tmp", ".preflight-test",
    "certs", "artifacts", "screenshots", ".idea", ".vscode",
}
SKIP_SUFFIXES = (".log", ".tmp", ".pyc", ".db", ".db-journal", ".db-wal", ".db-shm")
SKIP_NAMES = {
    ".env", ".env.local", ".env.production", "alertmanager.yml", "tls.crt", "tls.key",
    "load-report.json", "people-report.json", "report.json", "resilience-report.json",
    "security-report.json", "screenshot-report.json", "gcm-diagnose.log",
}


def included(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in SKIP_DIRS for part in rel.parts):
        return False
    if path.name in SKIP_NAMES or path.name.startswith(".env."):
        return False
    if path.suffix.lower() in SKIP_SUFFIXES:
        return False
    if path.suffix.lower() in {".docx", ".pdf", ".xlsx", ".pptx", ".zip"}:
        # Generated deliverables are excluded; sources and the review report stay in.
        return False
    if rel.parts[:2] == ("docs", "99_存档"):
        return False
    return path.is_file()


def package() -> dict:
    paths = [ROOT / name for name in ROOT_FILES if (ROOT / name).is_file()]
    for top in TOPS:
        paths.extend(p for p in (ROOT / top).rglob("*") if included(p))
    entries = sorted({p.relative_to(ROOT).as_posix() for p in paths})
    missing = [name for name in REQUIRED if name not in entries]
    if missing:
        raise RuntimeError("CG08 missing required source entries: " + ", ".join(missing))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in entries:
            archive.write(ROOT / name, name)
    with zipfile.ZipFile(OUT) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("CG08 zip CRC verification failed")
        names = set(archive.namelist())
        for name in REQUIRED:
            if name not in names:
                raise RuntimeError("CG08 verification missing " + name)
        forbidden = [n for n in names if n.endswith((".env.local", ".env.production"))
                     or "/certs/" in n or "node_modules/" in n or "/target/" in n]
        if forbidden:
            raise RuntimeError("CG08 contains forbidden files: " + ", ".join(forbidden))
    result = {
        "file": OUT.name,
        "entries": len(entries),
        "bytes": OUT.stat().st_size,
        "sha256": hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "required": list(REQUIRED),
        "excluded": "secrets/certificates/databases/dependencies/build output/runtime evidence/generated binaries",
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return result


if __name__ == "__main__":
    package()