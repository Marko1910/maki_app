# Checks supabase/sql/2026-11-11_canje_seguro.sql against STAGING with the *@ruma.test seed.
# Writes one real 200-pt donation for generador@ruma.test in STAGING. Run from the repo root:
#   python supabase/tests/canje_seguro_staging.py
import json, re, sys, urllib.error, urllib.request

cfg = open("pwa/src/lib/config.ts", encoding="utf-8").read()
URL = re.search(r'STAGING_URL = "([^"]+)"', cfg).group(1)
KEY = re.search(r'STAGING_ANON_KEY = "([^"]+)"', cfg).group(1)
assert "mbbdrmicjobydprvbpsj" in URL, "solo STAGING"

def call(method, path, body=None, tok=None, prefer=None):
    h = {"apikey": KEY, "Authorization": "Bearer " + (tok or KEY), "Content-Type": "application/json"}
    if prefer: h["Prefer"] = prefer
    req = urllib.request.Request(URL + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r: return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try: return e.code, json.loads(raw)
        except ValueError: return e.code, raw

s, d = call("POST", "/auth/v1/token?grant_type=password", {"email": "generador@ruma.test", "password": "Maki12345!"})
T, uid = d["access_token"], d["user"]["id"]
opts = {o["code"]: o for o in call("GET", "/rest/v1/redemption_options?select=id,code,kind,min_points", tok=T)[1]}
results = []
def check(name, ok, seen):
    results.append(ok); print(("PASA " if ok else "FALLA") + f" | {name} | {json.dumps(seen, ensure_ascii=False)[:170]}")
def redeem(row):
    return call("POST", "/rest/v1/redemptions?select=id,kind,points_spent,monetary_value,status,payout_reference", {"user_id": uid, **row}, T, "return=representation")

s, d = redeem({"option_id": opts["YAPE_PLIN"]["id"], "kind": "cash", "points_spent": 1, "monetary_value": 100})
check("1 punto por S/100 se rechaza", s >= 400, [s, d])
s, d = redeem({"option_id": None, "kind": "cash", "points_spent": 5000, "monetary_value": 5})
check("Canje sin opción se rechaza", s >= 400, [s, d])
s, d = redeem({"option_id": opts["YAPE_PLIN"]["id"], "kind": "cash", "points_spent": 5500, "monetary_value": 5.5})
check("Puntos que no son unidades enteras se rechazan", s >= 400, [s, d])
s, d = redeem({"option_id": opts["DONATE_TREE"]["id"], "kind": "cash", "points_spent": 200, "monetary_value": 500,
               "status": "completed", "payout_reference": "ya pagado"})
row = d[0] if s in (200, 201) and d else {}
check("Valor, tipo y estado los fija la base", row.get("monetary_value") in (1, 1.0, "1.00") and row.get("kind") == "donation"
      and row.get("status") == "pending" and row.get("payout_reference") is None, [s, row])
if row:
    s, _ = call("PATCH", f"/rest/v1/redemptions?id=eq.{row['id']}", {"status": "completed"}, T, "return=representation")
    s2, d2 = call("GET", f"/rest/v1/redemptions?select=status&id=eq.{row['id']}", tok=T)
    check("La app no puede marcar su canje como pagado", d2 and d2[0]["status"] == "pending", [s, d2])

print(f"\n{sum(results)}/{len(results)} pasan")
sys.exit(0 if all(results) else 1)
