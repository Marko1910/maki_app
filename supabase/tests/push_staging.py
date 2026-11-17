# Checks supabase/sql/2026-11-12_push_notifications.sql + 2026-11-12_push_reglas.sql against STAGING with the
# *@ruma.test seed. Run from the repo root:
#   python supabase/tests/push_staging.py
#
# Client-side checks go through the REST API as the test accounts. What only the server can see (push_tokens,
# push_queue, pg_net) is read with the Supabase CLI, so it needs `npx supabase login` on the owning account.
# Writes in STAGING, all removed at the end: two test device tokens and three notifications titled
# "Prueba push (test)" for generador@ruma.test. If that account has a real phone registered and the secrets
# are set, the phone receives those test notices.
import json, os, re, subprocess, sys, tempfile, time, urllib.error, urllib.request, uuid

REF = "mbbdrmicjobydprvbpsj"
cfg = open("pwa/src/lib/config.ts", encoding="utf-8").read()
URL = re.search(r'STAGING_URL = "([^"]+)"', cfg).group(1)
KEY = re.search(r'STAGING_ANON_KEY = "([^"]+)"', cfg).group(1)
assert REF in URL, "solo STAGING"

def call(method, path, body=None, tok=None):
    h = {"apikey": KEY, "Authorization": "Bearer " + (tok or KEY), "Content-Type": "application/json"}
    req = urllib.request.Request(URL + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r: return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try: return e.code, json.loads(raw)
        except ValueError: return e.code, raw

def sql(query, write=False):
    """Rows of one statement, run server-side. A DO block that ends in `raise exception 'RESULT:…'` returns that text
    and leaves nothing behind: the exception rolls its work back."""
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f: f.write(query)
    try:
        mode = "--yes" if write else "--read-only"
        p = subprocess.run(f'npx supabase db query --remote={REF} --agent no --output-format json {mode} -f "{f.name}"',
                           shell=True, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    finally: os.unlink(f.name)
    out = p.stdout + p.stderr
    m = re.search(r"RESULT:([^\\\"\n]*)", out)
    if m: return m.group(1)
    start = min([i for i in (p.stdout.find("["), p.stdout.find("{")) if i >= 0], default=-1)
    if start < 0: raise RuntimeError(out.strip()[-400:] or "sin respuesta de la CLI")
    data = json.loads(p.stdout[start:])
    if isinstance(data, dict) and data.get("_tag") == "Error": raise RuntimeError(json.dumps(data.get("error"))[:400])
    return data.get("rows", data) if isinstance(data, dict) else data

def login(email):
    s, d = call("POST", "/auth/v1/token?grant_type=password", {"email": email, "password": "Maki12345!"})
    assert s == 200, (email, s, d)
    return d["access_token"], d["user"]["id"]

results, pending = [], []
def check(name, ok, seen=""):
    results.append(ok); print(("PASA " if ok else "FALLA") + f" | {name} | {json.dumps(seen, ensure_ascii=False, default=str)[:190]}")
def rpc(name, body, tok=None): return call("POST", "/rest/v1/rpc/" + name, body, tok)

T, uid = login("generador@ruma.test")
T2, uid2 = login("rider@ruma.test")
run = uuid.uuid4().hex
TOKEN = f"ruma-test-{run}:APA91b{run}"                      # shaped like an FCM token, registered nowhere
WEB = f"https://fcm.googleapis.com/fcm/send/ruma-test-{run}"   # a real push host, a subscription that does not exist
P256, AUTH = "B" + "A" * 86, "A" * 22
TITLE = "Prueba push (test)"

def cleanup():
    sql(f"delete from public.notifications where title = '{TITLE}' and user_id in ('{uid}','{uid2}')", write=True)
    sql("delete from public.push_tokens where token like '%ruma-test-%'", write=True)

try:
    # ---- 1. The client can only register / remove its own token -------------------------------------------
    s, d = rpc("register_push_token", {"p_token": TOKEN, "p_platform": "android"})
    check("Sin sesión no se registra un token", s in (401, 403, 404), [s, d])
    s, d = call("GET", "/rest/v1/push_tokens?select=token", tok=T)
    check("La app no lee push_tokens", s in (401, 403) or d == [], [s, d])
    s, d = call("GET", "/rest/v1/push_queue?select=notification_id", tok=T)
    check("La app no lee push_queue", s in (401, 403) or d == [], [s, d])
    s, d = call("POST", "/rest/v1/push_tokens", {"token": TOKEN + "x", "user_id": uid, "platform": "android"}, T)
    check("La app no inserta directo en push_tokens", s in (401, 403), [s, d])
    s, d = rpc("push_dispatch", {}, T)
    check("La app no puede disparar envíos (push_dispatch)", s in (401, 403, 404), [s, d])

    s, d = rpc("register_push_token", {"p_token": "corto"}, T)
    check("Token corto se rechaza", s == 400, [s, d])
    s, d = rpc("register_push_token", {"p_token": TOKEN, "p_platform": "ios"}, T)
    check("Plataforma desconocida se rechaza", s == 400, [s, d])
    s, d = rpc("register_push_token", {"p_token": "https://evil.example.com/push/abcdefghijklmnop", "p_platform": "web", "p_p256dh": P256, "p_auth": AUTH}, T)
    check("Web: un endpoint que no es de un servicio push se rechaza", s == 400, [s, d])
    s, d = rpc("register_push_token", {"p_token": WEB, "p_platform": "web"}, T)
    check("Web: sin claves p256dh/auth se rechaza", s == 400, [s, d])

    s, d = rpc("register_push_token", {"p_token": TOKEN, "p_platform": "android"}, T)
    s2, d2 = rpc("register_push_token", {"p_token": WEB, "p_platform": "web", "p_p256dh": P256, "p_auth": AUTH}, T)
    rows = sql(f"select platform, user_id::text, (p256dh is not null) as claves from public.push_tokens where token like '%ruma-test-{run}%' order by platform")
    check("Registra su token Android y su suscripción web, a su nombre",
          s in (200, 204) and s2 in (200, 204) and [(r["platform"], r["user_id"], r["claves"]) for r in rows] == [("android", uid, False), ("web", uid, True)], [s, s2, rows])

    s, d = rpc("unregister_push_token", {"p_token": TOKEN}, T2)
    left = sql(f"select count(*)::int as n from public.push_tokens where token = '{TOKEN}' and user_id = '{uid}'")[0]["n"]
    check("Otro usuario no puede borrar mi token", left == 1, [s, left])

    # ---- 2. The database decides what is pushed -------------------------------------------------------------
    def notice(user, push):
        return sql(f"insert into public.notifications (user_id, kind, title, body, push) values ('{user}', 'system', '{TITLE}', 'Aviso {push} de la prueba automática', '{push}') returning id", write=True)[0]["id"]
    def queued(nid):
        r = sql(f"select level, attempts, sent_at, last_error, (next_attempt_at <= now() + interval '1 hour') as pronto, expires_at > now() as vigente from public.push_queue where notification_id = {nid}")
        return r[0] if r else None

    started = sql("select now()::text as t")[0]["t"]
    n_none, n_urgent, n_normal = notice(uid, "none"), notice(uid, "urgent"), notice(uid, "normal")
    check("Aviso con push='none' (pasos repetidos, consejos) no entra a la cola", queued(n_none) is None, queued(n_none))
    q_u = queued(n_urgent)
    check("Aviso urgente entra a la cola para envío inmediato", bool(q_u) and q_u["level"] == "urgent" and q_u["vigente"], q_u)
    q_n = queued(n_normal)
    hour = sql("select extract(hour from now() at time zone 'America/Lima')::int as h")[0]["h"]
    night = hour >= 21 or hour < 8
    check(f"Aviso normal entra a la cola y {'espera a las 08:00 (es de noche en Lima)' if night else 'sale ya (es de día en Lima)'}",
          bool(q_n) and q_n["level"] == "normal" and ((q_n["attempts"] == 0) if night else q_n["pronto"]), q_n)

    sql(f"delete from public.push_tokens where user_id = '{uid2}' and token like '%ruma-test-%'", write=True)
    had = sql(f"select count(*)::int as n from public.push_tokens where user_id = '{uid2}'")[0]["n"]
    if had == 0:
        check("Usuario sin dispositivos: el aviso urgente no entra a la cola", queued(notice(uid2, "urgent")) is None)
    else:
        pending.append("Usuario sin dispositivos: rider@ruma.test tiene un teléfono real registrado, no se puede comprobar aquí")

    b = sql("""select public.push_not_before('normal','2026-11-12 03:30+00')::text as noche, public.push_not_before('normal','2026-11-12 12:59+00')::text as madrugada,
               public.push_not_before('normal','2026-11-12 13:00+00')::text as ocho, public.push_not_before('normal','2026-11-13 01:59+00')::text as tarde,
               public.push_not_before('urgent','2026-11-12 03:30+00')::text as urgente""", write=True)[0]
    check("Silencio 21:00-08:00 de Lima: lo normal espera a las 08:00, lo urgente no",
          b == {"noche": "2026-11-12 13:00:00+00", "madrugada": "2026-11-12 13:00:00+00", "ocho": "2026-11-12 13:00:00+00",
                "tarde": "2026-11-13 01:59:00+00", "urgente": "2026-11-12 03:30:00+00"}, b)

    # ---- 3. What each real pickup step asks for (the real trigger, inside a transaction that is rolled back) ----
    steps = sql("""do $$
declare pid uuid; rid uuid; t text; out text := '';
begin
  select id into pid from public.pickups where status = 'completed' and generator_points_final is null limit 1;
  if pid is not null then
    update public.pickups set generator_points_final = 7 where id = pid;
    select push into t from public.notifications where reference_id = pid order by id desc limit 1;
    out := out || 'pesado=' || t || ';';
  end if;
  select p.id, r.profile_id into pid, rid from public.pickups p, (select profile_id from public.rider_profiles limit 1) r where p.status = 'requested' limit 1;
  if pid is not null then
    update public.pickups set rider_id = rid, status = 'assigned' where id = pid;
    select string_agg(distinct push, ',') into t from public.notifications where reference_id = pid and kind = 'rider_assigned' and created_at = now();
    out := out || 'asignado=' || t || ';';
    update public.pickups set status = 'en_route' where id = pid;
    select push into t from public.notifications where reference_id = pid order by id desc limit 1; out := out || 'en_camino=' || t || ';';
    update public.pickups set status = 'arriving' where id = pid;
    select push into t from public.notifications where reference_id = pid order by id desc limit 1; out := out || 'llegando=' || t || ';';
    update public.pickups set status = 'collecting' where id = pid;
    select push into t from public.notifications where reference_id = pid order by id desc limit 1; out := out || 'llego=' || t || ';';
    update public.pickups set status = 'cancelled' where id = pid;
    select push into t from public.notifications where reference_id = pid order by id desc limit 1; out := out || 'cancelado=' || t || ';';
  end if;
  raise exception 'RESULT:%', out;
end $$;""", write=True)
    got = dict(x.split("=") for x in steps.split(";") if "=" in x) if isinstance(steps, str) else {}
    want = {"pesado": "normal", "asignado": "urgent", "en_camino": "urgent", "llegando": "none", "llego": "none", "cancelado": "urgent"}
    seen = {k: v for k, v in want.items() if k in got}
    for k in want.keys() - got.keys(): pending.append(f"Paso «{k}»: no hay en STAGING un recojo en el estado necesario para probarlo")
    check("Los pasos reales del recojo piden el push que dice la regla (" + ", ".join(seen) + ")", bool(seen) and all(got[k] == v for k, v in seen.items()), got)

    # ---- 4. The trigger calls the function (needs the vault secrets the owner sets) --------------------------
    vault = sql("select count(*)::int as n from vault.secrets where name in ('push_hook_secret','push_function_url')")[0]["n"]
    if vault < 2:
        check("Sin secreto en el vault no se llama a nadie: el aviso queda en cola sin intentos", bool(q_u) and q_u["attempts"] == 0, q_u)
        pending.append("Llamada a send-push y entrega: faltan los secretos (bash scripts/setup-push-secrets.sh <cuenta.json>); repite esta prueba después")
    else:
        check("El disparador llamó a send-push al insertar el aviso urgente", q_u["attempts"] >= 1, q_u)
        resp = []
        for _ in range(10):
            resp = sql(f"select status_code, left(content, 160) as content from net._http_response where created >= '{started}' and content like '%devices%' order by id desc limit 1")
            if resp: break
            time.sleep(3)
        check("send-push respondió 200 a la llamada firmada de la base (net._http_response)", bool(resp) and resp[0]["status_code"] == 200, resp)
        q_after = queued(n_urgent)
        check("La cola registra el resultado (enviado, o el error por reintentar)", bool(q_after) and (q_after["sent_at"] or q_after["last_error"]), q_after)
        # The test tokens belong to no device: both services say so and the function forgets them.
        gone = sql(f"select count(*)::int as n from public.push_tokens where token like '%ruma-test-{run}%'")[0]["n"]
        if resp and '"gone":2' in resp[0]["content"].replace(" ", ""):
            check("Los tokens que ya no existen se borran solos", gone == 0, gone)
        else:
            pending.append(f"Borrado de tokens muertos: depende de que FCM y VAPID estén configurados (respuesta: {resp[0]['content'] if resp else 'ninguna'})")
finally:
    cleanup()

left = sql("select (select count(*) from public.push_tokens where token like '%ruma-test-%')::int as tokens, "
           f"(select count(*) from public.notifications where title = '{TITLE}')::int as avisos")[0]
check("La prueba no deja datos en STAGING", left == {"tokens": 0, "avisos": 0}, left)

for p in pending: print("PENDIENTE | " + p)
print(f"\n{sum(results)}/{len(results)} pasan" + (f", {len(pending)} pendiente(s)" if pending else ""))
sys.exit(0 if all(results) else 1)
