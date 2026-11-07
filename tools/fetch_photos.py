# Κατεβάζει τις επιλεγμένες φωτογραφίες ζώων από το Wikimedia Commons
# (ελεγμένες μία-μία οπτικά), σε πλάτος 1080px, και γράφει τα credits.
import json, os, re, time, html, io, urllib.request, urllib.parse, urllib.error
from PIL import Image

UA = {"User-Agent": "KidsCardBuild/1.0 (https://github.com/Zaoder/Kids-card)"}
OUT = "app/src/main/assets/photos"
PHOTOS = {
 "dog": "Samoyed dog two year old female dllu.jpg",
 "cat": "Cat November 2010-1a.jpg",
 "cow": "Cow (Fleckvieh breed) Oeschinensee Slaunger 2009-07-07.jpg",
 "sheep": "A sheep with a couple of lambs in Breidsæterdalen, 2013 June.jpg",
 "goat": "African dwarf goat baby.JPG",
 "horse": "Domestic horse at Suryachaur and the mountains in the back1.jpg",
 "pig": "Pig farm Vampula 1.jpg",
 "rooster": "Feral rooster on Kauaʻi.jpg",
 "duck": "Male mallard duck 2.jpg",
 "lion": "Lion (Panthera leo) male 6y.jpg",
 "elephant": "African elephant (Loxodonta africana) 2.jpg",
 "monkey": "016 Alpha male chimpanzee walking at Kibale forest National Park Photo by Giles Laurent.jpg",
 "frog": "Red-eyed Tree Frog - Litoria chloris edit1.jpg",
 "owl": "Eastern Screech Owl.jpg",
 "wolf": "Front view of a resting Canis lupus ssp.jpg",
 "chicken": "Gelbes Sachsenhuhn im Schnee (Henne).jpg"
}

def get(url):
    for i in range(5):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90).read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or i == 4: raise
            time.sleep(15 * (i + 1))

def strip(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()

os.makedirs(OUT, exist_ok=True)
credits, failed = [], []
for aid, title in PHOTOS.items():
    time.sleep(2)
    try:
        r = json.loads(get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({
            "action": "query", "titles": "File:" + title, "prop": "imageinfo", "format": "json",
            "formatversion": "2", "iiprop": "url|extmetadata", "iiurlwidth": "1080"})))
        ii = r["query"]["pages"][0]["imageinfo"][0]
        em = ii.get("extmetadata", {})
        im = Image.open(io.BytesIO(get(ii["thumburl"]))).convert("RGB")
        im.thumbnail((1080, 1080))
        im.save(f"{OUT}/{aid}.jpg", quality=82, optimize=True, progressive=True)
        credits.append({"id": aid, "file": title, "page": ii.get("descriptionurl"),
                        "license": strip((em.get("LicenseShortName") or {}).get("value")),
                        "artist": strip((em.get("Artist") or {}).get("value"))[:120]})
        print(f"OK   {aid:<9} {im.size} {os.path.getsize(f'{OUT}/{aid}.jpg')//1024}KB {title}")
    except Exception as e:
        failed.append(aid); print(f"FAIL {aid}: {e}")
json.dump(credits, open("app/src/main/assets/photo_credits.json", "w"), ensure_ascii=False, indent=1)
with open("CREDITS.md", "a") as f:
    f.write("\n# Φωτογραφίες ζώων\n\nΌλες από το Wikimedia Commons:\n\n")
    for c in credits:
        f.write(f"- **{c['id']}**: [{c['file']}]({c['page']}) - {c['artist'] or 'άγνωστος'} - {c['license']}\n")
print("failed:", failed)
