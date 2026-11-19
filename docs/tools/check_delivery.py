# -*- coding: utf-8 -*-
"""Verify review-report remediations and generated delivery artifacts."""
from __future__ import annotations

import json
import re
import sys
import zipfile
from pathlib import Path

from docx import Document
from openpyxl import load_workbook
from pptx import Presentation

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
DELIVERY = DOCS / "05_交付文档"
errors: list[str] = []
notes: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def read(path: Path) -> str:
    if not path.exists():
        fail("missing " + str(path.relative_to(ROOT)))
        return ""
    suffix = path.suffix.lower()
    if suffix == ".docx":
        doc = Document(path)
        return "\n".join(p.text for p in doc.paragraphs) + "\n" + "\n".join(
            cell.text for table in doc.tables for row in table.rows for cell in row.cells)
    if suffix == ".pptx":
        return "\n".join(shape.text for slide in Presentation(path).slides
                         for shape in slide.shapes if hasattr(shape, "text"))
    if suffix == ".xlsx":
        return "\n".join(str(cell.value) for ws in load_workbook(path, data_only=False)
                         for row in ws.iter_rows() for cell in row if cell.value is not None)
    if suffix == ".pdf":
        import pdfplumber
        with pdfplumber.open(path) as pdf:
            return "\n".join(page.extract_text() or "" for page in pdf.pages)
    return path.read_text(encoding="utf-8")


def require(path: Path, text: str, markers: list[str]) -> None:
    for marker in markers:
        if marker not in text:
            fail(f"{path.name}: missing required marker {marker!r}")


def reject(path: Path, text: str, markers: list[str]) -> None:
    for marker in markers:
        if marker in text:
            fail(f"{path.name}: stale marker {marker!r}")


current = [
    DOCS / "01_需求" / "需求报告-V5.0.md",
    DOCS / "01_需求" / "V5.0-统一说明.md",
    DOCS / "02_流程与状态" / "工单状态机图-V5.0.html",
    DOCS / "03_系统设计" / "SYS-003_接口契约.md",
    DOCS / "03_系统设计" / "SYS-005_系统设计说明书.md",
    DOCS / "04_项目管理" / "答辩演示脚本.md",
    ROOT / "app" / "server" / "README.md",
]
for path in current:
    text = read(path)
    reject(path, text, ["node app/server/index.js", "seeded=7", "13 条合法转移",
                        "9 状态 / 13 转移", "10 × 13 = 140", "501 CR-001",
                        "返回 501 NOT_IMPLEMENTED", "不做真登录", "真实并发没压测"])

require(current[0], read(current[0]), ["合法转移（14 条）", "ADMIN_CANCEL", "楼栋不存在"])
require(current[2], read(current[2]), ["14 条合法转移", "<td>14</td>", "REJECT</code></td><td>管理员"])
require(current[3], read(current[3]), ["`ADMIN_CANCEL` | 管理员批准撤销", "LEGACY_REJECT_DISABLED",
                                      "无管理员介入完成率", "photoIdsBefore"])
require(current[4], read(current[4]), ["20 张业务表", "t_schema_migration", "真实上传"])

requirement = DOCS / "01_需求" / "需求报告-V5.0"
for suffix in (".md", ".docx", ".pdf"):
    path = Path(str(requirement) + suffix)
    text = read(path)
    require(path, text, ["14", "ADMIN_CANCEL", "REJECT", "管理员", "无管理员介入完成率"])
    reject(path, text, ["十三条合法转移", "13 条合法转移"])

artifacts = sorted(DELIVERY.glob("CG*"))
if len(artifacts) < 15:
    fail(f"expected at least 15 CG files (CG01-CG14 plus CG09 PDF), got {len(artifacts)}")
for path in artifacts:
    if path.suffix.lower() not in {".docx", ".xlsx", ".pptx", ".jpg", ".pdf", ".zip"}:
        continue
    text = read(path) if path.suffix.lower() not in {".jpg", ".zip"} else ""
    reject(path, text, ["13 条合法转移", "130 格", "34 JUnit", "22 pytest",
                        "node app/server/index.js", "seeded=7", "不做真登录",
                        "501 NOT_IMPLEMENTED", "master 单线", "9a537dc"])

require(DELIVERY / "CG09-测试与质量评估报告.docx",
        read(DELIVERY / "CG09-测试与质量评估报告.docx"), ["132", "126", "14 条"])
require(DELIVERY / "CG10-用户使用说明手册.docx",
        read(DELIVERY / "CG10-用户使用说明手册.docx"),
        ["Bearer Token", "dorm@2026", "5174", "5175", "seeded=11"])
require(DELIVERY / "CG11-总结汇报.pptx", read(DELIVERY / "CG11-总结汇报.pptx"),
        ["14 条合法转移", "140", "132", "ADMIN_CANCEL"])
require(DELIVERY / "CG02-开发计划与配置管理说明书.docx",
        read(DELIVERY / "CG02-开发计划与配置管理说明书.docx"),
        ["本地整改完成，待提交复核", "main", "ADMIN_CANCEL"])
require(DELIVERY / "CG06-构造规格说明书.docx",
        read(DELIVERY / "CG06-构造规格说明书.docx"), ["Tests run: 132"])
require(DELIVERY / "CG11-总结汇报.pptx", read(DELIVERY / "CG11-总结汇报.pptx"), ["自动目录"])
reject(DELIVERY / "CG11-总结汇报.pptx", read(DELIVERY / "CG11-总结汇报.pptx"), ["目录占位"])
require(DOCS / "07_审查文件夹" / "整改执行记录.md",
        read(DOCS / "07_审查文件夹" / "整改执行记录.md"), ["132 tests", "13/13", "LEGACY_REJECT_DISABLED", "尚需真实输入或外部验收"])
require(DOCS / "README.md", read(DOCS / "README.md"),
        ["05_交付文档", "06_再次改进计划", "07_审查文件夹", "REVIEW-FIX-001"])

cg14 = DELIVERY / "CG14-项目经理打分表.xlsx"
text = read(cg14)
if "待评" not in text:
    fail("CG14 must remain unevaluated until real identities and scores are supplied")
score_book = load_workbook(cg14, data_only=False)
score_sheet = score_book["打分表"]
for row in score_sheet.iter_rows(min_row=5, max_row=6, min_col=4, max_col=10):
    for cell in row:
        if isinstance(cell.value, (int, float)):
            fail(f"CG14 contains numeric score before evaluation at {cell.coordinate}")

zip_path = DELIVERY / "CG08-源代码.zip"
if not zip_path.exists():
    fail("CG08 source zip is missing")
else:
    with zipfile.ZipFile(zip_path) as archive:
        names = set(archive.namelist())
        if archive.testzip() is not None:
            fail("CG08 CRC check failed")
        for name in (
            "app/student-web/package.json", "app/worker-web/package.json", "app/admin-web/package.json",
            "app/server/pom.xml", "app/ai/llm.py", "docs/06_再次改进计划/README.md",
            "docs/07_审查文件夹/项目文档综合审查报告.md", "docs/07_审查文件夹/整改执行记录.md",
            "deploy/docker-compose.yml",
            "app/server/src/main/resources/db/schema.sql",
        ):
            if name not in names:
                fail("CG08 missing " + name)
        for name in names:
            if (name.endswith((".env.local", ".env.production", "tls.key", "tls.crt", ".log"))
                    or "/certs/" in name or "node_modules/" in name or "/target/" in name):
                fail("CG08 forbidden local/runtime file " + name)
        transition = archive.read("app/server/src/main/java/com/zxp/dormrepair/statemachine/TransitionTable.java").decode("utf-8")
        if "t.put(OrderEvent.REJECT, EnumSet.of(ActorType.ADMIN));" not in transition:
            fail("CG08 TransitionTable does not restrict REJECT to ADMIN")
        schema = archive.read("app/server/src/main/resources/db/schema.sql").decode("utf-8")
        if len(re.findall(r"CREATE TABLE IF NOT EXISTS\s+(t_\w+)", schema)) != 20:
            fail("schema.sql must define 20 business tables")
        notes.append(f"CG08 entries={len(names)}, bytes={zip_path.stat().st_size}")

notes.append("identity fields, personal reflection and evaluation remain explicitly pending real user input")
if errors:
    print("DELIVERY CHECK FAIL")
    print("\n".join("- " + e for e in errors))
    sys.exit(1)
print("DELIVERY CHECK PASS")
print("\n".join("- " + n for n in notes))