# Checks supabase/sql/2026-11-11_arbitrios_porcentaje.sql against STAGING with the *@ruma.test seed.
# Spends at most one real 5 % arbitrios discount (2.750 pts) of generador@ruma.test, and only when this
# year has room for it. The yearly cap is checked BEFORE the balance, so the over-cap attempts are refused
# without spending anything, whatever the balance. Run from the repo root:
#   python supabase/tests/arbitrios_porcentaje_staging.py
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
opt = call("GET", "/rest/v1/redemption_options?select=id,points_per_unit,min_points,unit_value,metadata&code=eq.MUNI_ARBITRIOS", tok=T)[1][0]
results = []
def check(name, ok, seen):
    results.append(ok); print(("PASA " if ok else "FALLA") + f" | {name} | {json.dumps(seen, ensure_ascii=False)[:170]}")
def redeem(points):
    return call("POST", "/rest/v1/redemptions?select=id,points_spent,monetary_value,quantity,status",
                {"user_id": uid, "option_id": opt["id"], "kind": "cash", "points_spent": points, "monetary_value": 999}, T, "return=representation")
def used_this_year():
    # Lima has no DST: UTC-5 all year.
    rows = call("GET", f"/rest/v1/redemptions?select=quantity,status,created_at&option_id=eq.{opt['id']}&user_id=eq.{uid}", tok=T)[1]
    import datetime as dt
    year = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=5)).year
    return sum(float(r["quantity"]) for r in rows if r["status"] not in ("cancelled", "failed")
               and (dt.datetime.fromisoformat(r["created_at"]) - dt.timedelta(hours=5)).year == year)

meta = opt["metadata"] or {}
check("Opción en %: 550 pts = 1 % = S/0,55, mínimo 2.750 pts (5 %)",
      float(opt["points_per_unit"]) == 550 and float(opt["unit_value"]) == 0.55 and opt["min_points"] == 2750
      and meta.get("unit") == "percent" and meta.get("max_percent_year") == 50 and meta.get("arbitrio_anual") == 55, opt)

s, d = redeem(2200)
check("4 % (debajo del mínimo) se rechaza", s >= 400, [s, d])
s, d = redeem(3000)
check("Puntos que no son % enteros se rechazan", s >= 400, [s, d])

bal = call("GET", f"/rest/v1/generator_profiles?select=points_balance&profile_id=eq.{uid}", tok=T)[1][0]["points_balance"]
used = used_this_year()
print(f"saldo {bal} pts · usado este año {used:g} %")
if used + 5 <= 50 and bal >= 2750:
    s, d = redeem(2750)
    row = d[0] if s in (200, 201) and d else {}
    check("5 % = 2.750 pts = S/2,75, quantity 5 (lo que mande la app se ignora)",
          float(row.get("monetary_value", -1)) == 2.75 and float(row.get("quantity", -1)) == 5 and row.get("status") == "pending", [s, row])
    used += 5 if row else 0
else:
    print(f"OMITIDO | el canje real de 5 %: saldo {bal} o tope ({used:g} % usado) no alcanzan; no se infla el saldo")

over = int(50 - used) + 1  # one percent past the cap
s, d = redeem(over * 550)
check(f"Pasar el 50 % anual ({used:g} % + {over} %) se rechaza con el % usado",
      s >= 400 and f"Ya usaste el {used:g} %" in json.dumps(d, ensure_ascii=False) and "máximo 50 %" in json.dumps(d, ensure_ascii=False), [s, d])
fit = int(50 - used)
bal = call("GET", f"/rest/v1/generator_profiles?select=points_balance&profile_id=eq.{uid}", tok=T)[1][0]["points_balance"]
if fit >= 5 and fit * 550 > bal:
    # Exactly at the cap the refusal must come from the balance, proving the cap let it through. Spends nothing.
    s, d = redeem(fit * 550)
    check(f"Llegar justo al 50 % ({used:g} % + {fit} %) pasa el tope (solo lo frena el saldo)",
          s >= 400 and "Saldo insuficiente" in json.dumps(d, ensure_ascii=False), [s, d])
else:
    print(f"OMITIDO | llegar justo al 50 %: el saldo ({bal}) alcanzaría y el canje se gastaría de verdad")

print(f"\n{sum(results)}/{len(results)} pasan")
sys.exit(0 if all(results) else 1)
