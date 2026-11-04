# Fuentes de terceros

## Inter

`app/src/main/res/font/inter_{regular,medium,semibold,bold,extrabold}.ttf`

- **Origen:** `ofl/inter/Inter[opsz,wght].ttf` del repositorio
  [google/fonts](https://github.com/google/fonts), descargado el 2026-10-27.
- **Licencia:** SIL Open Font License 1.1 — texto completo en `Inter-OFL.txt`.
  Permite uso, modificación y redistribución empaquetada en la app, incluido el uso
  comercial. La OFL exige conservar el aviso de licencia: por eso está en este
  directorio y no se borra.
- **Autor:** Rasmus Andersson.

### Cómo se generaron estos cinco archivos

Google Fonts solo publica la variable. Se instanció cada peso y se recortó el juego de
caracteres con `fontTools`:

```
python genera_inter.py Inter[opsz,wght].ttf app/src/main/res/font .
```

- **Por qué estáticas y no la variable:** Compose aplica `FontVariation` solo desde
  API 26 y este módulo declara `minSdk 24`. Con la variable, en Android 7 todos los
  pesos caerían a Regular y la jerarquía tipográfica desaparecería justo en los
  teléfonos más humildes, que son parte del público de Ruma.
- **Por qué `opsz 14`:** es el tamaño óptico para el que Inter está dibujada a tamaños
  de interfaz. Si algún día las cifras grandes (40 sp) se ven apretadas, la mejora es
  añadir una instancia de display a `opsz 28`, no cambiar estas.
- **Subconjunto:** latin-1 y latin extendido-A completos, más los signos que la app
  imprime de verdad (derivados de los `.kt` y `.xml` de `app/src/main`) y la puntuación
  de interfaz. Verificado que no se perdió ningún glifo en uso: 344 por archivo,
  ~96 KB cada uno frente a los 876 KB de la variable. Los emoji los pinta la fuente del
  sistema, no Inter.

El script está en el directorio de trabajo temporal de la sesión, no en el repo: se
ejecuta una vez por actualización de la fuente y no forma parte de la compilación.
