# Compares what the public API exposes in STAGING and PRODUCTION. Read-only; prints names, never keys.
# It cannot see triggers or RLS policies. Run from the repo root:
#   python pwa/scripts/compare-schemas.py
import json
import re
import urllib.error
import urllib.request

vals = {}
for line in open("pwa/.env.production.local", encoding="utf-8-sig"):
    m = re.match(r"\s*(VITE_SUPABASE_URL|VITE_SUPABASE_ANON_KEY)\s*=\s*(.*)", line)
    if m:
        vals[m.group(1)] = m.group(2).strip().strip("\"'")
purl, pkey = vals["VITE_SUPABASE_URL"], vals["VITE_SUPABASE_ANON_KEY"]
cfg = open("pwa/src/lib/config.ts", encoding="utf-8").read()
surl = re.search(r'STAGING_URL = "([^"]+)"', cfg).group(1)
skey = re.search(r'STAGING_ANON_KEY = "([^"]+)"', cfg).group(1)


def get(url, key, path, method="GET", headers=None):
    h = {"apikey": key, "Authorization": "Bearer " + key, **(headers or {})}
    req = urllib.request.Request(url + path, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read(), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers)
    except Exception as e:
        return 0, str(e).encode(), {}


def schema(url, key):
    status, body, _ = get(url, key, "/rest/v1/", headers={"Accept": "application/openapi+json"})
    return (json.loads(body) if status == 200 else None), status


S, ss = schema(surl, skey)
P, ps = schema(purl, pkey)
print(f"Esquema publico: staging {ss}, produccion {ps}")
if S and P:
    names = lambda d: {k.strip("/") for k in d.get("paths", {}) if k != "/"}
    defs = lambda d: d.get("definitions") or d.get("components", {}).get("schemas", {})
    sn, pn = names(S), names(P)
    print(f"Tablas, vistas y funciones: staging {len(sn)}, produccion {len(pn)}")
    print("FALTAN en produccion:", sorted(sn - pn) or "nada")
    print("Solo en produccion:", sorted(pn - sn) or "nada")
    sd, pd = defs(S), defs(P)
    missing = {t: sorted(set(sd[t].get("properties", {})) - set(pd[t].get("properties", {}))) for t in set(sd) & set(pd)}
    print("COLUMNAS que faltan en produccion:", {t: c for t, c in sorted(missing.items()) if c} or "ninguna")
for fn in ["detect-material", "maki-assistant"]:
    print(f"Edge Function {fn}: staging {get(surl, skey, '/functions/v1/' + fn, 'OPTIONS')[0]}, produccion {get(purl, pkey, '/functions/v1/' + fn, 'OPTIONS')[0]}")
for table in ["materials", "redemption_options", "collection_centers", "v_material_prices", "v_material_demand", "market_news", "challenges"]:
    row = []
    for url, key in [(surl, skey), (purl, pkey)]:
        status, _, h = get(url, key, f"/rest/v1/{table}?select=*&limit=1", headers={"Prefer": "count=exact"})
        row.append(f"{status}, {(h.get('Content-Range') or h.get('content-range') or '?').split('/')[-1]} filas")
    print(f"{table}: staging {row[0]} | produccion {row[1]}")
