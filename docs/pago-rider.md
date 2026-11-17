# ¿Cómo pagarle al Eco-Rider? Por recojo, por peso, por ruta o mixto (análisis, 2026-11-17)

Pedido del dueño: «pagarle al rider por peso no lo veo muy rentable; analiza pagar por rutas recogidas y por peso,
cuál es más rentable».

## Cómo se le paga hoy

`set_rider_fee()` (disparador en `pickups`, STAGING) paga **por recojo**:

- S/ 2,50 de base + S/ 0,30 por km, con tope de 15 km.
- Promedio real en staging: **S/ 4,52 por recojo** (44 recojos completados).
- No depende del peso ni de cuántas botellas recoge.

## Supuestos del cálculo

Están marcados para que se puedan cambiar.

- **Precios del material** (memoria del modelo de puntos): PET S/ 1,00/kg y cartón S/ 0,60/kg.
- **Botella PET promedio:** 25 g. El vecino recibe el 40 % del valor y Ruma retiene el 60 %.
- **Parada típica, ya con el mínimo y el bono por juntar** de `docs/puntos-por-botella.md`: 60 botellas PET (1,5 kg)
  + 1 kg de cartón.
  - Vale **S/ 2,10** en el acopio.
  - El vecino recibe S/ 0,84 y Ruma retiene **S/ 1,26**.
- **Ruta típica en Cartavio:** 15 paradas cercanas en unas 3 horas, unos 0,5 km entre paradas.
  - Material: 37,5 kg, que valen S/ 31,50.
  - Vecinos: S/ 12,60.
  - Lo que le queda a Ruma para pagar al rider: **S/ 18,90**.
- **Sueldo mínimo** de referencia: RMV S/ 1.230 al mes (parámetro del libro de costos), unos **S/ 5,90 por hora**.

## Comparación por ruta de 15 paradas

| Modelo | Pago al rider | Rider por hora | Resultado para Ruma por ruta | Riesgo principal |
|---|---|---|---|---|
| **Hoy: por recojo** (S/ 2,50 + S/ 0,30/km) | ~S/ 39,75 | S/ 13,25 | **−S/ 20,85** | Paga igual una parada de 5 botellas que una de 200 |
| **Solo por peso** (S/ 0,50/kg pesado) | S/ 18,75 | S/ 6,25 | **+S/ 0,15** | Con paradas chicas el rider gana poco y rechaza recojos |
| **Por ruta fija** (S/ 20 por ruta de 12 o más paradas) | S/ 20,00 | S/ 6,67 | −S/ 1,10 | Si las paradas son livianas, pierde Ruma |
| **Mixto: base por ruta + peso** (S/ 6 + S/ 0,35/kg) | S/ 19,13 | S/ 6,38 | **−S/ 0,23 (≈ 0)** | Equilibrado: base segura para el rider e incentivo a cargar más |
| **El rider se queda con el material** (modelo amigos, `docs/piloto-recicladores-san-pedro.md`) | S/ 31,50 al vender él | S/ 10,50 | −S/ 12,60 (puntos de los vecinos) | Ruma paga los puntos de su caja; no hay margen del material |

**Con paradas chicas** (20 botellas = 0,5 kg, que valen S/ 0,50):
- Ruma retiene S/ 0,30 por parada.
- Pagar por recojo pierde **S/ 2,35 por parada**.
- Pagar por peso pierde casi nada, porque el pago baja solo.
- **El pago por peso es el único que se protege solo cuando el recojo es pequeño.**

## Conclusión

1. **El modelo actual, por recojo, es el que más pierde.** Paga lo mismo sin importar cuánto material trae el rider,
   y con paradas chicas cada recojo cuesta más de lo que vale.
2. **El más rentable para Ruma es pagar por peso.** Pero solo por peso, el rider gana apenas el mínimo y puede
   rechazar las paradas livianas.
3. **Recomendado: el modelo mixto, pagado por ruta completada y no por parada:**
   - **Base de S/ 6 por ruta completada**, con 12 o más paradas, para que el rider tenga un piso.
   - **S/ 0,35 por kg pesado en el acopio:** no por el conteo en la puerta, sino por la balanza, para evitar inflar.
   - Pagar al liquidar la entrega (`settle_delivery`), igual que los puntos del vecino.
4. **Lo que realmente vuelve rentable cualquier modelo** no es el pago, sino la logística:
   - **Rutas por sector y día** («los martes, Cartavio centro»): más paradas por hora y menos km.
   - **Mínimo de 30 botellas por recojo** y el bono por juntar 80 o más (`docs/puntos-por-botella.md`): más kg por
     parada.
   - **Vender el material en fardos:** el PET compactado se paga mejor que el suelto. La prensa ya está en el libro
     de costos. Subirlo de S/ 1,00 a S/ 1,30/kg sumaría unos S/ 7 por ruta.
5. **Aun así, el margen del material por sí solo queda cerca de cero.** La ganancia de Ruma debe venir de las marcas
   (datos y patrocinios), la municipalidad, los planes B2B y Ruma Plus (`docs/planes-de-pago.md`), no del reciclaje
   en sí.

## Qué habría que cambiar (para implementar después, en STAGING primero)

- **`set_rider_fee()`:** dejar de fijar la tarifa al asignar.
- **`settle_delivery()`:** pagar al rider por kg pesado.
- **Base por ruta:** tabla o vista de «ruta del día» que sume la base cuando el rider completa 12 o más paradas.
- **Parámetros en la base:** base, precio por kg y mínimo de paradas en `platform_settings`, no fijos en el código.
- **App del rider:** mostrar «Esta ruta: S/ 6 + S/ 0,35 por kg» y la ganancia proyectada con los kg estimados.
- **Prueba en `supabase/tests/`:** el pago sale del peso pesado, la base solo con la ruta completa y el cliente no puede
  escribir su tarifa.

## Decisiones del dueño

- ¿Mixto (recomendado), solo peso o rider dueño del material para el piloto con amigos?
- Los valores: base por ruta, precio por kg y mínimo de paradas.
- ¿Rutas por sector y día fijo, o despacho libre como hoy?
