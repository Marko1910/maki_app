#!/usr/bin/env bash
# Ruma: deja listos los secretos de los avisos push. Lo ejecuta el DUEÑO, en Git Bash, desde la raíz del repo:
#
#   bash scripts/setup-push-secrets.sh ruta/a/cuenta-de-servicio-firebase.json
#
# Qué hace, sin mostrar nunca un valor:
#   1. PUSH_HOOK_SECRET: genera uno nuevo y lo guarda a la vez en el vault de la base (push_hook_secret) y como
#      secreto de la función send-push. Junto a él guarda en el vault la URL de la función (push_function_url).
#   2. FCM_SERVICE_ACCOUNT: carga el JSON de la cuenta de servicio de Firebase (en base64, una sola línea).
#   3. Claves VAPID (Web Push): las genera solo si la función aún no las tiene. La privada va como secreto;
#      la pública se escribe en pwa/.env.staging.local y pwa/.env.development.local (VITE_VAPID_PUBLIC_KEY).
#
# Opciones:
#   --rotate-vapid   genera claves VAPID nuevas aunque existan (las suscripciones web actuales dejan de servir
#                    y cada teléfono se vuelve a suscribir solo al abrir la PWA).
#   --dry-run        genera todo en una carpeta temporal y no escribe nada en Supabase ni en pwa/.
#
# Requisitos: node, y la CLI de Supabase con sesión iniciada (npx supabase login) en la cuenta dueña del proyecto.
# Destino: STAGING. Para otro proyecto: RUMA_PROJECT_REF=<ref>; producción exige además RUMA_ALLOW_PRODUCTION=si.
set -euo pipefail

REF="${RUMA_PROJECT_REF:-mbbdrmicjobydprvbpsj}"
PRODUCTION="kqwyygxpcsmworvmjqdv"
SUBJECT="${RUMA_VAPID_SUBJECT:-https://ruma-recicla.vercel.app}"

SA_JSON=""; ROTATE_VAPID=0; DRY=0
for arg in "$@"; do
  case "$arg" in
    --rotate-vapid) ROTATE_VAPID=1 ;;
    --dry-run) DRY=1 ;;
    -*) echo "Opción desconocida: $arg" >&2; exit 2 ;;
    *) SA_JSON="$arg" ;;
  esac
done

die() { echo "ERROR: $*" >&2; exit 1; }

[ -n "$SA_JSON" ] || die "Falta la ruta del JSON de la cuenta de servicio. Uso: bash scripts/setup-push-secrets.sh <archivo.json>"
[ -f "$SA_JSON" ] || die "No existe el archivo: $SA_JSON"
[ -f "pwa/package.json" ] && [ -d "supabase/functions/send-push" ] || die "Ejecuta el script desde la raíz del repo."
command -v node >/dev/null || die "Falta node."
if [ "$REF" = "$PRODUCTION" ] && [ "${RUMA_ALLOW_PRODUCTION:-}" != "si" ]; then
  die "Ese es el proyecto de PRODUCCIÓN. Verifica todo en staging y repite con RUMA_ALLOW_PRODUCTION=si."
fi

umask 077
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# La CLI de Supabase es un programa de Windows: necesita rutas de Windows, no las de Git Bash.
native() { if command -v cygpath >/dev/null; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
sb() { npx --yes supabase "$@"; }

echo "Proyecto: $REF"

NEED_VAPID=1
if [ "$DRY" = 0 ]; then
  # La lista trae nombres y huellas, nunca valores; aun así no se muestra.
  NAMES="$(sb secrets list --remote="$REF" --agent no 2>/dev/null)" || die "No pude leer los secretos del proyecto. ¿Hiciste 'npx supabase login' con la cuenta dueña?"
  if [ "$ROTATE_VAPID" = 0 ] && printf '%s' "$NAMES" | grep -q "VAPID_PRIVATE_KEY"; then NEED_VAPID=0; fi
fi

# Todo valor secreto nace y muere dentro de $TMP (permisos 600, se borra al salir). Nada va a la pantalla.
SA_JSON="$SA_JSON" OUT="$TMP" REF="$REF" SUBJECT="$SUBJECT" NEED_VAPID="$NEED_VAPID" node <<'NODE' || die "No se pudieron preparar los secretos (¿el JSON es la clave de una cuenta de servicio de Firebase?)."
const fs = require("fs"), path = require("path"), crypto = require("crypto");
const { SA_JSON, OUT, REF, SUBJECT, NEED_VAPID } = process.env;
let sa;
try { sa = JSON.parse(fs.readFileSync(SA_JSON, "utf8")); } catch { console.error("El archivo no es JSON válido."); process.exit(1); }
if (sa.type !== "service_account" || !sa.project_id || !sa.client_email || !sa.private_key) {
  console.error("El JSON no es una clave de cuenta de servicio (faltan type/project_id/client_email/private_key).");
  process.exit(1);
}
const hook = crypto.randomBytes(32).toString("base64url");
const env = [
  `PUSH_HOOK_SECRET=${hook}`,
  // base64: una sola línea sin comillas ni saltos, que es lo que un archivo .env soporta sin sorpresas.
  `FCM_SERVICE_ACCOUNT=${Buffer.from(JSON.stringify(sa)).toString("base64")}`,
  `VAPID_SUBJECT=${SUBJECT}`,
];
if (NEED_VAPID === "1") {
  const { privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const jwk = privateKey.export({ format: "jwk" });
  const pub = Buffer.concat([Buffer.from([4]), Buffer.from(jwk.x, "base64url"), Buffer.from(jwk.y, "base64url")]).toString("base64url");
  env.push(`VAPID_PUBLIC_KEY=${pub}`, `VAPID_PRIVATE_KEY=${jwk.d}`);
  fs.writeFileSync(path.join(OUT, "vapid-public"), pub);
}
fs.writeFileSync(path.join(OUT, "secrets.env"), env.join("\n") + "\n");
const url = `https://${REF}.supabase.co/functions/v1/send-push`;
fs.writeFileSync(path.join(OUT, "vault.sql"), `do $ruma$
declare v uuid;
begin
  select id into v from vault.secrets where name = 'push_hook_secret';
  if v is null then perform vault.create_secret('${hook}', 'push_hook_secret', 'Firma de las llamadas a send-push');
  else perform vault.update_secret(v, '${hook}'); end if;
  select id into v from vault.secrets where name = 'push_function_url';
  if v is null then perform vault.create_secret('${url}', 'push_function_url', 'URL de la Edge Function send-push');
  else perform vault.update_secret(v, '${url}'); end if;
end $ruma$;
`);
NODE

if [ "$DRY" = 1 ]; then
  echo "Ensayo (--dry-run): se generaron $(grep -c '' "$TMP/secrets.env") secretos y el SQL del vault; no se escribió nada."
  exit 0
fi

echo "1/3 Secretos de la función send-push…"
sb secrets set --env-file "$(native "$TMP/secrets.env")" --remote="$REF" --yes --agent no >/dev/null 2>&1 \
  || die "La CLI rechazó los secretos. Comprueba 'npx supabase login' y que la cuenta tenga acceso al proyecto."

echo "2/3 Firma y URL en el vault de la base…"
sb db query --remote="$REF" --agent no --yes -f "$(native "$TMP/vault.sql")" >/dev/null 2>&1 \
  || die "No se pudo escribir en el vault. La función ya tiene la firma nueva: repite el script para igualarlas."

echo "3/3 Clave pública VAPID para la PWA…"
if [ "$NEED_VAPID" = 1 ]; then
  PUB="$(cat "$TMP/vapid-public")"
  for f in pwa/.env.staging.local pwa/.env.development.local; do
    touch "$f"
    grep -v '^VITE_VAPID_PUBLIC_KEY=' "$f" > "$TMP/env" || true
    printf 'VITE_VAPID_PUBLIC_KEY=%s\n' "$PUB" >> "$TMP/env"
    cat "$TMP/env" > "$f"
  done
  echo "    Claves nuevas. La pública quedó en pwa/.env.staging.local y pwa/.env.development.local."
elif grep -qs '^VITE_VAPID_PUBLIC_KEY=.' pwa/.env.staging.local; then
  echo "    Ya existían; no se tocaron."
else
  echo "    AVISO: la función ya tiene claves VAPID pero pwa/.env.staging.local no tiene VITE_VAPID_PUBLIC_KEY."
  echo "    Copia ese archivo desde la máquina donde se generaron, o repite con --rotate-vapid."
fi

# Comprobación por nombres, nunca por valores.
NAMES="$(sb secrets list --remote="$REF" --agent no 2>/dev/null || true)"
for n in PUSH_HOOK_SECRET FCM_SERVICE_ACCOUNT VAPID_PUBLIC_KEY VAPID_PRIVATE_KEY VAPID_SUBJECT; do
  if printf '%s' "$NAMES" | grep -q "$n"; then echo "    ok    $n"; else echo "    FALTA $n"; fi
done
VAULT="$(sb db query --remote="$REF" --read-only --agent no --output-format json \
  "select count(*) as n from vault.secrets where name in ('push_hook_secret','push_function_url')" 2>/dev/null || true)"
if printf '%s' "$VAULT" | grep -Eq '"n": *"?2'; then echo "    ok    vault (push_hook_secret, push_function_url)"; else echo "    REVISAR vault: no pude confirmar las dos entradas"; fi

cat <<'FIN'

Listo. Sigue con docs/push-setup.md:
  - PWA: en pwa/, npm run build:staging y publica (la clave pública entra en la compilación).
  - Prueba: python supabase/tests/push_staging.py
FIN
