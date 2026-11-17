# Checks supabase/sql/2026-11-12_planes.sql against STAGING with the *@ruma.test seed.
# Writes in STAGING for generador@ruma.test: its one free trial (first run only, expired at once), one Ruma
# Plus request activated and then expired, one real 1.000-pt Yape redemption (stays 'pending'), three
# cancelled test pickups and one withdrawn yearly request. Run from the repo root:
#   python supabase/tests/planes_staging.py
# The expiry step moves the subscription's dates back through the Supabase CLI (npx supabase db query),
# because nobody, not even an operator, can edit those dates through the API.
import datetime, json, re, subprocess, sys, tempfile, urllib.error, urllib.request

cfg = open("pwa/src/lib/config.ts", encoding="utf-8").read()
URL = re.search(r'STAGING_URL = "([^"]+)"', cfg).group(1)
KEY = re.search(r'STAGING_ANON_KEY = "([^"]+)"', cfg).group(1)
REF = "mbbdrmicjobydprvbpsj"
assert REF in URL, "solo STAGING"

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

def staging_sql(sql):
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f: f.write(sql)
    out = subprocess.run(f'npx supabase db query --remote={REF} --agent no --output-format text -f "{f.name}" --yes',
                         shell=True, capture_output=True, text=True, encoding="utf-8")
    assert out.returncode == 0, out.stderr

results = []
def check(name, ok, seen):
    results.append(bool(ok)); print(("PASA " if ok else "FALLA") + f" | {name} | {json.dumps(seen, ensure_ascii=False)[:170]}")

(G, gid), (O, _) = login("generador@ruma.test"), login("operador@ruma.test")
rpc = lambda name, body, tok=G: call("POST", f"/rest/v1/rpc/{name}", body, tok)
yape = call("GET", "/rest/v1/redemption_options?select=id&code=eq.YAPE_PLIN", tok=G)[1][0]["id"]
redeem_1k = lambda: call("POST", "/rest/v1/redemptions?select=id,monetary_value,status", {"user_id": gid, "option_id": yape, "kind": "cash", "points_spent": 1000, "monetary_value": 1}, G, "return=representation")
today = datetime.date.today().isoformat()
def pickup_priority():
    s, rows = call("POST", "/rest/v1/pickups?select=id,priority", {"generator_id": gid, "scheduled_date": today, "window_label": "Prueba planes"}, G, "return=representation")
    assert s in (200, 201), (s, rows)
    call("PATCH", f"/rest/v1/pickups?id=eq.{rows[0]['id']}", {"status": "cancelled", "cancel_reason": "Prueba de planes"}, G)
    return rows[0]["id"], rows[0]["priority"]

# The free trial: once per person. The first call may pass on a fresh account; the second never does.
rpc("start_plan_trial", {"p_plan": "PLUS"})
s, d = rpc("start_plan_trial", {"p_plan": "PLUS"})
check("La prueba gratis se da una sola vez", s >= 400, [s, d])

# A plan left in force (the trial above, an interrupted run) would make the "before" checks lie.
staging_sql(f"update public.plan_subscriptions set status = 'expired', starts_at = now() - interval '32 days', ends_at = now() - interval '1 day' where user_id = '{gid}' and status = 'active';")
start = rpc("my_plan", {})[1]
check("Sin plan: my_plan no da beneficios", start.get("active") is None and start.get("benefits") == {}, start.get("benefits"))

# 1. The client can't give itself a plan.
s, d = call("POST", "/rest/v1/plan_subscriptions", {"user_id": gid, "plan_code": "PLUS", "period": "month", "amount": 0, "status": "active", "reference": "HACK-1",
                                                   "starts_at": "2026-01-01T00:00:00Z", "ends_at": "2030-01-01T00:00:00Z"}, G, "return=representation")
check("La app no puede insertar una suscripción activa", s >= 400, [s, d])
s, d = rpc("request_plan", {"p_plan": "PLUS", "p_period": "month"})
sub = d if s == 200 and isinstance(d, dict) else {}
check("request_plan deja la solicitud pendiente con el monto del catálogo", float(sub.get("amount", 0)) == 4.90 and str(sub.get("reference", "")).startswith("RUMA-"), [s, d])
s2, d2 = rpc("request_plan", {"p_plan": "PLUS", "p_period": "month"})
check("Pedir otra vez devuelve el mismo código", d2.get("reference") == sub.get("reference") if isinstance(d2, dict) else False, [s2, d2])
call("PATCH", f"/rest/v1/plan_subscriptions?id=eq.{sub.get('id')}", {"status": "active", "starts_at": "2026-01-01T00:00:00Z", "ends_at": "2030-01-01T00:00:00Z"}, G)
s, d = call("GET", f"/rest/v1/plan_subscriptions?select=status&id=eq.{sub.get('id')}", tok=G)
check("La app no puede marcar su solicitud como pagada", bool(d) and d[0]["status"] == "pending_payment", [s, d])
s, d = rpc("activate_plan_subscription", {"p_subscription_id": sub.get("id"), "p_payment_reference": "123456"})
check("El hogar no puede activarse el plan con la RPC del operador", s >= 400, [s, d])
# 2. Pending is not paid: no benefit yet.
s, d = redeem_1k()
check("Pendiente de pago: el canje Yape de S/ 1 se rechaza (mínimo S/ 5)", s >= 400, [s, d])
s, d = call("POST", "/rest/v1/pickups", {"generator_id": gid, "scheduled_date": today, "window_label": "Prueba planes", "priority": 9}, G)
check("La app no puede mandar la prioridad al crear el recojo", s >= 400, [s, d])
_, prio = pickup_priority()
check("Pendiente de pago: el recojo nace sin prioridad", prio == 0, prio)

# 3. Only the operator activates.
s, d = rpc("activate_plan_subscription", {"p_subscription_id": sub.get("id"), "p_payment_reference": ""}, O)
check("El operador debe anotar el número de operación", s >= 400, [s, d])
s, d = rpc("activate_plan_subscription", {"p_subscription_id": sub.get("id"), "p_payment_reference": "YAPE-PRUEBA-001"}, O)
check("El operador activa el plan", s == 200 and isinstance(d, dict) and d.get("ends_at"), [s, d])
mine = rpc("my_plan", {})[1]
check("my_plan muestra el plan activo y sus beneficios", (mine.get("active") or {}).get("plan_code") == "PLUS" and mine["benefits"].get("redeem_min_points") == 1000, mine.get("active"))

# 4. Active and in force: the benefits apply in the database.
s, d = redeem_1k()
row = d[0] if s in (200, 201) and d else {}
check("Plan activo: canje Yape de S/ 1 aceptado y valorado por la base", float(row.get("monetary_value", 0)) == 1.0 and row.get("status") == "pending", [s, row])
pid, prio = pickup_priority()
check("Plan activo: el recojo nace con prioridad 1 (la base, no la app)", prio == 1, prio)
call("PATCH", f"/rest/v1/pickups?id=eq.{pid}", {"priority": 0}, G)
s, d = call("GET", f"/rest/v1/pickups?select=priority&id=eq.{pid}", tok=G)
check("Un PATCH no cambia la prioridad", bool(d) and d[0]["priority"] == 1, [s, d])
s, d = call("PATCH", f"/rest/v1/plan_subscriptions?id=eq.{sub.get('id')}", {"ends_at": "2030-01-01T00:00:00Z"}, O, "return=representation")
check("Ni el operador alarga un plan por la API", s >= 400 or d == [], [s, d])

# 5. Expired: it stops applying at once.
staging_sql(f"update public.plan_subscriptions set starts_at = now() - interval '32 days', ends_at = now() - interval '1 second' where id = '{sub.get('id')}';")
s, d = redeem_1k()
check("Plan vencido: el canje de S/ 1 vuelve a rechazarse", s >= 400, [s, d])
_, prio = pickup_priority()
check("Plan vencido: el recojo vuelve a nacer sin prioridad", prio == 0, prio)
after = rpc("my_plan", {})[1]
s, d = call("GET", f"/rest/v1/plan_subscriptions?select=status&id=eq.{sub.get('id')}", tok=G)
check("Plan vencido: my_plan ya no lo muestra y queda 'expired'", after.get("active") is None and after.get("benefits") == {} and d and d[0]["status"] == "expired", [after.get("active"), d])

# 6. Cancelling a request is one call and charges nothing.
s, d = rpc("request_plan", {"p_plan": "PLUS", "p_period": "year"})
s2, d2 = rpc("cancel_my_plan", {"p_subscription_id": d.get("id") if isinstance(d, dict) else None})
check("Plan anual pedido a S/ 39,90 y retirado con un toque", isinstance(d, dict) and float(d.get("amount", 0)) == 39.90 and s2 == 200, [d, d2])

print(f"\n{sum(results)}/{len(results)} pasan")
sys.exit(0 if all(results) else 1)
