# Βρίσκει υποψήφιες φωτογραφίες ζώων στο Wikimedia Commons (Featured/Quality)
# και φτιάχνει ένα «φύλλο επαφής» ανά ζώο για οπτικό έλεγχο.
import json, os, time, io, urllib.request, urllib.parse, urllib.error
from PIL import Image, ImageDraw, ImageFont

UA = {"User-Agent": "KidsCardBuild/1.0 (https://github.com/Zaoder/Kids-card)"}
TERMS = {
 "dog": ["dog", "Labrador Retriever"], "cat": ["cat", "domestic cat"], "cow": ["cow", "Holstein cattle"],
 "sheep": ["sheep"], "goat": ["goat", "domestic goat"], "horse": ["horse"], "pig": ["domestic pig", "pig"],
 "chicken": ["hen", "chicken"], "rooster": ["rooster", "cock chicken"], "duck": ["mallard"],
 "lion": ["lion"], "elephant": ["African elephant", "elephant"], "monkey": ["chimpanzee"],
 "frog": ["frog"], "owl": ["tawny owl", "owl"], "wolf": ["gray wolf", "wolf"],
}
if os.environ.get("ONLY"):
    TERMS = {os.environ["ONLY"]: os.environ["TERMS"].split("|")}
CATS = ["Featured_pictures_on_Wikimedia_Commons", "Quality_images"]
N = 9

def get(url):
    for i in range(5):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or i == 4: raise
            time.sleep(15 * (i + 1))

def api(params):
    params.update(format="json", formatversion="2")
    return json.loads(get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)))

os.makedirs("probe", exist_ok=True)
allc = {}
for aid, terms in TERMS.items():
    cands, seen = [], set()
    for cat in CATS:
        for t in terms:
            if len(cands) >= N: break
            r = api({"action": "query", "generator": "search", "gsrnamespace": "6", "gsrlimit": "12",
                     "gsrsearch": f'intitle:"{t}" incategory:{cat} filetype:bitmap',
                     "prop": "imageinfo", "iiprop": "url|size|extmetadata", "iiurlwidth": "420"})
            for p in (r.get("query") or {}).get("pages", []):
                ii = (p.get("imageinfo") or [{}])[0]
                if p["title"] in seen or not ii.get("thumburl"): continue
                if ii.get("width", 0) < 1000: continue
                seen.add(p["title"])
                em = ii.get("extmetadata", {})
                cands.append({"title": p["title"], "thumb": ii["thumburl"], "w": ii["width"], "h": ii["height"],
                              "lic": (em.get("LicenseShortName") or {}).get("value", "?")})
                if len(cands) >= N: break
            time.sleep(1)
    allc[aid] = cands
    # φύλλο επαφής 3x3
    S = 300
    sheet = Image.new("RGB", (3 * S, 3 * S), "white")
    d = ImageDraw.Draw(sheet)
    for i, c in enumerate(cands):
        try:
            im = Image.open(io.BytesIO(get(c["thumb"]))).convert("RGB")
            im.thumbnail((S - 6, S - 6))
            x, y = (i % 3) * S, (i // 3) * S
            sheet.paste(im, (x + (S - im.width) // 2, y + (S - im.height) // 2))
            d.rectangle([x + 2, y + 2, x + 38, y + 36], fill="red")
            d.text((x + 8, y + 4), str(i), fill="white", font=ImageFont.load_default(size=28))
        except Exception as e:
            print("thumb fail", aid, i, e)
        time.sleep(0.5)
    sheet.save(f"probe/{aid}.jpg", quality=80)
    print(aid, len(cands))
json.dump(allc, open("probe/candidates.json", "w"), ensure_ascii=False, indent=1)
