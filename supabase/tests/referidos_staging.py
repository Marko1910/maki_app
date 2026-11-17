# Checks supabase/sql/2026-11-12_referidos.sql against STAGING. Run from the repo root:
#   python supabase/tests/referidos_staging.py
# Writes in STAGING, per run: 3 new families ref-<stamp>-{x,y,z}@ruma.test (clave Maki12345!), created by SQL
# because sign-up needs email confirmation; 3 pickups of 50 PET collected by rider@ruma.test; one delivery
# weighed and settled by operador@ruma.test (Centro de Acopio Cartavio); and 3.000 referral points
# (2.000 for generador@ruma.test, 1.000 for the new x). It lowers platform_settings.referral_monthly_cap
# for the weighing and puts the old value back at the end.
import datetime, json, os, re, subprocess, sys, tempfile, time, urllib.error, urllib.request

sys.stdout.reconfigure(encoding="utf-8")
REF = "mbbdrmicjobydprvbpsj"
cfg = open("pwa/src/lib/config.ts", encoding="utf-8").read()
URL = re.search(r'STAGING_URL = "([^"]+)"', cfg).group(1)
KEY = re.search(r'STAGING_ANON_KEY = "([^"]+)"', cfg).group(1)
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

def sql(text):
    """Runs SQL as the project owner through the Supabase CLI (only for setup the API rightly refuses)."""
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f: f.write(text)
    try:
        out = subprocess.run(f'npx supabase db query --remote={REF} --agent no --output-format json -f "{f.name}" --yes',
                             shell=True, capture_output=True, text=True, encoding="utf-8", timeout=180)
    finally: os.unlink(f.name)
    assert out.returncode == 0, out.stderr[-600:]
    body = out.stdout[out.stdout.find("["):] if "[" in out.stdout else "[]"
    return json.loads(body)

def login(email):
    s, d = call("POST", "/auth/v1/token?grant_type=password", {"email": email, "password": "Maki12345!"})
    assert s == 200, (email, s, d)
    return d["access_token"], d["user"]["id"]

results = []
def check(name, ok, seen):
    results.append(bool(ok)); print(("PASA " if ok else "FALLA") + f" | {name} | {json.dumps(seen, ensure_ascii=False, default=str)[:170]}")
def rpc(fn, body, tok): return call("POST", f"/rest/v1/rpc/{fn}", body, tok)
def refused(s): return s >= 400

(A, aid), (R, rid), (O, oid) = (login(e) for e in ("generador@ruma.test", "rider@ruma.test", "operador@ruma.test"))
s, summary = rpc("my_referral_summary", {}, A)
code = summary["code"]
check("La base le da a cada familia un código", s == 200 and re.fullmatch(r"[A-HJKMNP-Z2-9]{6}", code), summary)

stamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
emails = {k: f"ref-{stamp}-{k}@ruma.test" for k in "xyz"}
rows = ",\n".join(
    f"""('00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated', '{e}',
       extensions.crypt('Maki12345!', extensions.gen_salt('bf')), now(), '{{"provider":"email","providers":["email"]}}',
       '{json.dumps({"role": "generador", "full_name": f"Prueba Referido {k.upper()}", **({"referral_code": code} if k == "x" else {})})}',
       now(), now(), '', '', '', '', '', '', '', '')""" for k, e in emails.items())
sql(f"""with u as (
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data,
    raw_user_meta_data, created_at, updated_at, confirmation_token, recovery_token, email_change_token_new, email_change,
    email_change_token_current, phone_change, phone_change_token, reauthentication_token)
  values {rows} returning id, email)
insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
select id::text, id, jsonb_build_object('sub', id::text, 'email', email, 'email_verified', true), 'email', now(), now(), now() from u;""")
(X, xid), (Y, yid), (Z, zid) = (login(emails[k]) for k in "xyz")

# Attribution.
s, d = rpc("apply_referral_code", {"p_code": rpc("my_referral_summary", {}, Z)[1]["code"]}, Z)
check("Nadie se invita a sí mismo", refused(s) and "propio" in json.dumps(d, ensure_ascii=False), [s, d])
s, sx = rpc("my_referral_summary", {}, X)
check("El código del registro (metadato) atribuye al invitado", s == 200 and (sx.get("invited_by") or {}).get("status") == "pendiente", sx.get("invited_by"))
s, d = rpc("apply_referral_code", {"p_code": code}, X)
check("No hay doble atribución", refused(s), [s, d])
s, d = rpc("apply_referral_code", {"p_code": code.lower()}, Y)
check("Un código en minúsculas atribuye después del registro", s == 200 and d.get("status") == "pendiente", [s, d])
s, d = rpc("apply_referral_code", {"p_code": code}, login("quispe@ruma.test")[0])
check("Pasados los 7 días del alta ya no se atribuye", refused(s) and "días" in json.dumps(d, ensure_ascii=False), [s, d])

# The app cannot write referrals, codes or points.
s1, _ = call("POST", "/rest/v1/referrals", {"referrer_id": aid, "referred_id": zid, "code": code, "status": "pagado"}, Z)
s2, _ = call("PATCH", f"/rest/v1/referrals?referred_id=eq.{xid}", {"status": "pagado", "referred_points": 99999}, X)
s3, _ = call("POST", "/rest/v1/point_transactions", {"user_id": xid, "type": "referral", "amount": 99999}, X)
s4, _ = call("PATCH", f"/rest/v1/referral_codes?profile_id=eq.{aid}", {"code": "AAAAAA"}, A)
s5, _ = rpc("attach_referral", {"p_referred": zid, "p_code": code}, Z)
s6, d6 = call("GET", f"/rest/v1/referrals?select=id,status,referred_points&referred_id=eq.{xid}", tok=X)
check("La app no escribe referidos, códigos ni puntos",
      all(refused(x) for x in (s1, s2, s3, s4, s5)) and d6 and d6[0]["status"] == "pendiente" and d6[0]["referred_points"] == 0,
      [s1, s2, s3, s4, s5, d6])
ref_x = d6[0]["id"]
ref_y = call("GET", f"/rest/v1/referrals?select=id&referred_id=eq.{yid}", tok=Y)[1][0]["id"]

# Pickups collected at the door: not weighed yet, so nothing is paid.
pet = call("GET", "/rest/v1/materials?select=id,avg_weight_kg&code=eq.PET", tok=A)[1][0]
today = datetime.date.today().isoformat()
pids = []
for i, tok in enumerate((X, Y, Z)):
    uid = (xid, yid, zid)[i]
    s, rows = call("POST", "/rest/v1/pickups?select=id", {"generator_id": uid, "scheduled_date": today, "window_label": "Prueba referidos",
                   "latitude": -7.8900 - i * 0.001, "longitude": -79.2280}, tok, "return=representation")
    assert s in (200, 201), (s, rows)
    pids.append(rows[0]["id"])
    call("POST", "/rest/v1/pickup_items", [{"pickup_id": rows[0]["id"], "material_id": pet["id"], "reported_quantity": 50}], tok)
    time.sleep(0.05)  # settle_delivery visits pickups by created_at: x first
for pid in pids:
    call("PATCH", f"/rest/v1/pickups?id=eq.{pid}&status=eq.requested&rider_id=is.null", {"rider_id": rid, "status": "assigned"}, R)
    for st in ("en_route", "arriving", "collecting"):
        call("PATCH", f"/rest/v1/pickups?id=eq.{pid}", {"status": st}, R)
    call("PATCH", f"/rest/v1/pickup_items?pickup_id=eq.{pid}", {"confirmed_quantity": 50}, R)
    s, _ = rpc("complete_pickup", {"p_pickup_id": pid}, R)
    assert s in (200, 204), s
s, txx = call("GET", f"/rest/v1/point_transactions?select=amount&reference_type=eq.referral&reference_id=eq.{ref_x}", tok=X)
check("No paga al recoger, antes del pesaje", s == 200 and txx == [] and rpc("my_referral_summary", {}, X)[1]["invited_by"]["status"] == "pendiente", txx)

# Cap: allow exactly one more paid referral for A this month, then weigh x and y in one delivery.
old_cap = sql("select referral_monthly_cap c from platform_settings")[0]["c"]
try:
    sql(f"""update platform_settings set referral_monthly_cap = (
      select count(*) + 1 from referrals where referrer_id = '{aid}' and status = 'pagado'
         and decided_at >= date_trunc('month', now() at time zone 'America/Lima') at time zone 'America/Lima') where id;""")
    center = call("GET", f"/rest/v1/operator_profiles?select=center_id&profile_id=eq.{oid}", tok=O)[1][0]["center_id"]
    price = call("GET", f"/rest/v1/center_materials?select=buy_price_per_kg&center_id=eq.{center}&material_id=eq.{pet['id']}", tok=O)[1][0]["buy_price_per_kg"]
    s, dl = rpc("create_delivery", {"p_center_id": center, "p_pickup_ids": pids}, R)
    assert s == 200, (s, dl); dl = dl if isinstance(dl, dict) else dl[0]
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    call("PATCH", f"/rest/v1/deliveries?id=eq.{dl['id']}", {"status": "recibida", "received_at": now, "received_by": oid}, O)
    weight = round(3 * 50 * float(pet["avg_weight_kg"]), 3)  # 1,25 kg per family
    call("POST", "/rest/v1/delivery_items", [{"delivery_id": dl["id"], "material_id": pet["id"], "weight_kg": weight, "quality_grade": "B", "price_per_kg": price, "grade_factor": 0.85}], O)
    call("PATCH", f"/rest/v1/deliveries?id=eq.{dl['id']}", {"status": "pesada", "weighed_at": now}, O)
    s, _ = rpc("settle_delivery", {"p_delivery_id": dl["id"]}, O)
    check("Operador liquida la entrega", s == 200, s)
    rpc("settle_delivery", {"p_delivery_id": dl["id"]}, O)  # a second click must not pay again
finally:
    sql(f"update platform_settings set referral_monthly_cap = {int(old_cap)} where id;")

_, ta = call("GET", f"/rest/v1/point_transactions?select=amount,type&reference_type=eq.referral&reference_id=eq.{ref_x}", tok=A)
_, tx = call("GET", f"/rest/v1/point_transactions?select=amount,type&reference_type=eq.referral&reference_id=eq.{ref_x}", tok=X)
_, sx = rpc("my_referral_summary", {}, X)
check("Tras el pesaje paga una sola vez: 2.000 al que invita y 1.000 al invitado",
      [t["amount"] for t in ta] == [2000] and [t["amount"] for t in tx] == [1000] and sx["invited_by"]["status"] == "pagado", [ta, tx, sx["invited_by"]])
_, ty = call("GET", f"/rest/v1/point_transactions?select=amount&reference_type=eq.referral&reference_id=eq.{ref_y}", tok=Y)
_, sy = rpc("my_referral_summary", {}, Y)
check("Pasado el tope del mes no paga: queda pendiente", ty == [] and sy["invited_by"]["status"] == "pendiente" and sy["invited_by"]["reason"] == "tope_mensual", [ty, sy["invited_by"]])
s, d = rpc("apply_referral_code", {"p_code": code}, Z)
check("Después de un pesaje ya no se atribuye", refused(s) and "pesaje" in json.dumps(d, ensure_ascii=False), [s, d])
_, sa = rpc("my_referral_summary", {}, A)
mine = {i["name"]: i["status"] for i in sa["invites"]}
check("El que invita ve a sus invitados y su estado", mine.get("Prueba Referido X") == "pagado" and mine.get("Prueba Referido Y") == "pendiente", sa["invites"][:3])

print(f"\n{sum(results)}/{len(results)} pasan")
sys.exit(0 if all(results) else 1)
