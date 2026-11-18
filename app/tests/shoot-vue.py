"""给 Vue 页面出图：Edge 无头 + 两遍法量高度 + 裁白边。

用法（在项目根跑）：
    python app/tests/shoot-vue.py            # 全部页
    python app/tests/shoot-vue.py /admin     # 只出一页

依赖：Pillow。Edge 路径写死在 EDGE，换机器改这一行。
坑（都在这里踩过）：
  1. 必须先删旧图，否则轮询 os.path.exists 会立刻读到上一轮的文件；
  2. 每次都要新的 --user-data-dir，复用会静默失败；
  3. Vue 是客户端渲染，必须给 --virtual-time-budget，否则截到"加载中"；
  4. 手机页窗口最小 520（Edge 夹到 479 会裁右边缘），桌面页 1440。
"""
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
import uuid
from PIL import Image

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
BASE = "http://localhost:5173"
OUT = os.path.join(tempfile.gettempdir(), "pv5", "vue")
WORK = os.path.join(tempfile.gettempdir(), "pv5", "vuework")

PHONE_W = 520      # 窗口宽（stage 430 居中）
PHONE_CSS = 430
DSF = 2
BG_PHONE = (239, 242, 245)   # tokens.css 的 --bg #eff2f5
BG_DESK = (239, 242, 245)
BG2_DESK = (244, 246, 249)   # .desk-page 的 #f4f6f9


def api(path):
    with urllib.request.urlopen(BASE + path, timeout=8) as r:
        return json.loads(r.read().decode("utf-8"))["data"]


def shot(url, out, width, guess, desk=False):
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)
    dsf = 1 if desk else DSF
    for attempt in range(2):
        profile = os.path.join(tempfile.gettempdir(), "pv5", "prof-" + uuid.uuid4().hex)
        args = [
            EDGE, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
            f"--force-device-scale-factor={dsf}",
            "--virtual-time-budget=6000",
            f"--window-size={width},{guess}",
            f"--user-data-dir={profile}",
            f"--screenshot={out}",
            url,
        ]
        subprocess.run(args, capture_output=True, timeout=120)
        # Edge 的 --screenshot 是异步落盘的：进程退了文件可能还要 3 秒才出现。
        # 只判 os.path.exists 会误报失败（曾经因此以为"我改的代码没生效"）。
        prev = -1
        for _ in range(120):
            _sleep(0.25)
            if os.path.exists(out):
                size = os.path.getsize(out)
                if size > 2000 and size == prev:
                    break
                prev = size
        if not os.path.exists(out):
            continue
        im = Image.open(out).convert("RGB")
        last = last_row(im, 0 if not desk else 300, im.width, desk)
        if last >= im.height - 30 and attempt == 0:
            guess = int(guess * 1.7)
            continue
        break
    im = im.crop((0, 0, im.width, min(im.height, last + (14 if not desk else 40))))
    if not desk:
        x0 = int((width - PHONE_CSS) / 2 * dsf)
        x1 = x0 + PHONE_CSS * dsf
        im = im.crop((x0, 0, min(x1, im.width), im.height))
    im.save(out)
    return im.size


def _sleep(s):
    import time
    time.sleep(s)


def last_row(im, x_from, width, desk):
    px = im.load()
    bgs = [BG_DESK, BG2_DESK] if desk else [BG_PHONE]
    step = 3
    for y in range(im.height - 1, -1, -step):
        for x in range(x_from, width, 7):
            c = px[x, y]
            if all(abs(c[0] - b[0]) + abs(c[1] - b[1]) + abs(c[2] - b[2]) > 12 for b in bgs):
                return y
    return im.height - 30


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orders = api("/api/v1/orders?pageSize=50")["items"]
    by = {o["status"]: o["no"] for o in orders}
    first = orders[0]["no"]
    # 截图要用"有戏"的单：师傅作业页用维修中，学生详情用待验收
    task_no = by.get("REPAIRING") or by.get("ASSIGNED") or first
    detail_no = by.get("VERIFYING") or first

    pages = [
        ("/", "entry", 1440, 1400, True),
        ("/student", "student-home", PHONE_W, 1400, False),
        ("/student/new", "student-new", PHONE_W, 1800, False),
        ("/student/list", "student-list", PHONE_W, 1200, False),
        (f"/student/detail?no={detail_no}", "student-detail", PHONE_W, 2400, False),
        ("/worker", "worker-home", PHONE_W, 1600, False),
        ("/worker/building", "worker-building", PHONE_W, 1800, False),
        (f"/worker/task?no={task_no}", "worker-task", PHONE_W, 2000, False),
        ("/worker/parts", "worker-parts", PHONE_W, 1400, False),
        ("/admin", "admin", 1440, 1800, True),
        ("/lab", "lab", 1440, 1800, True),
    ]
    for path, name, width, guess, desk in pages:
        if only and only not in path:
            continue
        out = os.path.join(OUT, name + ".png")
        sep = "&" if "?" in path else "?"
        url = BASE + path + sep + "shot=1"
        try:
            size = shot(url, out, width, guess, desk)
            print(f"{name:16s} {size[0]}x{size[1]}  {out}")
        except Exception as e:  # noqa: BLE001
            print(f"{name:16s} FAIL {e}")


if __name__ == "__main__":
    main()
