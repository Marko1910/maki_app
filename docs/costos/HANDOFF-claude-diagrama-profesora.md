# ACTUALIZACIÓN 2026-11-06 (reemplaza lo de abajo)

La profesora rechazó la versión de 3 hojas de operación mensual. El usuario aclaró: costear el **proyecto de implementación** (desarrollo del software + montaje físico del piloto), con MD/MOD/CIF por **recursos** en dos hojas (físico: local, maquinaria, EPP, personal de campo; software: desarrollo) y **una hoja por cada bloque superior**.

El libro vigente tiene 7 hojas: 1 Recursos físicos, 2 Software, 3 Gastos administrativos, 4 Gastos de ventas, 5 Gastos financieros, 6 Utilidad, 7 IGV y precio de venta. Generador: `scripts/build_proyecto_profesora.py`. Parámetros editables arriba de la hoja 1 (meses del proyecto, meses de piloto, recojos, recolectores, tipo de cambio, RMV y cargas REMYPE). Maquinaria y equipos duraderos: depreciación SUNAT por los meses de uso; la compra figura como inversión fuera del costo. Solo PET y cartón: incluye prensa enfardadora, transpaleta, mesa de clasificación, sacas y zuncho (precios a cotizar). Resultado: costo total S/ 170.553,33; valor de venta S/ 204.663,99 (margen 20 % supuesto); precio de venta con IGV S/ 241.503,51; compra de maquinaria y equipos S/ 20.979 (inversión, fuera del costo). La versión anterior está en `respaldo/Ruma_costos_diagrama_profesora_version_codex.xlsx`.

---

# Continuación para Claude: diagrama de costos de la profesora

Fecha: 2026-11-06. Trabajo solicitado por el usuario, completado en un Excel
**aparte** del modelo de costos e inversionistas anterior.

## Indicación del usuario que debe conservarse

El usuario adjuntó `docs/costos/plantilla/diagrama_costos.jpeg` y explicó que la
profesora pidió trabajar los **tres recuadros inferiores** en dos hojas:

1. Una hoja para **la parte física**.
2. Otra hoja para **la parte software**.
3. Una tercera hoja con los cuadros restantes, **desde gastos administrativos
   hasta IGV o IVA**.

Pidió generar un Excel separado y dejar este contexto para que Claude pueda
continuarlo. No pidió modificar la app, las proyecciones de inversionistas ni
los supuestos del archivo anterior.

## Lectura del diagrama

De abajo hacia arriba:

- Materiales directos (MD).
- Mano de obra directa (MOD).
- Costos indirectos de fabricación (CIF).
- Gastos administrativos.
- Gastos de ventas.
- Gastos financieros.
- Utilidad.
- IGV o IVA.

Los rótulos laterales representan acumulados, no gastos adicionales:

```text
MD + MOD                                      = costo primo
Costo primo + CIF                             = costo de producción
Costo de producción + administración
  + ventas + financieros                      = costo total
Costo total + utilidad                        = valor de venta, sin IGV
Valor de venta + IGV                          = precio de venta, con IGV
```

En Ruma se adapta «fabricación/producción» a costo de prestación del servicio.

## Entregable

**`docs/costos/Ruma_costos_diagrama_profesora.xlsx`**, exactamente tres hojas:

- `1. Parte física`: MD, MOD, CIF, costo primo y costo del servicio físico.
- `2. Parte software`: MD, MOD, CIF, costo primo y costo del software.
- `3. Gastos y precio`: costos enlazados de las dos primeras, administración,
  ventas, financieros, utilidad propuesta, IGV y precio final.

Se tomó una copia de los valores calculados del **piloto distrital mensual** de
`Ruma_costos_2026-11.xlsx`: 180 activos, 360 recojos, 1.080 kg, dos riders.
Es una elección de implementación, **no una instrucción explícita de la profesora**.
El objeto de costo elegido es el servicio Ruma durante un mes, incluyendo
operación física y software; no solo la inversión inicial para desarrollar la app.
Si la profesora precisa otro objeto o periodo, adaptar estas tres hojas.

El archivo es autónomo, sin vínculos externos. Azul indica entradas editables.
Las celdas importadas incluyen comentarios con la hoja/celda de procedencia.
Los precios y dotaciones son supuestos heredados, no cotizaciones nuevas.

## Clasificación elegida y puntos que no deben perderse

1. **Físico**: compra del material mediante puntos como MD; rider y auxiliar de
   acopio como MOD; coordinador operativo, consumibles, equipos, conectividad y
   seguros como CIF. El auxiliar tiene cantidad cero porque pesa el acopio aliado.
2. **Hardware físico**: laptops y teléfonos se muestran en físico aunque los use
   software. Solo entra depreciación mensual, no compra completa más depreciación.
   Internet y coworking también se asignan una sola vez a físico.
3. **MD software = cero**: no se inventó una materia prima tangible para llenar
   el recuadro. Hosting, APIs, licencias y herramientas se presentan como CIF.
   Es una adaptación didáctica explícita; no afirmar que la profesora confirmó
   esta clasificación. Si ella prefiere «insumos digitales directos», reclasificar
   de forma trazable, sin sumar los mismos importes por segunda vez.
4. **Desarrollador como MOD software**, suponiendo trabajo directo y dedicado a
   Ruma. Antes figuraba como MOI; se reclasificó, no se duplicó su costo. Soporte
   queda en CIF software. Se conservan las cargas laborales del piloto del libro
   fuente, donde REMYPE es un supuesto, sin afirmar inscripción efectiva.
5. **Gastos financieros**: partida bancaria de S/ 50 y comisiones de transferencias
   (actualmente cero). Los intereses son cero porque no hay préstamo presupuestado.
   El origen decía «gastos bancarios y otros»: está marcado para separar otros
   conceptos cuando exista desglose, sin inventar una deuda.
6. **Utilidad**: `3. Gastos y precio!E5 = 20 %` es un recargo didáctico editable
   **sobre costo**, no margen del 20 % sobre ventas y no ganancia realizada.
   El usuario no dio este porcentaje; fue un supuesto visible de implementación.
   No reemplaza las pérdidas ni los resultados del modelo de inversionistas.
7. **IGV**: `3. Gastos y precio!E6 = 18 %` para la operación gravada del ejercicio.
   Se calcula sobre valor de venta, no sobre costo sin utilidad. No es ingreso,
   utilidad ni impuesto a la renta. Fuente oficial contrastada:
   https://orientacion.sunat.gob.pe/3053-concepto-tasa-y-operaciones-gravadas-igv-empresas
8. **IGV de compras**: se mantuvo el supuesto conservador heredado de servicios
   digitales con IGV como costo no recuperable. La hoja software expone la tasa
   editable en `E43`; si ese impuesto es recuperable, usar 0 % en esa celda de
   costo. El IGV de ventas se mantiene independiente. La fila se identifica
   también por «Tasa IGV compras digitales»; verificar dirección si se añaden filas.
9. **Precio final mensual**: representa el ingreso objetivo del conjunto del
   servicio. No es automáticamente la tarifa de una app, el precio por kg o lo
   que pagará una familia. Ruma tiene distintas fuentes de ingreso; no afirmar
   que el mercado acepta el precio calculado.

## Resultados iniciales (S/ por mes)

| Concepto | Importe |
|---|---:|
| Parte física | 4.720,69 |
| Parte software | 6.309,73 |
| Costo de producción combinado | 11.030,41 |
| Gastos administrativos | 4.435,00 |
| Gastos de ventas | 1.075,00 |
| Gastos financieros/bancarios | 50,00 |
| Costo total | 16.590,41 |
| Utilidad deseada, recargo del 20 % | 3.318,08 |
| Valor de venta sin IGV | 19.908,49 |
| IGV de venta | 3.583,53 |
| Precio de venta objetivo con IGV | 23.492,02 |

Los cálculos conservan precisión completa; por redondeo visual puede haber
un céntimo entre sumar subtotales mostrados y el total mostrado.

## Comprobación y reproducción

Excel recalculó **84 fórmulas sin errores**. Se verificaron las tres hojas,
ausencia de enlaces externos, cantidad × precio en cada partida, la formación
del precio y la conciliación exacta del costo total con el piloto fuente.
`3. Gastos y precio!F52` y `F53` dan cero inicialmente.
Tras editar costos, F52 puede cambiar legítimamente: compara con una referencia
histórica fija. F53 debe seguir dando cero.

- Generador: `docs/costos/scripts/build_plantilla_profesora.py`.
- Referencias: `docs/costos/plantilla/referencias_diagrama.json`.
- Resultados de verificación: `docs/costos/plantilla/verificacion_diagrama.json`.
- Imagen original: `docs/costos/plantilla/diagrama_costos.jpeg`.

```powershell
py -3.13 -X utf8 docs/costos/scripts/build_plantilla_profesora.py
& ./docs/costos/scripts/recalc_excel.ps1 -Path "$PWD/docs/costos/Ruma_costos_diagrama_profesora.xlsx"
```

**Regenerar reemplaza las ediciones del Excel separado.** Si el usuario o la
profesora lo editaron, conservar esas ediciones antes de ejecutar el generador.
No usar `-Verify` ni `-ExportPdf` del recalculador con este archivo: esas opciones
son específicas del modelo de inversionistas anterior.

El entregable solicitado está completo. Para futuras correcciones, mantener
las tres hojas pedidas y adaptar cantidades, clasificación, periodo o utilidad
a las precisiones que dé el usuario/la profesora. No mezclar nuevamente este
ejercicio con el archivo de inversionistas sin que lo soliciten.
