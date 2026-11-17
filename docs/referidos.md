# Invita y gana (referidos)

Estado al 2026-11-16. Todo lo de esta página está leído del script `supabase/sql/2026-11-12_referidos.sql` y del código
que existe; lo que no existe se dice como pendiente. Aplicado en **STAGING** (`mbbdrmicjobydprvbpsj`). Producción no
lo tiene.

## Qué es

Una familia (rol `generador`) comparte su código de 6 caracteres. Quien se registra con él queda atribuido a esa
persona. Cuando el acopio **pesa** el primer reciclaje del invitado y llega al mínimo, los dos reciben puntos.
Nadie cobra por registrarse: el premio depende de que el invitado recicle de verdad.

## Reglas (todas en la base; la app solo pide)

| Regla | Valor hoy en STAGING | Dónde vive |
|---|---|---|
| Premio al que invita | 2.000 pts (= S/ 2,00, con 1.000 pts = S/ 1) | `platform_settings.referral_referrer_points` |
| Premio al invitado | 1.000 pts | `referral_referred_points` |
| Mínimo pesado del invitado | 1 kg | `referral_min_kg` |
| Tope por invitante y mes | 5 pagados | `referral_monthly_cap` |
| Tope global por mes | 60 pagados | `referral_global_monthly_cap` |
| Ventana para ingresar el código | 7 días desde crear la cuenta | `referral_attach_days` |

Subir o bajar un premio es un `UPDATE` de `platform_settings`, no un cambio de código. La función `referral_prize()`
además suma al invitante el `referral_bonus_points` de su plan vigente; **ningún plan lo define hoy**
(ver `docs/planes-de-pago.md`).

- **Atribución una sola vez.** `referrals.referred_id` es único. Se atribuye en el registro (metadato `referral_code`
  del sign-up, leído por `handle_new_user`) o después con `rpc/apply_referral_code`, solo en la ventana de 7 días y
  nunca después de un primer pesaje. Nunca a uno mismo. Solo para familias.
- **Un código equivocado no cuesta la cuenta.** En el registro el error se ignora y el código se puede ingresar luego.
- **El pago lo dispara el pesaje.** El trigger `trg_pickup_referral` corre cuando `pickups.generator_points_final`
  pasa de nulo a un valor (lo escribe `settle_delivery`). Suma lo pesado del invitado (`confirmed_quantity` × peso
  promedio del material); si no llega a `referral_min_kg`, no paga todavía.
- **Los puntos van por el libro** `point_transactions` (tipo `referral`). Un índice único impide pagar dos veces la
  misma invitación a la misma persona. Cada lado recibe una notificación.
- **Antifraude antes de pagar** (se rechaza, sin pagar a nadie, con el motivo en `referrals.reason`):
  `mismo_telefono` (comparten los últimos 9 dígitos), `rider_vinculado` (el rider del recojo es el invitante o
  comparte teléfono con alguno), `misma_direccion` (direcciones guardadas iguales, o recojo a menos de 20 m de una
  dirección o recojo del invitante). 20 m también marca dos departamentos del mismo edificio: un operador puede
  pagarlos a mano.
- **Topes.** Pasado el tope mensual la invitación sigue `pendiente` con motivo `tope_mensual` y se paga en un pesaje
  del mes siguiente.
- **Permisos.** `referrals` y `referral_codes` no tienen escritura para la app; se leen con `rpc/my_referral_summary`
  (código, premios, pagados del mes, puntos ganados, `can_apply`, quién te invitó, lista de invitados).

Estados de `referrals.status`: `pendiente`, `pagado`, `rechazado`.

## Qué existe en las apps

Android (`ui/screens/generator/GeneratorInviteScreen.kt`):
- pantalla «Invita y gana» (ruta `GenRoutes.INVITE`) con `InviteViewModel`: código, copiar, compartir por mensaje,
  campo para ingresar el código de quien te invitó, lista de invitados con su estado;
- fila «Invita y gana» en `GeneratorProfileScreen` y tarjeta (`InvitePromptCard`) en el inicio;
- campo de código en `RegisterScreen` y `AuthViewModel`;
- `loadReferralSummary` / `applyReferralCode` en `RumaRepository`.

PWA:
- `src/screens/family/Invite.tsx`, `src/lib/referral.ts` (limpieza del código, `?ref=`, mensaje y enlace) y las dos
  llamadas en `src/lib/api.ts`; `scripts/check-invite.ts` prueba los ayudantes.
- Conectado (paquete B1): ruta `/f/invita` en `App.tsx` (importarla ejecuta `captureRef`, así el `?ref=` se guarda
  antes de que la entrada redirija), fila «Invita y gana» en el Perfil, `<InvitePrompt>` en el inicio, y el campo
  opcional «Código de invitación» del registro de familia, que `signUp` envía como metadato `referral_code`.

La tarjeta del inicio aparece como máximo una vez por semana por cuenta (`claimInvitePrompt` en la PWA y su gemelo en
Android): se recuerda, no se insiste.

## Por qué debería funcionar (y dónde se frena)

Los premios se pagan en puntos y solo cuando hay material pesado: es crecimiento que ya cuesta lo que vale (el punto
se financia con el valor del material). El invitante tiene un motivo social («te regalan puntos») y el invitado, un
motivo propio de bienvenida. Los topes y los chequeos de arriba acotan el costo del abuso: en el peor mes la
plataforma paga 60 invitaciones × (2.000 + 1.000) pts = 180.000 pts = S/ 180. La cifra del tope se decide con el
dueño (D6 del plan de trabajo).

## Cómo probar

```bash
python supabase/tests/referidos_staging.py   # espera 13/13
```

La prueba escribe en STAGING: crea cuentas `ref-*@ruma.test`, atribuciones y pagos a `generador@ruma.test`. Deja
suciedad por diseño (cuentas `ref-*` y referidos pagados o en `tope_mensual`). Ejecutar de a una prueba por vez, no en
paralelo con `planes_staging.py` (ambas tocan `platform_settings` y a `generador@ruma.test`).

## Qué falta para producción

1. Aplicar `2026-11-12_planes.sql` **antes** (define `user_benefits`, que `referral_prize` usa) y después
   `2026-11-12_referidos.sql`. El script reemplaza `handle_new_user`: leer el de producción antes de aplicarlo.
2. Verificar en teléfono el flujo completo (enlace `?ref=`, registro, primer pesaje).
3. Decidir los premios y topes (D6) y revisar los límites legales de la promoción con Indecopi: ver el apartado de
   límites de `docs/planes-de-pago.md`, que aplica igual a «invita y gana».
4. Actualizar el encabezado del SQL con la fecha de la réplica.
