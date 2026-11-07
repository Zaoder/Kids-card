# Ψάχνει ήχους ζώων στο Wikimedia Commons και τυπώνει υποψήφια αρχεία
import json, urllib.request, urllib.parse
UA = {"User-Agent": "KidsCardBuild/1.0 (https://github.com/Zaoder/Kids-card)"}
Q = {
 "dog": ["dog barking", "dog bark"], "cat": ["cat meow", "meowing"],
 "cow": ["cow moo", "cattle mooing"], "sheep": ["sheep bleat", "sheep baa"],
 "goat": ["goat bleat", "goat"], "horse": ["horse neigh", "horse whinny"],
 "pig": ["pig grunt", "pig oink"], "chicken": ["chicken clucking", "hen cluck"],
 "rooster": ["rooster crowing", "cock-a-doodle"], "duck": ["duck quack", "mallard quack"],
 "lion": ["lion roar", "lion"], "elephant": ["elephant trumpet", "elephant"],
 "monkey": ["monkey call", "chimpanzee"], "frog": ["frog croak", "frog call"],
 "owl": ["owl hoot", "tawny owl"], "wolf": ["wolf howl", "wolf howling"],
}
def api(params):
    params.update(format="json", formatversion="2")
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30))
out = {}
for animal, qs in Q.items():
    seen = {}
    for q in qs:
        try:
            r = api({"action": "query", "generator": "search", "gsrsearch": q + " filetype:audio",
                     "gsrnamespace": "6", "gsrlimit": "10", "prop": "imageinfo",
                     "iiprop": "url|mime|size|extmetadata|metadata"})
        except Exception as e:
            print("ERR", animal, q, e); continue
        print("  q:", q, "hits:", len((r.get("query") or {}).get("pages", [])), str(r)[:200] if "query" not in r else "")
        for p in (r.get("query") or {}).get("pages", []):
            ii = (p.get("imageinfo") or [{}])[0]
            md = {m["name"]: m.get("value") for m in (ii.get("metadata") or []) if isinstance(m, dict)}
            length = md.get("length") or md.get("playtime_seconds")
            em = ii.get("extmetadata") or {}
            lic = (em.get("LicenseShortName") or {}).get("value", "?")
            art = (em.get("Artist") or {}).get("value", "?")
            seen[p["title"]] = {"title": p["title"], "len": length, "mime": ii.get("mime"),
                                "size": ii.get("size"), "lic": lic, "artist": art[:80], "url": ii.get("url")}
    out[animal] = list(seen.values())
    print("=====", animal)
    for c in out[animal]:
        print(f'  {c["len"]!s:>8} | {c["lic"]:<14} | {c["mime"]} | {c["size"]} | {c["title"]}')
json.dump(out, open("probe.json", "w"), ensure_ascii=False, indent=1)
