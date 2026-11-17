# Checks supabase/sql/2026-11-17_puntos_estado_pago_mixto.sql against STAGING with the *@ruma.test seed.
# Writes 14 test pickups and two settled deliveries (pays the test rider) in STAGING. Run from the repo root:
#   python supabase/tests/puntos_estado_staging.py
import datetime, json, re, sys, urllib.error, urllib.request

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

def login(email):
    s, d = call("POST", "/auth/v1/token?grant_type=password", {"email": email, "password": "Maki12345!"})
    assert s == 200, (email, s)
    return d["access_token"], d["user"]["id"]

results = []
def check(name, ok, seen):
    results.append(ok); print(("PASA " if ok else "FALLA") + f" | {name} | {json.dumps(seen, ensure_ascii=False)[:170]}")

(A, aid), (B, bid), (R, rid), (O, oid) = (login(e) for e in ("generador@ruma.test", "quispe@ruma.test", "rider@ruma.test", "operador@ruma.test"))
pet = call("GET", "/rest/v1/materials?select=id,avg_weight_kg&code=eq.PET", tok=A)[1][0]
center = call("GET", f"/rest/v1/operator_profiles?select=center_id&profile_id=eq.{oid}", tok=O)[1][0]["center_id"]
price = call("GET", f"/rest/v1/center_materials?select=buy_price_per_kg&center_id=eq.{center}&material_id=eq.{pet['id']}", tok=O)[1][0]["buy_price_per_kg"]
today = datetime.date.today().isoformat()

def new_pickup(tok, uid, declared):
    s, rows = call("POST", "/rest/v1/pickups?select=id,code", {"generator_id": uid, "scheduled_date": today, "window_label": "Prueba puntos", "latitude": -7.884, "longitude": -79.221}, tok, "return=representation")
    pid = rows[0]["id"]
    s2, _ = call("POST", "/rest/v1/pickup_items", [{"pickup_id": pid, "material_id": pet["id"], "reported_quantity": declared}], tok)
    assert s2 in (200, 201), s2
    return pid

def claim(pid):
    call("PATCH", f"/rest/v1/pickups?id=eq.{pid}&status=eq.requested&rider_id=is.null", {"rider_id": rid, "status": "assigned"}, R)

def to_door(pid):
    for st in ("en_route", "arriving", "collecting"):
        call("PATCH", f"/rest/v1/pickups?id=eq.{pid}", {"status": st}, R)

def door(pid, qty, condition):
    s, d = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pid}", {"confirmed_quantity": qty, "condition": condition}, R, "return=representation")
    s2, _ = call("POST", "/rest/v1/rpc/complete_pickup", {"p_pickup_id": pid}, R)
    return s == 200 and d and d[0]["condition"] == condition and s2 in (200, 204)

def settle(pids, kg):
    s, dl = call("POST", "/rest/v1/rpc/create_delivery", {"p_center_id": center, "p_pickup_ids": pids}, R)
    assert s == 200, (s, dl); dl = dl if isinstance(dl, dict) else dl[0]
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    call("PATCH", f"/rest/v1/deliveries?id=eq.{dl['id']}", {"status": "recibida", "received_at": now, "received_by": oid}, O)
    call("POST", "/rest/v1/delivery_items", [{"delivery_id": dl["id"], "material_id": pet["id"], "weight_kg": kg, "quality_grade": "A", "price_per_kg": price, "grade_factor": 1}], O)
    call("PATCH", f"/rest/v1/deliveries?id=eq.{dl['id']}", {"status": "pesada", "weighed_at": now}, O)
    s, d = call("POST", "/rest/v1/rpc/settle_delivery", {"p_delivery_id": dl["id"]}, O)
    assert s == 200, (s, d)
    return d if isinstance(d, dict) else d[0]

def final(pid, tok):
    return call("GET", f"/rest/v1/pickups?select=generator_points_final,rider_earnings&id=eq.{pid}", tok=tok)[1][0]

def earned(pid):
    return [float(e["amount"]) for e in call("GET", f"/rest/v1/rider_earnings?select=amount&pickup_id=eq.{pid}", tok=R)[1]]

# 1. Who marks the state, and the rider's estimate on claiming.
pa = new_pickup(A, aid, 40)
s, _ = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", {"condition": "aplastada"}, A, "return=representation")
check("La familia no marca el estado", s in (401, 403), s)
claim(pa)
check("Al tomarlo, el rider ve un estimado por kg (40 PET = 1 kg x S/0,35)", float(final(pa, R)["rider_earnings"]) == 0.35, final(pa, R))
s, _ = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", {"condition": "aplastada"}, R, "return=representation")
check("El rider no marca el estado antes de llegar", s in (401, 403), s)
pb = new_pickup(B, bid, 10); claim(pb)
for pid in (pa, pb): to_door(pid)
s, _ = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", {"condition": "rota"}, R, "return=representation")
check("Un estado inventado se rechaza", s >= 400, s)
check("Rider: 40 aplastadas en la puerta y completa", door(pa, 40, "aplastada"), pa)
check("Rider: 10 sucias en la puerta y completa", door(pb, 10, "sucia"), pb)
check("Completar ya no paga al rider", earned(pa) == [] and earned(pb) == [], [earned(pa), earned(pb)])

# 2. Delivery 1: 50 PET weighed at their average weight (1,25 kg); 2 stops, no route base.
d1 = settle([pa, pb], 1.25)
fa, fb = final(pa, A), final(pb, B)
check("40 aplastadas: 40x10x1,1 = 440, +10 % por 30 o más = 484", fa["generator_points_final"] == 484, fa)
check("10 sucias: 10x10x0,5 = 50, sin bono", fb["generator_points_final"] == 50, fb)
check("Rider cobra por kg al liquidar (1 kg y 0,25 kg x S/0,35)", earned(pa) == [0.35] and earned(pb) == [0.09], [earned(pa), earned(pb)])
check("Dos recojos no son una ruta: sin base", float(d1["rider_amount"]) == 0.44, d1.get("rider_amount"))

# 3. Delivery 2: 12 stops (one of 100, eleven of 10) weighing half of what was counted -> the weight cap binds.
big = new_pickup(A, aid, 100)
small = [new_pickup(A, aid, 10) for _ in range(11)]
for pid in [big] + small: claim(pid); to_door(pid)
door(big, 100, "limpia")
for pid in small: door(pid, 10, "limpia")
d2 = settle([big] + small, 2.625)
fbig, fsmall = final(big, A), final(small[0], A)
check("Tope por peso + bono 80 o más: min(1000, 1,25 kg x 400 x 1,25 = 625) x 1,2 = 750", fbig["generator_points_final"] == 750, fbig)
check("Tope por peso sin bono: min(100, 62,5) = 62", fsmall["generator_points_final"] == 62, fsmall)
routes = call("GET", f"/rest/v1/rider_earnings?select=amount,description&type=eq.route&rider_id=eq.{rid}&order=created_at.desc&limit=1", tok=R)[1]
check("12 recojos son una ruta: base S/6", routes and float(routes[0]["amount"]) == 6.0 and d2["code"] in routes[0]["description"], routes)
check("Total del rider en la entrega: 0,44 + 11 x 0,04 + 6 = 6,88", float(d2["rider_amount"]) == 6.88, d2.get("rider_amount"))

print(f"\n{sum(results)}/{len(results)} pasan")
sys.exit(0 if all(results) else 1)
