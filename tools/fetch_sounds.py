# Κατεβάζει τους ήχους των ζώων από το Wikimedia Commons, τους κόβει σε λίγα
# δευτερόλεπτα και γράφει τα credits (άδειες/δημιουργοί).
import json, os, re, subprocess, urllib.request, urllib.parse, html

UA = {"User-Agent": "KidsCardBuild/1.0 (https://github.com/Zaoder/Kids-card)"}
OUT = "app/src/main/assets/sounds"
# id: (αρχείο στο Commons, μέγιστη διάρκεια σε δευτ., επαναλήψεις)
SOUNDS = {
    "dog":      ("Barking of a dog 2.ogg", 3.5, 1),
    "cat":      ("Meow of a Siamese cat - freemaster2.wav", 3, 1),
    "cow":      ("Single Cow Moo.ogg", 4, 1),
    "sheep":    ("Sheep bleating.ogg", 3.5, 1),
    "goat":     ("Herd of goats bleating.ogg", 3.5, 1),
    "horse":    ("Wiehern.ogg", 3, 1),
    "pig":      ("Pig grunt - Erdie.ogg", 3, 3),
    "chicken":  ("Hen announcing shes lain an egg.ogg", 3.5, 1),
    "rooster":  ("Rooster crowing.ogg", 4, 1),
    "duck":     ("Anas platyrhynchos - Mallard - XC62258.ogg", 3.5, 1),
    "lion":     ("Lion raring-sound1TamilNadu178.ogg", 4, 1),
    "elephant": ("Elephant voice - trumpeting.ogg", 3, 1),
    "monkey":   ("Pant-hoot call made by a male chimpanzee.ogg", 4.5, 1),
    "frog":     ("Single Frog Croak.oga", 3.5, 1),
    "owl":      ("Strix aluco male.oga", 4.5, 1),
    "wolf":     ("Wolf howls.ogg", 5, 1),
}

def api(params):
    params.update(format="json", formatversion="2")
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60))

def strip(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()

os.makedirs(OUT, exist_ok=True)
credits, failed = [], []
for aid, (title, maxlen, reps) in SOUNDS.items():
    try:
        r = api({"action": "query", "titles": "File:" + title, "prop": "imageinfo",
                 "iiprop": "url|extmetadata"})
        p = r["query"]["pages"][0]
        ii = p["imageinfo"][0]
        em = ii.get("extmetadata", {})
        src = f"/tmp/{aid}.src"
        with urllib.request.urlopen(urllib.request.Request(ii["url"], headers=UA), timeout=120) as f, open(src, "wb") as o:
            o.write(f.read())
        # Αφαίρεση αρχικής σιωπής, κόψιμο, σβήσιμο στο τέλος, κανονικοποίηση έντασης
        fade_st = max(0.1, maxlen - 0.4)
        af = (f"silenceremove=start_periods=1:start_threshold=-40dB:start_silence=0.05,"
              f"atrim=0:{maxlen},asetpts=N/SR/TB,afade=t=out:st={fade_st}:d=0.4,"
              f"loudnorm=I=-16:TP=-1.5:LRA=11")
        if reps > 1:
            af = f"aloop=loop={reps-1}:size=2e9," + af if False else af
        cmd = ["ffmpeg", "-y", "-loglevel", "error"]
        if reps > 1:
            cmd += ["-stream_loop", str(reps - 1)]
        cmd += ["-i", src, "-ac", "1", "-ar", "44100", "-af", af, "-c:a", "libvorbis", "-q:a", "4", f"{OUT}/{aid}.ogg"]
        subprocess.run(cmd, check=True)
        dur = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                              f"{OUT}/{aid}.ogg"], capture_output=True, text=True).stdout.strip()
        credits.append({"id": aid, "file": title, "page": ii.get("descriptionurl"),
                        "license": strip((em.get("LicenseShortName") or {}).get("value")),
                        "artist": strip((em.get("Artist") or {}).get("value"))[:120]})
        print(f"OK   {aid:<9} {dur:>8}s  {credits[-1]['license']:<14} {title}")
    except Exception as e:
        failed.append(aid)
        print(f"FAIL {aid:<9} {title}: {e}")

json.dump(credits, open("app/src/main/assets/credits.json", "w"), ensure_ascii=False, indent=1)
with open("CREDITS.md", "w") as f:
    f.write("# Ήχοι ζώων\n\nΌλοι οι ήχοι προέρχονται από το Wikimedia Commons:\n\n")
    for c in credits:
        f.write(f"- **{c['id']}**: [{c['file']}]({c['page']}) - {c['artist'] or 'άγνωστος'} - {c['license']}\n")
print("failed:", failed)
