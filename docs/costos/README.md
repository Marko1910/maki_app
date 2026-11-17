# Costos y recuperación de inversión — Ruma

**Ejercicio de la profesora (archivo separado):**
`Ruma_costos_diagrama_profesora.xlsx` costea el proyecto de implementación en siete hojas:
recursos físicos, software y una hoja por cada bloque superior del diagrama (hasta IGV). La indicación del usuario y el criterio de
clasificación están en [HANDOFF-claude-diagrama-profesora.md](HANDOFF-claude-diagrama-profesora.md).

Continuación del trabajo de Claude, 2026-11-06. El pendiente era completar la
proyección mensual a cinco años y responder cuándo un inversionista recupera
su aporte. No se modificó la app ni se desplegaron cambios.

## Archivos para revisar

- **Ruma_costos_2026-11.xlsx**: libro principal actualizado. Abrir la primera hoja,
  **Inversionistas**. Incluye base, plan, sensibilidad, metodología y fuentes.
- **Ruma_inversionistas_2026-11.pdf**: resumen legible de dos páginas para compartir.
- **respaldo/Ruma_costos_2026-11_antes_recuperacion.xlsx**: original anterior a esta ampliación.

## Resultado con los supuestos guardados

| Indicador | Caso base | Plan con palancas |
|---|---:|---:|
| Inversión inicial | S/ 100.000 | S/ 100.000 |
| Mes de insuficiencia del capital inicial | 5 | 5 |
| Recuperación del inversionista | No ocurre en 60 meses | No ocurre en 60 meses |
| Primer dividendo | No ocurre en 60 meses | No ocurre en 60 meses |
| Capital total mínimo del proyecto, antes de dividendos | S/ 4.053.362 | S/ 1.351.834 |
| Aportes adicionales de todos los socios | S/ 3.953.362 | S/ 1.251.834 |
| Total aportado por el inversionista | S/ 890.672 | S/ 350.367 |
| Efectivo cobrado por el inversionista | S/ 0 | S/ 0 |

La participación supuesta es 20 %. El inversionista aporta los S/ 100.000
iniciales y luego su 20 % de cada déficit; otros socios aportan el 80 % restante.
**Es financiación hipotética, no comprometida.** Sin ella el proyecto no puede
continuar las operaciones proyectadas. Estos aportes adicionales se descuentan
del retorno: no se ocultan ni se atribuyen ganancias de toda la empresa al inversor.

El plan mantiene las palancas de Claude (8 kg/recojo y 3 paradas/viaje), pero
incorpora caja, impuestos, capital de trabajo, personal bajo régimen general,
crecimiento de costos y capacidad. Por eso no debe compararse directamente con
el punto de equilibrio estático de las hojas originales. El ahorro de tarifa
por agrupación exige renegociación: hoy la base del rider se paga por recojo.

La venta de acciones está apagada. Una valorización no realizada no se muestra
como recuperación. No recuperar dentro de cinco años no significa que sea
imposible recuperar en un horizonte posterior.

## Qué se completó y corrigió

- 60 meses de empresa e inversionista en ambos casos; nueve variantes de sensibilidad.
- VAN, TIR anual efectiva, MOIC de efectivo y recuperación simple/descontada,
  considerando aportes posteriores y recuperación sostenida hasta el mes 60.
- IR anual RMT/general, pérdidas A/B y anticipos con arrastre de créditos.
- Dividendos anuales limitados por pérdidas, reserva legal y caja mínima;
  retención de dividendos y venta final opcional separada.
- Capital de trabajo, compras y reemplazo de equipos, sin duplicar depreciación.
- Corrección del costo laboral: los doce salarios incluyen vacaciones pagadas;
  el reemplazo adicional es un supuesto separado, apagado por defecto.
- Fuentes de SUNAT y Ley General de Sociedades consultadas; trazabilidad de la
  tarifa del rider recuperada del historial de Claude, sin afirmar una nueva
  consulta a staging.

El modelo conserva simplificaciones: ejercicios completos de enero a diciembre,
UIT y tasas congeladas al corte, regularización tributaria al cierre anual,
sin deuda ni dilución, sin flujo neto de IGV ni ajustes fiscales de deducibilidad.
Las limitaciones completas están en **Metodología retorno**.

## Verificación

Excel recalculó **45.857 fórmulas** del libro principal sin errores. Se verificó
el libro principal y cinco copias de prueba: rentable, dividendos desactivados,
venta final, pérdidas sistema B y capital inicial insuficiente. Pasaron **64.779
comprobaciones numéricas** de caja, impuestos, aportes, reparto y retorno.

La prueba rentable recupera en mes 24 con parámetros deliberadamente extremos
para probar el cálculo. **No es una recomendación, meta comercial ni pronóstico.**
Con el reparto y la venta apagados, ese mismo negocio rentable no devuelve
efectivo al accionista: el modelo lo distingue correctamente.

Los archivos de `verificacion/` son pruebas técnicas, no versiones para inversores.
`verificacion/resultados.json` contiene los resultados de las comprobaciones.

## Reproducir

Requiere Python con openpyxl y Excel instalado. El exportador PDF también usa
PyMuPDF y matplotlib. Los scripts se guardan con el proyecto para no depender
del directorio temporal de Claude.

```powershell
py -3.13 -X utf8 docs/costos/scripts/build_costos.py
& ./docs/costos/scripts/recalc_excel.ps1 -Path "$PWD/docs/costos/Ruma_costos_2026-11.xlsx" -Verify
py -3.13 -X utf8 docs/costos/scripts/verificar_modelo.py
py -3.13 -X utf8 docs/costos/scripts/exportar_resumen.py
```

**El generador restaura los supuestos del código.** Si se cambian supuestos en
Excel, guardar una copia antes de regenerar. Se puede recalcular y exportar sin
ejecutar `build_costos.py` para conservar las ediciones manuales.
