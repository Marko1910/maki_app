# Checks supabase/sql/2026-11-06_seguridad_cantidades.sql against STAGING with the *@ruma.test seed.
# Writes test pickups and one settled delivery in STAGING. Run from the repo root:
#   python supabase/tests/seguridad_cantidades_staging.py
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
    results.append(ok); print(("PASA " if ok else "FALLA") + f" | {name} | {json.dumps(seen, ensure_ascii=False)[:160]}")

(A, aid), (B, bid), (R, rid), (O, oid) = (login(e) for e in ("generador@ruma.test", "quispe@ruma.test", "rider@ruma.test", "operador@ruma.test"))
pet = call("GET", "/rest/v1/materials?select=id,avg_weight_kg&code=eq.PET", tok=A)[1][0]
today = datetime.date.today().isoformat()

def new_pickup(tok, uid, declared):
    s, rows = call("POST", "/rest/v1/pickups?select=id,code", {"generator_id": uid, "scheduled_date": today, "window_label": "Prueba seguridad", "latitude": -7.884, "longitude": -79.221}, tok, "return=representation")
    pid = rows[0]["id"]
    s2, _ = call("POST", "/rest/v1/pickup_items", [{"pickup_id": pid, "material_id": pet["id"], "reported_quantity": declared}], tok)
    assert s2 in (200, 201), s2
    return pid

# 1-2. Scans and money columns are closed to the app.
s, d = call("GET", "/rest/v1/detections?select=id&limit=1", tok=A)
if d:
    s, _ = call("DELETE", f"/rest/v1/detections?id=eq.{d[0]['id']}", tok=A, prefer="return=representation")
    check("Recolector no puede borrar un escaneo", s in (401, 403), s)
pa = new_pickup(A, aid, 10)
s, _ = call("POST", "/rest/v1/pickup_items", [{"pickup_id": pa, "material_id": pet["id"], "reported_quantity": 1, "points": 99999}], A)
check("App no puede escribir points en un ítem", s in (401, 403), s)
s, _ = call("DELETE", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", tok=A, prefer="return=representation")
check("App no puede borrar ítems del recojo", s in (401, 403), s)

# 3. Who changes which quantity.
s, d = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", {"confirmed_quantity": 50}, A, "return=representation")
check("Recolector no puede tocar lo confirmado", s in (401, 403), [s, d if isinstance(d, str) else d.get("message") if isinstance(d, dict) else d])
pb = new_pickup(B, bid, 1)
for pid in (pa, pb):
    call("PATCH", f"/rest/v1/pickups?id=eq.{pid}&status=eq.requested&rider_id=is.null", {"rider_id": rid, "status": "assigned"}, R)
s, d = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", {"confirmed_quantity": 1}, R, "return=representation")
check("Rider no confirma antes de llegar", s in (401, 403), s)
for pid in (pa, pb):
    for st in ("en_route", "arriving", "collecting"):
        call("PATCH", f"/rest/v1/pickups?id=eq.{pid}", {"status": st}, R)
s, d = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", {"reported_quantity": 40}, R, "return=representation")
check("Rider no puede cambiar lo declarado", s in (401, 403), s)
s, d = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pa}", {"reported_quantity": 40}, A, "return=representation")
check("Recolector no corrige lo declarado durante la recolección", s in (401, 403), s)
for pid in (pa, pb):
    s, d = call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pid}", {"confirmed_quantity": 1}, R, "return=representation")
    check("Rider confirma 1 en la puerta", s == 200 and d and d[0]["confirmed_quantity"] == 1, s)
    s, _ = call("POST", "/rest/v1/rpc/complete_pickup", {"p_pickup_id": pid}, R)
    check("Rider completa", s in (200, 204), s)
s, _ = call("POST", "/rest/v1/pickup_items", [{"pickup_id": pa, "material_id": pet["id"], "reported_quantity": 5}], A)
check("Recolector no agrega ítems a un recojo completado", s in (401, 403), s)

# 4. Settlement splits by the door count: 10 declared vs 1 declared, both confirmed 1 -> equal shares.
center = call("GET", f"/rest/v1/operator_profiles?select=center_id&profile_id=eq.{oid}", tok=O)[1][0]["center_id"]
price = call("GET", f"/rest/v1/center_materials?select=buy_price_per_kg&center_id=eq.{center}&material_id=eq.{pet['id']}", tok=O)[1][0]["buy_price_per_kg"]
s, dl = call("POST", "/rest/v1/rpc/create_delivery", {"p_center_id": center, "p_pickup_ids": [pa, pb]}, R)
check("Entrega con los dos recojos", s == 200, s); dl = dl if isinstance(dl, dict) else dl[0]
now = datetime.datetime.now(datetime.timezone.utc).isoformat()
call("PATCH", f"/rest/v1/deliveries?id=eq.{dl['id']}", {"status": "recibida", "received_at": now, "received_by": oid}, O)
weight = round(2 * float(pet["avg_weight_kg"]) * 10, 3)
call("POST", "/rest/v1/delivery_items", [{"delivery_id": dl["id"], "material_id": pet["id"], "weight_kg": weight, "quality_grade": "B", "price_per_kg": price, "grade_factor": 0.85}], O)
call("PATCH", f"/rest/v1/deliveries?id=eq.{dl['id']}", {"status": "pesada", "weighed_at": now}, O)
s, _ = call("POST", "/rest/v1/rpc/settle_delivery", {"p_delivery_id": dl["id"]}, O)
check("Operador liquida", s == 200, s)
fa = call("GET", f"/rest/v1/pickups?select=code,generator_points_final&id=eq.{pa}", tok=A)[1][0]
fb = call("GET", f"/rest/v1/pickups?select=code,generator_points_final&id=eq.{pb}", tok=B)[1][0]
check("Declarar 10 no le quita peso al vecino (mismo conteo en la puerta = mismos puntos)",
      fa["generator_points_final"] is not None and fa["generator_points_final"] == fb["generator_points_final"], [fa, fb, dl.get("code")])

print(f"\n{sum(results)}/{len(results)} pasan")
sys.exit(0 if all(results) else 1)
