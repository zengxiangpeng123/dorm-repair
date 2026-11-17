import os, subprocess, time
from PIL import Image

TMP = os.path.join(os.environ["TEMP"], "pv5")
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
SCALE = 2
WIN_W = 520
STAGE_W = 430
OFF_X = int((WIN_W - STAGE_W) / 2 * SCALE)
NAV_BAND = 200

PAGES = [
    ("index", 1500),
    ("lab", 2700),
    ("student/index", 1200),
    ("student/new", 1600),
    ("student/list", 1200),
    ("student/detail", 2200),
    ("worker/index", 1600),
    ("worker/building", 1400),
    ("worker/task", 2000),
    ("worker/parts", 1200),
    ("admin/index", 1600),
]

def shot(page, css_h, out):
    udd = os.path.join(TMP, "udd_" + os.urandom(6).hex())
    url = "file:///" + os.path.join(TMP, page + ".html").replace("\\", "/")
    args = [EDGE, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
            f"--force-device-scale-factor={SCALE}", f"--user-data-dir={udd}",
            f"--screenshot={out}", f"--window-size={WIN_W},{css_h}", url]
    r = subprocess.run(args, capture_output=True, timeout=120)
    for _ in range(60):
        if os.path.exists(out) and os.path.getsize(out) > 1000:
            return True
        time.sleep(0.25)
    return False

def content_bottom(im):
    w, h = im.size
    px = im.load()
    stop = max(1, h - NAV_BAND)
    for y in range(stop, 0, -1):
        c = px[OFF_X + 2, y]
        if any(px[x, y] != c for x in range(OFF_X, min(w, OFF_X + STAGE_W * SCALE), 6)):
            return y
    return max(1, stop)

def trim_bottom(im):
    w, h = im.size
    px = im.load()
    y = h - 1
    while y > 0:
        c = px[OFF_X + 2, y]
        if any(px[x, y] != c for x in range(OFF_X, min(w, OFF_X + STAGE_W * SCALE), 6)):
            break
        y -= 1
    return im.crop((0, 0, w, min(h, y + 12)))

outdir = os.path.join(TMP, "crop")
os.makedirs(outdir, exist_ok=True)

for page, guess in PAGES:
    safe = page.replace("/", "-")
    raw = os.path.join(TMP, f"raw_{safe}.png")
    if os.path.exists(raw):
        os.remove(raw)
    if not shot(page, guess, raw):
        print("FAIL", page)
        continue
    last = content_bottom(Image.open(raw).convert("RGB"))
    css_h = int(last / SCALE) + 136
    final = os.path.join(TMP, f"shot_{safe}.png")
    if os.path.exists(final):
        os.remove(final)
    if not shot(page, css_h, final):
        print("FAIL pass2", page)
        continue
    im = trim_bottom(Image.open(final).convert("RGB"))
    im = im.crop((OFF_X, 0, OFF_X + STAGE_W * SCALE, im.size[1]))
    im.save(os.path.join(outdir, f"{safe}.png"))
    print(f"{safe:18s} css_h={css_h:5d}  -> {im.size}")
