"""生成 CG01 / CG11 / CG12 / CG13 / CG14。

用法：
    python build_extras.py all
    python build_extras.py cg01 cg14
输出目录：docs/05_交付文档
"""
import os
import sys

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

from docx_kit import new_doc, h1, h2, p, bullets, table, save, cover

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "05_交付文档")
ORG = "示范大学　后勤保障部"
PM = "（待填：学号 姓名）"
NAVY = "1F4E79"


def _sheet(wb, title, header, rows, widths, note=None):
    ws = wb.active if wb.active.max_row == 1 and wb.active["A1"].value is None else wb.create_sheet()
    ws.title = title
    thin = Side(style="thin", color="BFBFBF")
    border = Border(left=thin, right=thin, top=thin, bottom=thin)

    r = 1
    if note:
        ws.cell(row=1, column=1, value=note).font = Font(size=9, color="666666")
        ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=len(header))
        r = 2
    for i, h in enumerate(header, start=1):
        c = ws.cell(row=r, column=i, value=h)
        c.font = Font(bold=True, color="FFFFFF", size=10)
        c.fill = PatternFill("solid", fgColor=NAVY)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = border
    ws.row_dimensions[r].height = 26
    for row in rows:
        r += 1
        for i, v in enumerate(row, start=1):
            c = ws.cell(row=r, column=i, value=v)
            c.font = Font(size=10)
            c.alignment = Alignment(vertical="center", wrap_text=True)
            c.border = border
        ws.row_dimensions[r].height = 30
    for i, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = ws.cell(row=3 if note else 2, column=1)
    return ws


# ============================================================ CG01 项目信息登记表

def cg01():
    wb = Workbook()
    wb.active.title = "封面"
    ws = wb.active
    ws["A1"] = "人机协同的软件工程项目实训　项目信息登记表"
    ws["A1"].font = Font(size=16, bold=True, color=NAVY)
    ws.merge_cells("A1:D1")
    info = [
        ("项目名称", "宿舍维修系统（零人工闭环）"),
        ("项目简称 / 代号", "宿舍维修 V5.0"),
        ("组号 / 组名", "（待填：第 X 组 / 组名）"),
        ("组名含义 / 口号", "（待填）"),
        ("项目经理", PM),
        ("团队成员数", "5 人"),
        ("指导教师", "（待填）"),
        ("课程 / 学期", "人机协同的软件工程项目实训 / （待填）"),
        ("技术栈", "Spring Boot 4 + MySQL 8 + MyBatis-Plus；Vue 3 + Element Plus；Python FastAPI + 规则定级"),
        ("代码规模", "后端 22 个类；前端 11 页 5 组件；AI 3 个文件；三端测试 18 + 30 + 3 段脚本"),
        ("仓库", "本地 git（master），8 次里程碑提交"),
        ("提交日期", "2026-11-18"),
    ]
    r = 3
    for k, v in info:
        ws.cell(row=r, column=1, value=k).font = Font(bold=True, size=10)
        ws.cell(row=r, column=1).fill = PatternFill("solid", fgColor="EAF1FA")
        c = ws.cell(row=r, column=2, value=v)
        c.font = Font(size=10)
        c.alignment = Alignment(wrap_text=True, vertical="center")
        ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=4)
        ws.row_dimensions[r].height = 32
        r += 1
    ws.column_dimensions["A"].width = 18
    for col in ("B", "C", "D"):
        ws.column_dimensions[col].width = 24

    _sheet(wb, "成员", ["序号", "学号", "姓名", "角色", "主要负责", "联系方式", "备注"], [
        [1, "（待填）", "（待填）", "项目经理 / 主程", "口径确认、状态机与后端、文档汇总", "（待填）", ""],
        [2, "（待填）", "（待填）", "前端", "Vue 11 页、设计系统、截图核对", "（待填）", ""],
        [3, "（待填）", "（待填）", "AI / 算法", "定级规则与兜底、规则单测", "（待填）", ""],
        [4, "（待填）", "（待填）", "测试", "白盒/接口/黑盒用例、缺陷跟踪", "（待填）", ""],
        [5, "（待填）", "（待填）", "文档 / 答辩", "演示脚本、海报、PPT、个人体会", "（待填）", ""],
    ], [6, 16, 12, 16, 40, 18, 14], note="说明：学号、姓名、组号组名、指导教师需按实际填写；角色可一人多岗。")

    _sheet(wb, "交付物", ["编号", "交付物", "文件名", "状态"], [
        ["CG01", "信息登记表", "CG01-项目信息登记表.xlsx", "已交"],
        ["CG02", "开发计划与配置管理说明书", "CG02-开发计划与配置管理说明书.docx", "已交"],
        ["CG03", "选题可行性研究报告", "CG03-选题可行性研究报告.docx", "已交"],
        ["CG04", "需求规格说明书", "CG04-需求规格说明书.docx", "已交"],
        ["CG05", "设计规格说明书", "CG05-设计规格说明书.docx", "已交"],
        ["CG06", "构造规格说明书", "CG06-构造规格说明书.docx", "已交"],
        ["CG07", "源代码说明", "CG07-源代码说明.docx", "已交"],
        ["CG08", "源代码包", "CG08-源代码.zip", "已交"],
        ["CG09", "测试与质量评估报告", "CG09-测试与质量评估报告.docx", "已交"],
        ["CG10", "用户使用说明手册", "CG10-用户使用说明手册.docx", "已交"],
        ["CG11", "总结汇报", "CG11-总结汇报.pptx", "已交"],
        ["CG12", "路演海报", "CG12-路演海报.jpg", "已交"],
        ["CG13", "个人体会（每人一份）", "CG13-个人体会-（姓名）.docx", "每人一份"],
        ["CG14", "项目经理打分表", "CG14-项目经理打分表.xlsx", "已交"],
    ], [8, 30, 42, 12])

    path = os.path.join(OUT, "CG01-项目信息登记表.xlsx")
    wb.save(path)
    return path


# ============================================================ CG14 项目经理打分表

def cg14():
    wb = Workbook()
    ws = wb.active
    ws.title = "打分表"
    ws["A1"] = "项目经理打分表（百分制）"
    ws["A1"].font = Font(size=14, bold=True, color=NAVY)
    ws.merge_cells("A1:I1")
    ws["A2"] = "说明：七个维度合计 100 分；任意两名成员的分数不得相同（总分保留 1 位小数）。"
    ws["A2"].font = Font(size=9, color="666666")
    ws.merge_cells("A2:I2")

    header = ["序号", "学号", "姓名", "需求分析\n(10)", "设计\n(10)", "编码\n(25)",
              "测试\n(15)", "文档\n(15)", "协作\n(15)", "答辩\n(10)", "总分"]
    thin = Side(style="thin", color="BFBFBF")
    border = Border(left=thin, right=thin, top=thin, bottom=thin)
    for i, h in enumerate(header, start=1):
        c = ws.cell(row=4, column=i, value=h)
        c.font = Font(bold=True, color="FFFFFF", size=10)
        c.fill = PatternFill("solid", fgColor=NAVY)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = border
    ws.row_dimensions[4].height = 40

    demo = [(8.5, 8.5, 23, 13, 14, 14, 9), (8, 8, 21, 12, 13, 13, 8.5),
            (7.5, 7, 20, 12, 12, 13, 8), (7, 7, 18, 11, 12, 12, 7.5),
            (7, 6.5, 17, 10, 11, 12, 7)]
    for n in range(5):
        r = 5 + n
        ws.cell(row=r, column=1, value=n + 1).alignment = Alignment(horizontal="center")
        for col in (2, 3):
            ws.cell(row=r, column=col, value="（待填）")
        vals = demo[n]
        for i, v in enumerate(vals):
            c = ws.cell(row=r, column=4 + i, value=v)
            c.alignment = Alignment(horizontal="center")
        ws.cell(row=r, column=11, value=f"=SUM(D{r}:J{r})").font = Font(bold=True)
        ws.cell(row=r, column=11).alignment = Alignment(horizontal="center")
        for col in range(1, 12):
            cell = ws.cell(row=r, column=col)
            cell.border = border
            if col not in (2, 3, 11):
                cell.font = Font(size=10)
        ws.row_dimensions[r].height = 26

    ws.cell(row=11, column=1, value="合计 / 平均").font = Font(bold=True)
    ws.cell(row=11, column=11, value="=ROUND(AVERAGE(K5:K9),1)").font = Font(bold=True)
    ws.cell(row=11, column=11).alignment = Alignment(horizontal="center")

    widths = [6, 14, 12, 10, 9, 9, 9, 9, 9, 9, 10]
    for i, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w

    ws2 = wb.create_sheet("评分说明")
    rows = [
        ["维度", "看什么", "给分档"],
        ["需求分析", "需求是否可判断、是否写了不做什么", "9-10 有量化判据；7-8 清楚；≤6 只有形容词"],
        ["设计", "状态机、接口契约、数据模型是否自洽", "9-10 三处对齐且有测试；7-8 基本对齐；≤6 有矛盾"],
        ["编码", "是否守规约（唯一改状态入口、阈值不写死、无假数据）", "22-25 全部守；18-21 个别例外；≤17 多处破例"],
        ["测试", "白盒+接口+黑盒+脚本四层是否齐、断言是否真断言", "13-15 四层齐且能复跑；10-12 缺一层；≤9 只有手工点点"],
        ["文档", "是否按 CG 编号、是否有封面目录页脚、数字是否可核", "13-15 齐套；10-12 缺项；≤9 拼凑"],
        ["协作", "是否按里程碑交付、是否有人等别人", "13-15 每里程碑准时；10-12 个别延迟；≤9 反复返工"],
        ["答辩", "能否 5 分钟讲清闭环三条判据、能否现场证明 409", "9-10 现场能证；7-8 讲得清；≤6 只讲愿景"],
    ]
    for r, row in enumerate(rows, start=1):
        for c, v in enumerate(row, start=1):
            cell = ws2.cell(row=r, column=c, value=v)
            cell.alignment = Alignment(wrap_text=True, vertical="top")
            cell.border = border
            if r == 1:
                cell.font = Font(bold=True, color="FFFFFF")
                cell.fill = PatternFill("solid", fgColor=NAVY)
    ws2.column_dimensions["A"].width = 14
    ws2.column_dimensions["B"].width = 46
    ws2.column_dimensions["C"].width = 46

    path = os.path.join(OUT, "CG14-项目经理打分表.xlsx")
    wb.save(path)
    return path


# ============================================================ CG13 个人体会

def cg13():
    doc = new_doc("宿舍维修系统 · 个人体会")
    cover(doc, ORG, "CG13-RFL-001", "个人体会",
          "宿舍维修系统 V5.0　（每人一份，不得雷同）",
          [["文档编号", "CG13-RFL-001"],
           ["版本 / 日期", "V1.0　2026-11-18"],
           ["姓名 / 学号", "（待填：姓名 学号）"],
           ["承担角色", "（待填：项目经理 / 前端 / AI / 测试 / 文档）"]])

    h1(doc, "一　我在这门课里做了什么")
    p(doc, "（待填：用 200 字左右写清自己负责的模块、交付了什么、被谁验收。"
           "写具体的东西：哪个类、哪一页、哪条测试。避免“参与了项目开发”这种句子。）")

    h1(doc, "二　我原先以为的和实际发生的")
    table(doc, ["我原先以为", "实际发生的", "为什么会这样"], [
        ["（待填）", "（待填）", "（待填）"],
        ["（待填）", "（待填）", "（待填）"],
        ["（待填）", "（待填）", "（待填）"],
    ], [4.8, 6.4, 4.6], caption="表 2-1　认知差异", size=9)
    p(doc, "提示：这一节是整篇里最值钱的部分。写“我以为把状态画成图就完事了，"
           "结果发现真正难的是让 96 种非法组合都返回 409 且一个字都不改”这种，比写感想有用。")

    h1(doc, "三　一个我踩过的坑")
    p(doc, "（待填：选一个真实的技术坑，写清 现象 → 排查过程 → 根因 → 修法 → 以后怎么避免。"
           "例如：“例外清单里超预算那条在配件到货后就消失了，原因是清单只在待配件状态下才计算……”）")

    h1(doc, "四　我对“人机协同”的理解")
    p(doc, "（待填：结合本项目写。可用的角度：这套系统里人只做两类事——"
           "必须人到场的物理动作（打卡、修、拍照）和必须人做的判断（签字、驳回、批准撤销）；"
           "其余全部交给规则与定时任务。所以“协同”不是让 AI 更像人，而是把人的动作压到最少且每一步都留痕。）")

    h1(doc, "五　如果还有两周")
    bullets(doc, [
        "（待填）我会先补……",
        "（待填）我不会做……，因为……",
        "（待填）我最想验证的一个数字是……",
    ])
    return save(doc, os.path.join(OUT, "CG13-个人体会-（姓名）.docx"))


# ============================================================ CG11 总结汇报 pptx

def cg11():
    from pptx import Presentation
    from pptx.util import Inches, Pt
    from pptx.dml.color import RGBColor

    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)
    blank = prs.slide_layouts[6]
    NAVY_RGB = RGBColor(0x12, 0x29, 0x4B)
    ORANGE = RGBColor(0xFF, 0x5A, 0x1F)
    GREY = RGBColor(0x47, 0x54, 0x67)

    def slide(title, kicker=None):
        s = prs.slides.add_slide(blank)
        bar = s.shapes.add_shape(1, Inches(0), Inches(0), prs.slide_width, Inches(0.09))
        bar.fill.solid()
        bar.fill.fore_color.rgb = NAVY_RGB
        bar.line.fill.background()
        tb = s.shapes.add_textbox(Inches(0.9), Inches(0.55), Inches(11.6), Inches(0.9))
        tf = tb.text_frame
        tf.word_wrap = True
        run = tf.paragraphs[0].add_run()
        run.text = title
        run.font.size = Pt(30)
        run.font.bold = True
        run.font.color.rgb = NAVY_RGB
        if kicker:
            kb = s.shapes.add_textbox(Inches(0.9), Inches(1.35), Inches(11.6), Inches(0.5))
            r2 = kb.text_frame.paragraphs[0].add_run()
            r2.text = kicker
            r2.font.size = Pt(14)
            r2.font.color.rgb = GREY
        # 左侧竖线 + 页脚
        ln = s.shapes.add_shape(1, Inches(0.55), Inches(0.62), Inches(0.05), Inches(1.05))
        ln.fill.solid()
        ln.fill.fore_color.rgb = ORANGE
        ln.line.fill.background()
        ft = s.shapes.add_textbox(Inches(0.9), Inches(6.85), Inches(11.6), Inches(0.4))
        fr = ft.text_frame.paragraphs[0].add_run()
        fr.text = "宿舍维修系统 V5.0　·　人机协同的软件工程项目实训"
        fr.font.size = Pt(11)
        fr.font.color.rgb = RGBColor(0x98, 0xA2, 0xB3)
        return s

    def body(s, lines, top=2.05, size=19):
        tb = s.shapes.add_textbox(Inches(0.9), Inches(top), Inches(11.6), Inches(4.8))
        tf = tb.text_frame
        tf.word_wrap = True
        for i, line in enumerate(lines):
            para = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
            run = para.add_run()
            run.text = line
            run.font.size = Pt(size)
            run.font.color.rgb = RGBColor(0x0B, 0x15, 0x24)
            para.space_after = Pt(20)

    # 1 封面
    s = prs.slides.add_slide(blank)
    bg = s.shapes.add_shape(1, Inches(0), Inches(0), prs.slide_width, prs.slide_height)
    bg.fill.solid()
    bg.fill.fore_color.rgb = NAVY_RGB
    bg.line.fill.background()
    tb = s.shapes.add_textbox(Inches(1.0), Inches(2.3), Inches(11.3), Inches(1.6))
    run = tb.text_frame.paragraphs[0].add_run()
    run.text = "拍一张、说一句，剩下的它自己跑"
    run.font.size = Pt(44)
    run.font.bold = True
    run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    tb2 = s.shapes.add_textbox(Inches(1.0), Inches(3.9), Inches(11.3), Inches(0.6))
    r = tb2.text_frame.paragraphs[0].add_run()
    r.text = "宿舍维修系统 V5.0　人机协同的软件工程项目实训"
    r.font.size = Pt(18)
    r.font.color.rgb = RGBColor(0xC7, 0xD6, 0xEA)
    tb3 = s.shapes.add_textbox(Inches(1.0), Inches(5.6), Inches(11.3), Inches(0.5))
    r3 = tb3.text_frame.paragraphs[0].add_run()
    r3.text = "组号组名　项目经理　2026-11-18"
    r3.font.size = Pt(14)
    r3.font.color.rgb = RGBColor(0xFF, 0x5A, 0x1F)

    # 2 问题
    s = slide("问题不是“报不了”，是“报了没人管”", "四个可观察的现象")
    body(s, ["同一间房一周被报三次 —— 入口不统一",
             "师傅到门口没人 —— 时段缺失，白跑",
             "学生反复问“派了吗” —— 进度不可见",
             "修完说不清 —— 没有留痕，责任无法回溯"])

    # 3 目标
    s = slide("目标是“一条链不需要人点头”", "零人工完成率 = 全部事件由系统推进的已完成工单占比")
    body(s, ["学生提交 → 系统定级派工 → 师傅到门完工 → 学生签字 → 系统归档",
             "全流程 9 个状态、13 条合法转移",
             "人只做两件事：必须到场的物理动作、必须由人做的判断"])

    # 4 状态机
    s = slide("状态机是整个系统的骨架", "10 行 × 13 事件 = 130 种组合，只有 13 种合法")
    body(s, ["转移表写在 TransitionTable，别处不许再写一份",
             "没这条边 → 409，带 from / event / allowed",
             "有边但前置不满足 → 422",
             "唯一改状态入口：OrderStateService.transit() 六步顺序"])

    # 5 两颗计时器
    s = slide("两个 7 天，起点不一样", "T1 验收 / T2 归档，同一张单不会同时有两颗")
    body(s, ["T1：师傅完工起算 168 小时 → 系统自动验收（manual = 0）",
             "T2：学生签字起算 7 天 → 系统自动归档（转只读）",
             "都是“现算”而不是存剩余时间，改阈值立刻生效"])

    # 6 例外
    s = slide("管理端大部分时间是空的", "只在四类例外上出现，前三类不占状态位")
    body(s, ["数据对不上（有完工时间但完工照 0 张）",
             "超预算采购（¥480 > 200 线，到货了这条也留到归档）",
             "同一单反复返修（驳回 ≥ 2 次）",
             "学生申请撤销（唯一由人决定、且能改状态的入口）"])

    # 7 技术栈
    s = slide("技术栈：三个进程，零外部服务", "断网可跑，答辩现场不依赖任何云资源")
    body(s, ["后端 Spring Boot 4 + MySQL 8 + MyBatis-Plus（8080）",
             "定级 Python FastAPI + 规则词表（8000，800ms 超时兜底）",
             "前端 Vue 3 + Element Plus + Vite（5173，代理到 8080）",
             "原型另有一套零依赖 HTML/JS，作为“改过不止一版”的物证"])

    # 8 证据
    s = slide("怎么证明它是对的", "四个数字，现场可复跑")
    body(s, ["mvn test → Tests run: 18, Failures: 0（7 白盒 + 7 接口 + 4 AI 兜底）",
             "m1-chain.ps1 13 项 / m2-mysql.ps1 16 项 / m3-ai.ps1 20 项，全过",
             "黑盒用例 30 条：S-01~13 / W-01~11 / A-01~06 / L-01~02",
             "自检台现场推非法事件：409 + 工单一个字没动"])

    # 9 演示路径
    s = slide("5 分钟演示怎么走", "按这条走，不跑题")
    body(s, ["① 学生提交（拍一张、说一句）→ 系统定级、自动派工、给出单号",
             "② 师傅进楼打卡（一次点击 N 条到门事件）→ 完工拍照 → 待验收",
             "③ 学生签字 → 已完成，T1 消失、T2 出现",
             "④ 管理端点「快进 168 小时」→ 待归档变化；自检台点非法事件 → 409"])

    # 10 边界
    s = slide("我们不做，也说得清为什么", "边界写在文档里，不是事后解释")
    body(s, ["不承诺“修得更快、修得更好”，只承诺“说得清、少白跑、能举证”",
             "不做工分/绩效/工资 —— 属人事，不属维修流程",
             "不做管理端审批 —— 审批会让闭环退回 OA",
             "CR-001（维修中→已取消）未批准，接口返回 501，页面按钮置灰"])

    # 11 交付
    s = slide("交付物", "CG01–CG14 齐套")
    body(s, ["文档 8 份（CG02–CG07、CG09、CG10）+ 登记表与打分表 2 份",
             "源代码包 CG08（后端 / 前端 / AI / 原型 / 文档）",
             "总结汇报 CG11、路演海报 CG12、个人体会 CG13",
             "全部文档带封面、目录占位、页眉页脚与页码"])

    # 12 结尾
    s = prs.slides.add_slide(blank)
    bg = s.shapes.add_shape(1, Inches(0), Inches(0), prs.slide_width, prs.slide_height)
    bg.fill.solid()
    bg.fill.fore_color.rgb = NAVY_RGB
    bg.line.fill.background()
    tb = s.shapes.add_textbox(Inches(1.0), Inches(2.9), Inches(11.3), Inches(1.2))
    run = tb.text_frame.paragraphs[0].add_run()
    run.text = "谢谢，请提问"
    run.font.size = Pt(40)
    run.font.bold = True
    run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    tb2 = s.shapes.add_textbox(Inches(1.0), Inches(4.2), Inches(11.3), Inches(0.6))
    r = tb2.text_frame.paragraphs[0].add_run()
    r.text = "现场可复跑：mvn test / m1-chain.ps1 / 自检台 409"
    r.font.size = Pt(16)
    r.font.color.rgb = RGBColor(0xFF, 0x5A, 0x1F)

    path = os.path.join(OUT, "CG11-总结汇报.pptx")
    prs.save(path)
    return path


# ============================================================ CG12 路演海报

def cg12():
    from PIL import Image, ImageDraw, ImageFont

    W, H = 1500, 2126
    im = Image.new("RGB", (W, H), (18, 41, 75))
    d = ImageDraw.Draw(im)

    def font(size, bold=False):
        cands = [
            r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
            r"C:\Windows\Fonts\simhei.ttf",
            r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
        ]
        for c in cands:
            if os.path.exists(c):
                try:
                    return ImageFont.truetype(c, size)
                except Exception:
                    continue
        return ImageFont.load_default()

    # 顶部橙条
    d.rectangle([0, 0, W, 14], fill=(255, 90, 31))

    y = 90
    d.text((100, y), "宿舍维修系统", font=font(78, True), fill=(255, 255, 255))
    y += 110
    d.text((100, y), "拍一张、说一句，剩下的它自己跑", font=font(44), fill=(255, 90, 31))
    y += 80
    d.text((100, y), "人机协同的软件工程项目实训　V5.0　2026-11",
           font=font(26), fill=(199, 214, 234))

    # 四个数字
    y = 420
    nums = [("9", "个状态"), ("13", "条合法转移"), ("130", "种组合"), ("2", "颗计时器")]
    box_w = 300
    for i, (n, label) in enumerate(nums):
        x = 100 + i * (box_w + 20)
        d.rectangle([x, y, x + box_w, y + 190], outline=(70, 105, 160), width=2)
        d.text((x + 28, y + 34), n, font=font(64, True), fill=(255, 255, 255))
        d.text((x + 30, y + 128), label, font=font(24), fill=(151, 178, 214))

    # 三段流程
    y = 690
    d.text((100, y), "它把这条链写成不让它出错的形状", font=font(38, True), fill=(255, 255, 255))
    y += 80
    steps = [
        ("01", "学生提交", "拍照 + 一句话 + 可上门时段；紧急程度由 AI 判，不由学生选"),
        ("02", "系统定级派工", "五类故障 / 三档紧急；命中安全类直接定紧急，置信度低保守升一档"),
        ("03", "师傅到门完工", "一次进楼打卡 = N 条单级到门事件；缺件登记走预算线 200 元"),
        ("04", "学生签字归档", "7 天不响应自动验收；签字后 7 天自动归档，转只读"),
    ]
    for code, title, desc in steps:
        d.text((100, y), code, font=font(30, True), fill=(255, 90, 31))
        d.text((160, y), title, font=font(32, True), fill=(255, 255, 255))
        d.text((162, y + 46), desc, font=font(24), fill=(178, 196, 219))
        y += 120

    # 管理端
    y += 30
    d.rectangle([100, y, W - 100, y + 250], fill=(24, 52, 92))
    d.text((130, y + 32), "管理端大部分时间是空的 —— 只在四类例外上出现",
           font=font(30, True), fill=(255, 255, 255))
    kinds = ["数据对不上", "超预算采购", "同一单反复返修", "学生申请撤销"]
    for i, k in enumerate(kinds):
        x = 130 + (i % 2) * 620
        yy = y + 110 + (i // 2) * 66
        d.text((x, yy), "· " + k, font=font(26), fill=(199, 214, 234))

    # 证据
    y += 300
    d.text((100, y), "怎么证明", font=font(34, True), fill=(255, 255, 255))
    y += 66
    ev = [
        "mvn test → 18 项全绿（7 白盒 + 7 接口 + 4 AI 兜底）",
        "三段脚本验收 13 / 16 / 20 项全过；黑盒用例 30 条",
        "自检台现场推非法事件 → 409，且工单一个字没动",
        "杀掉 AI 服务照样能建单（800ms 兜底，levelSource=FALLBACK）",
    ]
    for e in ev:
        d.text((100, y), "— " + e, font=font(24), fill=(178, 196, 219))
        y += 50

    # 底部
    d.rectangle([0, H - 150, W, H], fill=(255, 90, 31))
    d.text((100, H - 112), "Spring Boot 4 + MySQL 8 + Vue 3 + Python FastAPI　|　断网可跑，三进程启动",
           font=font(26, True), fill=(255, 255, 255))
    d.text((100, H - 66), "第 X 组　组名｜项目经理：（待填）", font=font(22), fill=(18, 41, 75))

    path = os.path.join(OUT, "CG12-路演海报.jpg")
    im.save(path, quality=92)
    return path


DOCS = {"cg01": cg01, "cg14": cg14, "cg13": cg13, "cg11": cg11, "cg12": cg12}

if __name__ == "__main__":
    args = sys.argv[1:] or ["all"]
    names = list(DOCS) if args == ["all"] else args
    for n in names:
        path = DOCS[n]()
        print(f"{n}  {os.path.getsize(path) / 1024:.0f} KB  {path}")
