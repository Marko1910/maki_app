# Puntos por botella y por estado (análisis, 2026-11-16)

Pedido del dueño: «para incentivar el reciclaje es mejor asignar puntos por cantidad de botellas y el estado de cada
una; así tiene que juntar más botellas para ganar más dinero».

## Cómo funciona hoy

- **Al escanear** (`supabase/functions/detect-material/index.ts`, `pointsFor`):
  `puntos = cantidad × points_per_unit × (0,5 + 0,5 × calidad)`.
  - PET vale 10 pts por unidad y cartón 12.
  - La IA califica la `calidad` de 0 a 1: «limpieza/estado, 0 sucio o irreconocible, 1 limpio e íntegro».
- **Al pesar en el acopio** (`settle_delivery`): lo que se acredita sale del PESO del lote, repartido por el conteo
  del rider en la puerta y ajustado por el grado de calidad (A/B/C, `grade_factor`).
  - El número del escaneo es solo una estimación.
- **Economía de una botella PET** (`materials`: 25 g promedio, S/ 1,00/kg; el vecino recibe el 40 %):
  - vale S/ 0,025 en el acopio;
  - le deja al vecino S/ 0,01, es decir 10 pts;
  - 100 botellas = 1.000 pts = S/ 1.

Hoy ya es «por botella y por estado» en la estimación, pero hay tres problemas:
1. **Lo pagado no se entiende.** Al final manda el peso y el usuario no ve la regla «más botellas, más plata».
2. **La nota castiga lo que conviene.** «Íntegro» resta puntos a la botella **aplastada**, que es justo lo que
   ayuda: ocupa menos en el triciclo y en el acopio. Lo que debe restar es el líquido, la suciedad o los restos.
3. **El incentivo no premia juntar.** Un recojo de 20 botellas paga igual por botella que uno de 200, pero al
   negocio le cuesta lo mismo en rider. Por eso hoy cada recojo pierde unos S/ 2,42 (`docs/costos/`): el material
   de un recojo chico vale menos que la tarifa del rider (S/ 3 a 7).

## Recomendación: por botella, por estado y por tamaño del recojo

### 1. Pago visible por botella

La regla que ve el usuario: **«Cada botella limpia = 10 pts»**. La misma cifra aparece en el escaneo, en la puerta y
al acreditar.

### 2. Estado

Lo confirma el rider en la puerta y lo ajusta el grado del acopio:

| Estado | Factor | Por qué |
|---|---|---|
| Limpia y vacía (aplastada o no) | ×1,0 | Es lo que el acopio compra al mejor precio |
| Aplastada y limpia | ×1,1 | Ahorra espacio en el triciclo y en el acopio: es un premio pequeño y visible |
| Con líquido, restos o muy sucia | ×0,5 | El acopio la paga menos o la descarta |

- La IA sigue dando una calificación preliminar, pero se le pide «limpieza/contenido», no «íntegro».
- La palabra final la tienen el rider en la puerta y el acopio, como ya ocurre con la cantidad.

### 3. Bono por juntar más en un mismo recojo

Es lo que hace que «juntar más = ganar más» sea cierto y además mejore el negocio:

| Botellas en el recojo | Bono sobre los puntos del recojo |
|---|---|
| Menos de 30 | Sin bono. Se sugiere juntar más antes de pedir |
| 30 a 79 | +10 % |
| 80 o más | +20 % |

**Costo frente a ahorro:**
- Un recojo de 100 botellas paga 1.000 pts, y el bono del 20 % suma 200 pts (S/ 0,20).
- Si ese mismo material llegaba en 4 recojos de 25 botellas, se ahorran 3 visitas de rider: unos S/ 9 a 21.
- El bono se paga solo con lo que se ahorra en logística.

### 4. Tope contra el fraude

Se paga por conteo, pero nunca más que el peso medido × 400 pts/kg × 1,25. Con botellas diminutas, o el conteo
inflado, manda el peso.
- Así el usuario ve «por botella» y el negocio queda protegido por la balanza.

## Lo que cambia en el sistema (para implementar después, en STAGING primero)

1. **`detect-material`:**
   - El prompt pide `limpieza` (vacía, sin líquido ni restos) en vez de `íntegro`.
   - `pointsFor` usa el factor de estado. Hay que subir `PIPELINE_VERSION`.
2. **Confirmación del rider** (Android y PWA): un selector de estado por material (limpia / aplastada / con líquido)
   junto al contador que ya existe.
3. **`settle_delivery`:**
   - Paga por conteo confirmado × estado × bono por tamaño, con el tope por peso.
   - Todo en la base, con una prueba nueva en `supabase/tests/`.
4. **Textos:**
   - «Cada botella limpia = 10 pts».
   - En el resultado y en el formulario de recojo: «Junta 30 o más para ganar +10 %», con barra de progreso hacia el
     siguiente nivel.

## Decisiones del dueño

- ¿Aprueba los factores (×1,1 aplastada, ×0,5 sucia) y los niveles de bono (30 y 80 botellas)?
- ¿El mínimo de 30 botellas para pedir recojo debe ser obligatorio o solo una sugerencia?
- Cuidado con prometer montos en soles: 100 botellas son S/ 1. El mensaje motivador debe ir en puntos y en
  progreso, no en dinero grande.
