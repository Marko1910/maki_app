"""Ruma brand learning: teaches the detector the labels of Peruvian brands from open-licence web images.

    python tools/brand-learn/brand_learn.py collect [--per-brand 6] [--brand "Inca Kola"]
    python tools/brand-learn/brand_learn.py learn   [--limit 300]
    python tools/brand-learn/brand_learn.py report
    python tools/brand-learn/brand_learn.py apply   [--write]

collect  Searches every brand of supabase/functions/detect-material/brands.ts on Open Food Facts and
         Wikimedia Commons (both open licences: the source and licence of each image go to manifest.jsonl)
         and keeps a JPEG copy in ../datasets/marcas/<marca>/, outside the repo.
learn    Sends each image to detect-material {"mode":"learn"} (STAGING, operator account): the same prompt
         and AI the family's phone uses. Results go to results.jsonl; already read images are skipped.
report   Per brand: images with a container, read as the right brand, as another brand, or unnamed.
         Writes reporte.md and sugerencias.json: label words the AI copied ("texto") on two or more images
         of one brand, that no other brand uses and the catalog doesn't have yet.
apply    Shows those words per brand; with --write adds them to the brand's "texts" in brands.ts. Then bump
         PIPELINE_VERSION and deploy detect-material: brandFromText names a brand from these words.

The dataset also seeds the future own model (YOLO): every image keeps its true brand and what the AI read.
"""
import argparse, base64, io, json, re, sys, time, unicodedata, urllib.error, urllib.parse, urllib.request
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BRANDS_TS = ROOT / "supabase/functions/detect-material/brands.ts"
DATA = ROOT.parent / "datasets" / "marcas"
MANIFEST, RESULTS = DATA / "manifest.jsonl", DATA / "results.jsonl"
UA = "Ruma-BrandLearn/0.1 (recycling pilot, Peru)"
OFF_PAUSE_S = 2      # search.openfoodfacts.org: be polite
# A brand name alone brings noise ("San Luis" = Paris' cathedral, "Loa" = a volcano and a nougat bar):
# only drinks and containers are kept.
DRINK = re.compile(r"botell|bottle|lata|can|agua|water|gaseosa|soda|drink|bebid|cerveza|beer|envase|refresco|jugo|juice|néctar|nectar|pet|ml|lt?|litro", re.I)
LEARN_PAUSE_S = 4    # stay well under the free vision tiers
# Words on every bottle: they never name a brand.
GENERIC = {"agua", "mineral", "sin gas", "con gas", "gaseosa", "bebida", "litro", "litros", "ml", "lt", "botella",
           "natural", "original", "sabor", "zero", "light", "nuevo", "nueva", "peru", "hecho en peru", "contenido",
           "alcalina", "mesa", "limon", "naranja", "cola", "regular", "kids", "pack", "lata",
           # read on the first run (2026-11-18): flavours and English words any brand prints
           "drink", "grape", "tonic", "brasil", "uva", "manzana", "pina", "energy", "sugar", "free", "tea", "juice", "water"}


def brands():
    out = []
    for line in BRANDS_TS.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith('{"name"'):
            out.append(json.loads(line.strip().rstrip(",")))
    return out


def norm(s):
    s = unicodedata.normalize("NFKD", str(s)).encode("ascii", "ignore").decode().lower()
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9 ]", " ", s)).strip()


def slug(s):
    return norm(s).replace(" ", "-")


def get(url, raw=False, tries=3):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=40) as r:
                body = r.read()
                return body if raw else json.loads(body)
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as e:
            if i == tries - 1:
                print(f"  ! {url[:90]}… {e}")
                return None
            time.sleep(30 * (i + 1))   # 429/503: both sites ask to slow down


def to_jpeg(data):
    from PIL import Image  # already used by the repo's device scripts
    im = Image.open(io.BytesIO(data)).convert("RGB")
    im.thumbnail((1024, 1024))
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=88)
    return buf.getvalue()


def off_images(b):
    """Open Food Facts through its search service (the v2 API answered 503 most of the day). Peru first:
    a label sold here is the one to learn, but an Inca Kola looks the same in Madrid."""
    q = urllib.parse.quote(f'brands_tags:"{slug(b["name"])}"')
    d = get(f"https://search.openfoodfacts.org/search?q={q}&page_size=40"
            "&fields=code,product_name,image_front_url,countries_tags,categories_tags")
    time.sleep(OFF_PAUSE_S)
    hits = sorted((d or {}).get("hits", []), key=lambda h: "en:peru" not in (h.get("countries_tags") or []))
    return [{"source": "openfoodfacts", "url": h["image_front_url"], "title": h.get("product_name") or "",
             "page": f"https://world.openfoodfacts.org/product/{h['code']}",
             "license": "CC BY-SA 3.0 (Open Food Facts contributors)"}
            for h in hits if h.get("image_front_url")
            and ("en:beverages" in (h.get("categories_tags") or []) or DRINK.search(h.get("product_name") or ""))]


def commons_images(b):
    # Brand in the file title: a free-text search returned highways and video games for "Inca Kola".
    q = urllib.parse.quote(f'intitle:"{b["name"]}" filetype:bitmap')
    d = get("https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6"
            f"&gsrlimit=15&gsrsearch={q}&prop=imageinfo&iiprop=url|extmetadata&iiurlwidth=1024")
    out = []
    for page in ((d or {}).get("query") or {}).get("pages", {}).values():
        info = (page.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata") or {}
        lic = (meta.get("LicenseShortName") or {}).get("value", "")
        if info.get("thumburl") and lic and DRINK.search(page.get("title", "")):
            out.append({"source": "wikimedia", "url": info["thumburl"], "title": page.get("title", ""),
                        "page": info.get("descriptionurl", ""), "license": f"{lic} (Wikimedia Commons)"})
    return out


def collect(args):
    DATA.mkdir(parents=True, exist_ok=True)
    rows = [json.loads(l) for l in MANIFEST.open(encoding="utf-8")] if MANIFEST.exists() else []
    have = {r["url"] for r in rows}
    per = Counter(r["brand"] for r in rows)   # a re-run tops each brand up, it doesn't pile on
    todo = [b for b in brands() if (not args.brand or norm(b["name"]) == norm(args.brand)) and per[b["name"]] < args.per_brand]
    for b in todo:
        found = [x for x in off_images(b) + commons_images(b) if x["url"] not in have][: args.per_brand - per[b["name"]]]
        print(f"{b['name']}: {len(found)} imágenes nuevas")
        folder = DATA / slug(b["name"])
        folder.mkdir(exist_ok=True)
        for i, x in enumerate(found):
            data = get(x["url"], raw=True)
            if not data:
                continue
            try:
                jpg = to_jpeg(data)
            except Exception as e:  # SVG, broken file…
                print(f"  ! no es imagen: {x['url'][:80]} ({e})")
                continue
            name = f"{x['source'][:3]}-{int(time.time() * 1000)}-{i}.jpg"
            (folder / name).write_bytes(jpg)
            with MANIFEST.open("a", encoding="utf-8") as f:
                f.write(json.dumps({"brand": b["name"], "file": f"{slug(b['name'])}/{name}", **x}, ensure_ascii=False) + "\n")
            have.add(x["url"])
            time.sleep(3)
    print(f"Listo: {DATA}")


def staging():
    cfg = (ROOT / "pwa/src/lib/config.ts").read_text(encoding="utf-8")
    url = re.search(r'STAGING_URL = "([^"]+)"', cfg).group(1)
    key = re.search(r'STAGING_ANON_KEY = "([^"]+)"', cfg).group(1)
    assert "mbbdrmicjobydprvbpsj" in url, "solo STAGING"
    return url, key


def post(url, key, path, body, tok=None):
    req = urllib.request.Request(url + path, data=json.dumps(body).encode(), method="POST", headers={
        "apikey": key, "Authorization": "Bearer " + (tok or key), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]


def learn(args):
    url, key = staging()
    # The seeded operator test account of STAGING (same as supabase/tests/*).
    s, d = post(url, key, "/auth/v1/token?grant_type=password", {"email": "operador@ruma.test", "password": "Maki12345!"})
    assert s == 200, ("login", s, d)
    tok = d["access_token"]
    done = {json.loads(l)["file"] for l in RESULTS.open(encoding="utf-8")} if RESULTS.exists() else set()
    todo = [json.loads(l) for l in MANIFEST.open(encoding="utf-8")]
    todo = [m for m in todo if m["file"] not in done][: args.limit]
    for n, m in enumerate(todo, 1):
        frame = base64.b64encode((DATA / m["file"]).read_bytes()).decode()
        for _ in range(4):
            s, r = post(url, key, "/functions/v1/detect-material", {"mode": "learn", "frame": frame}, tok)
            if s == 200 and isinstance(r, dict) and r.get("busy"):
                time.sleep(r.get("retry_after_ms", 10000) / 1000)
                continue
            break
        if s != 200 or not isinstance(r, dict) or "items" not in r:
            print(f"  ! {m['file']}: {s} {str(r)[:120]}")
            continue
        named = sorted({i["brand"] for i in r["items"] if i.get("brand")})
        print(f"[{n}/{len(todo)}] {m['brand']:<14} → {', '.join(named) or '—'}  ({len(r['items'])} envases, {r['provider']})")
        with RESULTS.open("a", encoding="utf-8") as f:
            f.write(json.dumps({"file": m["file"], "brand": m["brand"], **r}, ensure_ascii=False) + "\n")
        time.sleep(LEARN_PAUSE_S)


def analyse():
    cat = brands()
    known = {b["name"]: {norm(t) for t in [b["name"], *b.get("aliases", []), *b.get("texts", []), *b.get("fragments", [])]} for b in cat}
    rows = [json.loads(l) for l in RESULTS.open(encoding="utf-8")] if RESULTS.exists() else []
    stats = defaultdict(Counter)
    words = defaultdict(Counter)   # brand -> text -> images where the AI copied it
    for r in rows:
        st = stats[r["brand"]]
        st["imagenes"] += 1
        items = [i for i in r["items"] if i.get("code")]
        if not items:
            st["sin_envase"] += 1
            continue
        st["con_envase"] += 1
        named = {i.get("brand") for i in items if i.get("brand")}
        st["acierta" if r["brand"] in named else "otra_marca" if named else "sin_marca"] += 1
        if named - {r["brand"]}:
            st["confusiones"] += 1
        for t in {norm(i.get("texto", "")) for i in items} - {""}:
            # One candidate per phrase and per word: a label read "inca kola sabor" teaches both.
            for cand in {t, *t.split()}:
                words[r["brand"]][cand] += 1
    suggestions = {}
    for brand, counter in words.items():
        mine = known.get(brand, set())
        others = set().union(*(v for k, v in known.items() if k != brand))
        rival_words = {w for k, c in words.items() if k != brand for w in c}
        good = [w for w, c in counter.most_common()
                if c >= 2 and len(w) >= 4 and w not in GENERIC and not w.isdigit()
                and not any(w == k or w in k.split() for k in mine)
                and not any(w == o or w in o or o in w for o in others if len(o) >= 4)
                and w not in rival_words]
        if good:
            suggestions[brand] = good[:8]
    return stats, suggestions


def report(args):
    stats, suggestions = analyse()
    lines = ["# Ruma · qué tan bien lee la IA cada marca", "",
             "Imágenes de Open Food Facts y Wikimedia Commons leídas con el mismo detector que usa la app.", "",
             "| Marca | Imágenes | Con envase | Acierta | Otra marca | Sin marca |", "|---|---|---|---|---|---|"]
    for brand, st in sorted(stats.items(), key=lambda kv: (kv[1]["acierta"] / max(kv[1]["con_envase"], 1), kv[0])):
        lines.append(f"| {brand} | {st['imagenes']} | {st['con_envase']} | {st['acierta']} | {st['otra_marca']} | {st['sin_marca']} |")
    tot = Counter()
    for st in stats.values():
        tot.update(st)
    if tot["con_envase"]:
        lines += ["", f"**Total:** {tot['acierta']} de {tot['con_envase']} imágenes con envase reconocidas "
                      f"({100 * tot['acierta'] // tot['con_envase']} %); {tot['otra_marca']} confundidas con otra marca."]
    lines += ["", "## Palabras nuevas que la IA leyó en las etiquetas", ""]
    lines += [f"- **{b}**: {', '.join(ws)}" for b, ws in sorted(suggestions.items())] or ["(ninguna todavía)"]
    (DATA / "reporte.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (DATA / "sugerencias.json").write_text(json.dumps(suggestions, ensure_ascii=False, indent=1), encoding="utf-8")
    print("\n".join(lines))


def apply(args):
    _, suggestions = analyse()
    if not suggestions:
        return print("No hay palabras nuevas que agregar.")
    text = BRANDS_TS.read_text(encoding="utf-8")
    for line in text.splitlines():
        if not line.lstrip().startswith('{"name"'):
            continue
        b = json.loads(line.strip().rstrip(","))
        new = suggestions.get(b["name"])
        if not new:
            continue
        print(f"{b['name']}: + {', '.join(new)}")
        if args.write:
            b["texts"] = [*b.get("texts", []), *new]
            indent = line[: len(line) - len(line.lstrip())]
            text = text.replace(line, indent + json.dumps(b, ensure_ascii=False) + ("," if line.rstrip().endswith(",") else ""))
    if args.write:
        BRANDS_TS.write_text(text, encoding="utf-8")
        print("brands.ts actualizado: sube PIPELINE_VERSION en detect-material/index.ts y despliega en STAGING.")
    else:
        print("(vista previa: agrega --write para escribirlo en brands.ts)")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("collect"); c.add_argument("--per-brand", type=int, default=6); c.add_argument("--brand")
    l = sub.add_parser("learn"); l.add_argument("--limit", type=int, default=300)
    sub.add_parser("report")
    a = sub.add_parser("apply"); a.add_argument("--write", action="store_true")
    args = ap.parse_args()
    {"collect": collect, "learn": learn, "report": report, "apply": apply}[args.cmd](args)
