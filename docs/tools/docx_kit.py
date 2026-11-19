# -*- coding: utf-8 -*-
"""交付文档用的 python-docx 小工具箱。

踩过的坑都写在这儿，改文档时别再踩第二遍：
1. 只设 cell.width 无效 —— 必须改 table.columns[i].width（写 gridCol）+ tblLayout=fixed。
2. 行跨页会看起来像“多了一行空白” —— 每个 trPr 加 cantSplit。
3. 标题与表格被分页切开 —— 表前那段说明设 keep_with_next。
4. 结尾孤页 —— 别在文末留独立短段，折进最后一张表或页脚。
5. 页脚页码要域代码，不能写字面量。
"""
import os

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

HEI = "黑体"
SONG = "宋体"
INK = RGBColor(0x1A, 0x1A, 0x1A)
ACCENT = RGBColor(0x1F, 0x4E, 0x79)
MUTED = RGBColor(0x66, 0x66, 0x66)

# A4 减去左右 2.6cm 边距 = 15.8cm，所有表格列宽之和必须等于它
TEXT_W = 15.8


# ---------------------------------------------------------------- 低层工具

def cnfont(run, name, size=None, bold=None, color=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color


def shade(cell, hexcolor):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hexcolor)
    tcPr.append(shd)


def cell_margins(table, top=60, bottom=60, left=90, right=90):
    tblPr = table._tbl.tblPr
    mar = OxmlElement("w:tblCellMar")
    for side, val in (("top", top), ("left", left), ("bottom", bottom), ("right", right)):
        el = OxmlElement(f"w:{side}")
        el.set(qn("w:w"), str(val))
        el.set(qn("w:type"), "dxa")
        mar.append(el)
    tblPr.append(mar)


def repeat_header(row):
    trPr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:tblHeader")
    el.set(qn("w:val"), "true")
    trPr.append(el)


def cant_split(row):
    trPr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:cantSplit")
    el.set(qn("w:val"), "true")
    trPr.append(el)


def fixed_layout(table, widths):
    """固定列宽。只设 cell.width 会被 Word 忽略，必须写 gridCol + tblLayout fixed。"""
    tblPr = table._tbl.tblPr
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tblPr.append(layout)
    for i, w in enumerate(widths):
        table.columns[i].width = Cm(w)
    for row in table.rows:
        for i, w in enumerate(widths):
            row.cells[i].width = Cm(w)


def page_field(paragraph):
    run = paragraph.add_run()
    fld = OxmlElement("w:fldSimple")
    fld.set(qn("w:instr"), "PAGE")
    run._r.addnext(fld)


# ---------------------------------------------------------------- 文档骨架

def new_doc(title_footer):
    doc = Document()
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Cm(21), Cm(29.7)
    sec.left_margin = sec.right_margin = Cm(2.6)
    sec.top_margin = Cm(2.5)
    sec.bottom_margin = Cm(2.3)

    normal = doc.styles["Normal"]
    normal.font.name = SONG
    normal.font.size = Pt(10.5)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), SONG)
    pf = normal.paragraph_format
    pf.line_spacing = 1.45
    pf.space_after = Pt(4)

    # 页脚：左标题 + 右页码
    footer = sec.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = footer.add_run(title_footer + "　第 ")
    cnfont(r, SONG, 9, color=MUTED)
    page_field(footer)
    r2 = footer.add_run(" 页")
    cnfont(r2, SONG, 9, color=MUTED)
    return doc


def h1(doc, text):
    p = doc.add_paragraph(style="Heading 1")
    p.paragraph_format.space_before = Pt(16)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    cnfont(r, HEI, 15, True, ACCENT)
    # 标题下加一条细线
    pPr = p._p.get_or_add_pPr()
    bdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "6")
    bottom.set(qn("w:space"), "3")
    bottom.set(qn("w:color"), "1F4E79")
    bdr.append(bottom)
    pPr.append(bdr)
    return p


def h2(doc, text):
    p = doc.add_paragraph(style="Heading 2")
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    cnfont(r, HEI, 12, True, ACCENT)
    return p


def h3(doc, text):
    p = doc.add_paragraph(style="Heading 3")
    p.paragraph_format.space_before = Pt(7)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    cnfont(r, HEI, 10.5, True)
    return p


def p(doc, text, indent=True, size=10.5, gap=4):
    par = doc.add_paragraph()
    par.paragraph_format.space_after = Pt(gap)
    if indent:
        par.paragraph_format.first_line_indent = Cm(0.74)
    r = par.add_run(text)
    cnfont(r, SONG, size, color=INK)
    return par


def bullets(doc, items, size=10.5):
    for it in items:
        par = doc.add_paragraph(style="List Bullet")
        par.paragraph_format.space_after = Pt(2)
        par.paragraph_format.line_spacing = 1.35
        r = par.add_run(it)
        cnfont(r, SONG, size, color=INK)


def cap(doc, text):
    par = doc.add_paragraph()
    par.paragraph_format.space_before = Pt(3)
    par.paragraph_format.space_after = Pt(3)
    r = par.add_run(text)
    cnfont(r, SONG, 8.5, color=MUTED)
    return par


def table(doc, header, rows, widths, caption=None, size=9.5, head_size=9.5,
          keep_first=0, align_right=(), zebra=False):
    """表格。caption 写在表上方（表注习惯），表头深蓝反白。"""
    if caption:
        par = doc.add_paragraph()
        par.paragraph_format.space_before = Pt(6)
        par.paragraph_format.space_after = Pt(3)
        par.paragraph_format.keep_with_next = True
        r = par.add_run(caption)
        cnfont(r, SONG, 9, True, INK)

    # 表前的说明段与表同页
    if doc.paragraphs:
        doc.paragraphs[-1].paragraph_format.keep_with_next = True

    t = doc.add_table(rows=1, cols=len(header))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell_margins(t)
    hdr = t.rows[0]
    repeat_header(hdr)
    cant_split(hdr)
    for i, h in enumerate(header):
        cell = hdr.cells[i]
        shade(cell, "1F4E79")
        par = cell.paragraphs[0]
        par.paragraph_format.space_after = Pt(0)
        par.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = par.add_run(h)
        cnfont(r, HEI, head_size, True, RGBColor(0xFF, 0xFF, 0xFF))

    for ri, row in enumerate(rows):
        tr = t.add_row()
        cant_split(tr)
        for ci, val in enumerate(row):
            cell = tr.cells[ci]
            par = cell.paragraphs[0]
            par.paragraph_format.space_after = Pt(0)
            par.paragraph_format.line_spacing = 1.2
            if ci in align_right:
                par.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            r = par.add_run("" if val is None else str(val))
            cnfont(r, SONG, size, color=INK)
            if zebra and ri % 2 == 1:
                shade(cell, "F4F7FB")

    fixed_layout(t, widths)

    if keep_first:
        for row in t.rows[: keep_first + 1]:
            for cell in row.cells:
                for par in cell.paragraphs:
                    par.paragraph_format.keep_with_next = True
    return t


def cover(doc, org, code, title, sub, meta_pairs):
    """封面：机构 / 编号 / 大标题 / 副标题 / 信息表。"""
    for _ in range(3):
        doc.add_paragraph()
    par = doc.add_paragraph()
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = par.add_run(org)
    cnfont(r, HEI, 12, color=MUTED)

    doc.add_paragraph()
    par = doc.add_paragraph()
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = par.add_run(title)
    cnfont(r, HEI, 26, True, ACCENT)

    par = doc.add_paragraph()
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = par.add_run(sub)
    cnfont(r, HEI, 13, color=INK)

    doc.add_paragraph()
    par = doc.add_paragraph()
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = par.add_run("文档编号　" + code)
    cnfont(r, SONG, 10.5, color=MUTED)

    for _ in range(4):
        doc.add_paragraph()

    table(doc, ["项", "内容"], meta_pairs, [4.6, 11.2], size=9.5)
    doc.add_page_break()


def toc_placeholder(doc, items, note="目次由 Word 根据标题级别自动生成；导出 PDF 前更新整个目录域。"):
    """插入真正的 TOC 域。生成器不手填页码，Word 打开和导出时负责更新。"""
    h1(doc, "目　录")
    p(doc, note, indent=False, size=9.5)
    par = doc.add_paragraph()
    run = par.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    begin.set(qn("w:dirty"), "true")
    instruction = OxmlElement("w:instrText")
    instruction.set(qn("xml:space"), "preserve")
    instruction.text = ' TOC \\o "1-3" \\h \\z \\u '
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    hint = OxmlElement("w:t")
    hint.text = "打开 Word 后更新目录"
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    for element in (begin, instruction, separate, hint, end):
        run._r.append(element)
    doc.add_page_break()


def save(doc, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    doc.save(path)
    return path
