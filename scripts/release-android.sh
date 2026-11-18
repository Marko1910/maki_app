#!/usr/bin/env bash
# Publica una versión nueva de la app Android como GitHub Release (tag android-v<versionCode>).
# La app la ve sola al abrirse (app/.../data/AppUpdater.kt), la descarga y abre el instalador.
#
# Uso (desde Git Bash, con `gh` iniciado en la cuenta dueña del repo):
#   scripts/release-android.sh "Qué cambió, en dos líneas: esto lo lee el tester en el aviso"
#
# Necesita la llave de firma en ../keys (fuera del repo): sin la MISMA llave Android rechaza la actualización.
set -euo pipefail
cd "$(dirname "$0")/.."

notes="${1:?Pasa las notas de la versión entre comillas}"
gradle=app/build.gradle.kts
[ -f ../keys/ruma-release.properties ] || { echo "Falta ../keys/ruma-release.properties (la llave de firma)." >&2; exit 1; }

# Siempre por encima de lo instalado (gradle) y de lo publicado: un fallo a medias solo salta un número, que no molesta.
last=$(gh release list --limit 100 --json tagName \
  -q '[.[].tagName | select(startswith("android-v")) | ltrimstr("android-v") | tonumber] | max // 0')
cur=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$gradle")
next=$(( (cur > last ? cur : last) + 1 ))
sed -i -E "s/^([[:space:]]*versionCode = )[0-9]+/\1$next/; s/^([[:space:]]*versionName = )\"[^\"]*\"/\1\"1.$next\"/" "$gradle"
echo "Versión $next (1.$next); la última publicada era $last."

./gradlew :app:assembleRelease
built=app/build/outputs/apk/release/app-release.apk
[ -f "$built" ] || { echo "No salió $built firmado: revisa ../keys." >&2; exit 1; }

mkdir -p ../entregas
apk="../entregas/Ruma-v$next.apk"
cp "$built" "$apk"

# ponytail: the tag points at the remote default branch, not this exact working tree; tag the commit when releases go formal.
gh release create "android-v$next" "$apk" --title "Ruma 1.$next" --notes "$notes" --latest
echo "Publicada android-v$next. Los testers la verán la próxima vez que abran la app."
echo "Recuerda hacer commit del versionCode en $gradle."
