"""登录后的 Vue 页面截图：后端 8082、前端 5173 必须已启动。

前置：数据库里至少有一条待管理员审查的返修申请。
运行：uv run --with playwright python app/tests/shoot-vue.py
输出：app/screens/vue/*.png
"""
import json
import os
import urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright

WEB = "http://127.0.0.1:5173"
API = "http://127.0.0.1:8082/api/v1"
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "screens" / "vue"
EDGE = os.environ.get("BROWSER_PATH", r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")

def call(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(API + path, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))["data"]

def login(account):
    return call("POST", "/auth/login", {"account": account, "password": "dorm@2026"})

def ctx(browser, width, height, session=None):
    c = browser.new_context(viewport={"width": width, "height": height}, device_scale_factor=1)
    if session:
        payload = json.dumps(session, ensure_ascii=False)
        c.add_init_script("localStorage.setItem('dorm.session', " + json.dumps(payload, ensure_ascii=False) + ");")
    return c

def snap(page, name, full=False, fit=True):
    page.wait_for_timeout(500)
    if fit:
        h = page.evaluate("""() => { const el = document.querySelector('.stage') || document.querySelector('.desk-page') || document.body; return Math.ceil(el.getBoundingClientRect().bottom + window.scrollY); }""")
        h = max(500, min(int(h), 3200))
        page.set_viewport_size({"width": page.viewport_size["width"], "height": h})
        page.wait_for_timeout(250)
    page.screenshot(path=str(OUT / name), full_page=full, animations="disabled")
    print(name, page.viewport_size)

def goto(page, path):
    sep = "&" if "?" in path else "?"
    page.goto(WEB + path + sep + "shot=1", wait_until="networkidle", timeout=30000)
    page.wait_for_timeout(500)

OUT.mkdir(parents=True, exist_ok=True)
student = login("S001")
worker = login("W0428")
admin = login("A001")
queue = call("GET", "/admin/rework-reviews", None, admin)
if not queue.get("items"):
    raise RuntimeError("截图前需要一条待审查返修申请")
PENDING_NO = queue["items"][0]["no"]
errors = []

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=EDGE, headless=True, args=["--disable-gpu", "--no-sandbox"])

    c = ctx(browser, 1440, 1200)
    page = c.new_page()
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
    goto(page, "/login")
    snap(page, "login.png")
    goto(page, "/")
    snap(page, "entry.png")
    c.close()

    c = ctx(browser, 430, 1200, student)
    page = c.new_page()
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
    goto(page, "/student")
    snap(page, "student-home.png")
    goto(page, "/student/new")
    snap(page, "student-new.png")
    goto(page, "/student/list")
    snap(page, "student-list.png")
    goto(page, "/student/detail?no=" + PENDING_NO)
    snap(page, "student-review-pending.png")
    snap(page, "student-detail.png")
    c.close()

    c = ctx(browser, 430, 1200, worker)
    page = c.new_page()
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
    goto(page, "/worker")
    snap(page, "worker-home.png")
    goto(page, "/worker/building")
    snap(page, "worker-building.png")
    goto(page, "/worker/task?no=" + PENDING_NO)
    snap(page, "worker-task.png")
    goto(page, "/worker/parts")
    snap(page, "worker-parts.png")
    c.close()

    c = ctx(browser, 1440, 1200, admin)
    page = c.new_page()
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
    goto(page, "/admin")
    page.locator(".review-panel").wait_for(timeout=10000)
    snap(page, "admin.png", fit=False)
    snap(page, "admin-review-pending.png", fit=False)
    page.get_by_role("button", name="确认返修").first.click()
    page.locator(".rv-sheet").wait_for(timeout=5000)
    snap(page, "admin-review-modal.png", fit=False)
    goto(page, "/lab")
    snap(page, "lab.png", fit=False)
    c.close()

    browser.close()

print("CONSOLE_ERRORS", json.dumps(errors, ensure_ascii=False))
