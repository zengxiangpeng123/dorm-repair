# 桌面页截图（入口页 / 自检台 / 管理端）：宽度 1440，不裁设备壳，只裁掉底部空白
import os, subprocess, sys, tempfile, uuid, time
from PIL import Image

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
SRC = os.environ.get("PV5_SRC", r"D:\a_student\ai应用工程师课程\理论-龙哥\项目开发-fix\宿舍维修系统\prototype")
TMP = os.path.join(tempfile.gettempdir(), "pv5desk")
OUT = os.path.join(tempfile.gettempdir(), "pv5", "crop")
W = int(os.environ.get("DESK_W", "1440"))
BG = (244, 246, 249)
BG2 = (233, 238, 245)   # 文档高度小于窗口时，Edge 在下方补的底色，也要当空

PAGES = [
    ("index.html", 1440, 1120),
    ("lab.html", 1440, 1080),
    ("admin/index.html", 1440, 1500),
]


def shoot(url_file, w, h, out):
    # 关键：先删掉旧图，否则轮询会立刻读到上一轮的残图（看起来"渲染没生效"）
    if os.path.exists(out):
        os.remove(out)
    udd = os.path.join(TMP, "udd_" + uuid.uuid4().hex)
    cmd = [
        EDGE, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
        "--force-device-scale-factor=1",
        "--user-data-dir=" + udd,
        "--screenshot=" + out,
        "--window-size=%d,%d" % (w, h),
        "file:///" + url_file,
    ]
    subprocess.run(cmd, capture_output=True)
    for _ in range(80):
        if os.path.exists(out) and os.path.getsize(out) > 2000:
            try:
                im = Image.open(out)
                im.load()
                return im
            except Exception:
                pass
        time.sleep(0.2)
    raise RuntimeError("截图没生成: " + out)


def last_content_row(im):
    # 背景有两种：body 的 #f4f6f9，和窗口比内容高时 Edge 补的 #e9eef5
    # 左侧深色导航整列到底都有像素，所以只从 x=280 开始采样
    px = im.convert("RGB").load()
    w, h = im.size
    for y in range(h - 1, -1, -1):
        for x in range(280, w, 3):
            p = px[x, y]
            if all(abs(p[i] - BG[i]) > 6 for i in range(3)) and all(abs(p[i] - BG2[i]) > 6 for i in range(3)):
                return y
    return h - 1


def main():
    os.makedirs(TMP, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    for rel, w, guess in PAGES:
        src = os.path.join(SRC, rel.replace("/", os.sep))
        name = rel.replace("/", "-").replace(".html", "").replace("index-admin", "admin-index")
        if rel == "admin/index.html":
            name = "admin-index"
        out = os.path.join(OUT, name + ".png")
        im = shoot(src.replace("\\", "/"), w, guess, os.path.join(TMP, "p.png"))
        last = last_content_row(im)
        if last >= guess - 40:                      # 可能被截断，加高重截
            im = shoot(src.replace("\\", "/"), w, last + 220, os.path.join(TMP, "p2.png"))
            last = last_content_row(im)
        im = im.crop((0, 0, im.size[0], min(im.size[1], last + 40)))
        im.save(out)
        print("saved", name, im.size)


if __name__ == "__main__":
    main()
