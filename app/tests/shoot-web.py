"""
给 app/web 的真页面截图（跑在 HTTP 上，不是 file://）。
用法：python app/tests/shoot-web.py [name ...]
  - 手机页：窗口 520 宽 + dsf=2，裁出中间 430 的 stage 区域
  - 桌面页：窗口 1440 宽 + dsf=1，只裁掉底部空白
页面加 ?as=账号 自动登录，?shot=1 把底部导航放回文档流（否则整页截图里没有它）。
"""
import json, os, subprocess, sys, tempfile, time, urllib.request, uuid
from PIL import Image

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
BASE = os.environ.get("APP_BASE", "http://localhost:5173")
TMP = os.path.join(tempfile.gettempdir(), "pv5web")
OUT = os.path.join(tempfile.gettempdir(), "pv5", "crop")
BG = (246, 247, 249)          # --bg
PAD = 60                      # 内容下方多留一点


def api(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + "/api/v1" + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode())["data"]


def discover():
    """挑 S001 自己名下的单截图（用别人的单会 403，截出来只有一行错误）"""
    tok = api("POST", "/login", {"role": "student", "id": "S001"})["token"]
    items = api("GET", "/my/orders?status=ALL&pageSize=50", token=tok)["items"]
    pick = {}
    for o in items:
        pick.setdefault(o["status"], o["no"])
    return {
        "verifying": pick.get("VERIFYING"),
        "repairing": pick.get("REPAIRING"),
        "assigned": pick.get("ASSIGNED"),
        "parts": pick.get("PARTS"),
        "done": pick.get("DONE"),
    }


def shoot(url, w, h, out, dsf):
    if os.path.exists(out):
        os.remove(out)                      # 关键：不删会读到上一轮的残图
    udd = os.path.join(TMP, "udd_" + uuid.uuid4().hex)
    cmd = [EDGE, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
           "--force-device-scale-factor=%d" % dsf,
           "--user-data-dir=" + udd,
           "--screenshot=" + out,
           "--window-size=%d,%d" % (w, h),
           url]
    subprocess.run(cmd, capture_output=True)
    for _ in range(120):
        if os.path.exists(out) and os.path.getsize(out) > 2000:
            try:
                im = Image.open(out); im.load(); return im
            except Exception:
                pass
        time.sleep(0.2)
    raise RuntimeError("截图没生成: " + out)


def last_row(im, x0, x1, y0=0):
    px = im.convert("RGB").load()
    w, h = im.size
    for y in range(h - 1, y0, -1):
        for x in range(x0, min(x1, w), 3):
            p = px[x, y]
            if any(abs(p[i] - BG[i]) > 7 for i in range(3)):
                return y
    return y0


def shot(url_path, mode="m", guess=None, name=None):
    if mode == "m":
        w, dsf = 520, 2
        guess = guess or 1400
        url = "%s%s%sshot=1" % (BASE, url_path, "&" if "?" in url_path else "?")
        x0, x1 = 90, 950                       # 520 窗口里 430 stage 的左右边界（dsf=2）
        im = shoot(url, w, guess, os.path.join(TMP, "p.png"), dsf)
        last = last_row(im, x0 + 6, x1 - 6)
        if last >= im.size[1] - 30:            # 顶到底了 = 被截断，加高重截
            im = shoot(url, w, int(guess * 1.7), os.path.join(TMP, "p2.png"), dsf)
            last = last_row(im, x0 + 6, x1 - 6)
        im = im.crop((x0, 0, x1, min(im.size[1], last + 14)))
    else:
        w, dsf = 1440, 1
        guess = guess or 1200
        url = "%s%s%sshot=1" % (BASE, url_path, "&" if "?" in url_path else "?")
        im = shoot(url, w, guess, os.path.join(TMP, "d.png"), dsf)
        last = last_row(im, 0, im.size[0])
        if last >= im.size[1] - 30:
            im = shoot(url, w, int(guess * 1.7), os.path.join(TMP, "d2.png"), dsf)
            last = last_row(im, 0, im.size[0])
        im = im.crop((0, 0, im.size[0], min(im.size[1], last + PAD)))

    dst = os.path.join(OUT, (name or url_path.strip("/").replace("/", "-").replace(".html", "")) + ".png")
    im.save(dst)
    print("saved %-28s %s" % (os.path.basename(dst), im.size))
    return dst


def main():
    os.makedirs(TMP, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    d = discover()
    PAGES = [
        ("student-index", "/student/index.html?as=S001", "m", 1300),
        ("student-new", "/student/new.html?as=S001", "m", 1500),
        ("student-list", "/student/list.html?as=S001", "m", 1200),
        ("student-detail", "/student/detail.html?as=S001&no=" + str(d["verifying"]), "m", 1700),
        ("worker-index", "/worker/index.html?as=W0428", "m", 1400),
        ("worker-building", "/worker/building.html?as=W0428", "m", 1500),
        ("worker-task", "/worker/task.html?as=W0428", "m", 1500),
        ("worker-parts", "/worker/parts.html?as=W0428", "m", 1200),
        ("admin", "/admin/?as=A001", "d", 1500),
        ("lab", "/lab.html", "d", 1200),
        ("index", "/index.html", "d", 1000),
    ]
    want = sys.argv[1:]
    for name, path, mode, guess in PAGES:
        if want and name not in want:
            continue
        try:
            shot(path, mode, guess, name)
        except Exception as e:
            print("FAIL", name, e)


if __name__ == "__main__":
    main()
