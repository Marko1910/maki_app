# Construye docs/costos/Ruma_costos_2026-11.xlsx con fórmulas reales.
import os
from pathlib import Path
from openpyxl import Workbook
from openpyxl.chart import BarChart, Reference, ScatterChart, Series
from openpyxl.formatting.rule import CellIsRule
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

OUT = str(Path(__file__).resolve().parents[1] / "Ruma_costos_2026-11.xlsx")
HOY = "2026-11-06"
SC = ["MVP / demo (hoy)", "Piloto distrital", "Ciudad"]
SUPC = ["C", "D", "E"]  # columnas de escenario en Supuestos, Escenarios, Estado, Ingresos, PE, Resumen, Inversionistas

F = "Arial"
f_base = Font(name=F, size=10)
f_in = Font(name=F, size=10, color="0000FF")
f_link = Font(name=F, size=10, color="008000")
f_bold = Font(name=F, size=10, bold=True)
f_hdr = Font(name=F, size=10, bold=True, color="FFFFFF")
f_title = Font(name=F, size=16, bold=True, color="1F4E3D")
f_sub = Font(name=F, size=10, italic=True, color="555555")
f_sec = Font(name=F, size=11, bold=True, color="1F4E3D")
fill_hdr = PatternFill("solid", fgColor="1F4E3D")
fill_sec = PatternFill("solid", fgColor="E2EFDA")
fill_key = PatternFill("solid", fgColor="FFFF00")
fill_tot = PatternFill("solid", fgColor="F2F2F2")
thin = Side(style="thin", color="BFBFBF")
b_all = Border(left=thin, right=thin, top=thin, bottom=thin)
b_top = Border(top=Side(style="thin", color="000000"), bottom=Side(style="double", color="000000"))
wrap = Alignment(wrap_text=True, vertical="top")
center = Alignment(horizontal="center", vertical="center", wrap_text=True)
right = Alignment(horizontal="right")

SOL = '"S/" #,##0.00;("S/" #,##0.00);"-"'
SOL0 = '"S/" #,##0;("S/" #,##0);"-"'
USD = '"US$" #,##0.00;("US$" #,##0.00);"-"'
USD4 = '"US$" #,##0.0000;("US$" #,##0.0000);"-"'
INT = '#,##0;(#,##0);"-"'
DEC1 = '#,##0.0;(#,##0.0);"-"'
DEC2 = '#,##0.00;(#,##0.00);"-"'
DEC3 = '0.000'
PCT = '0.0%;(0.0%);"-"'
PCT2 = '0.00%;(0.00%);"-"'
FLAG = '0'

wb = Workbook()
ws_inv = wb.active
ws_inv.title = "Inversionistas"
ws_res = wb.create_sheet("Resumen")
ws_sup = wb.create_sheet("Supuestos")
ws_est = wb.create_sheet("Estado de costos")
ws_ing = wb.create_sheet("Ingresos")
ws_pe = wb.create_sheet("Punto de equilibrio")
ws_mo = wb.create_sheet("Mano de obra")
ws_inf = wb.create_sheet("Infraestructura")
ws_var = wb.create_sheet("Costos variables")
ws_ind = wb.create_sheet("Indirectos y gastos")
ws_uni = wb.create_sheet("Única vez")
ws_esc = wb.create_sheet("Escenarios")
ws_src = wb.create_sheet("Fuentes y notas")


def q(ws):
    return f"'{ws.title}'"


def put(ws, ref, value, font=f_base, fmt=None, fill=None, align=None, border=None):
    c = ws[ref]
    c.value = value
    c.font = font
    if fmt:
        c.number_format = fmt
    if fill:
        c.fill = fill
    if align:
        c.alignment = align
    if border:
        c.border = border
    return c


def title(ws, text, sub):
    put(ws, "A1", text, f_title)
    put(ws, "A2", sub, f_sub)


def header(ws, row, labels, start=1):
    for i, lab in enumerate(labels):
        put(ws, f"{get_column_letter(start + i)}{row}", lab, f_hdr, fill=fill_hdr, align=center, border=b_all)
    ws.row_dimensions[row].height = 30


def group_header(ws, row, groups):
    for c0, txt in groups:
        put(ws, f"{c0}{row}", txt, f_hdr, fill=fill_hdr, align=center)
        end = get_column_letter(ws[f"{c0}{row}"].column + 2)
        ws.merge_cells(f"{c0}{row}:{end}{row}")


def section(ws, row, text, ncols):
    for col in range(1, ncols + 1):
        ws.cell(row=row, column=col).fill = fill_sec
    put(ws, f"A{row}", text, f_sec, fill=fill_sec)


def note(ws, row, text, last_col, height=30):
    put(ws, f"A{row}", text, f_sub, align=wrap)
    ws.merge_cells(f"A{row}:{last_col}{row}")
    ws.row_dimensions[row].height = height


def widths(ws, ws_widths):
    for col, w in ws_widths.items():
        ws.column_dimensions[col].width = w


# ============================================================ Supuestos
S = {}
title(ws_sup, "Supuestos del modelo de costos — Ruma",
      f"Fecha de corte: {HOY}. Toda otra hoja lee de aquí: cambie un número y el libro se recalcula.")
put(ws_sup, "A3", "Leyenda: texto azul = dato ingresado (editable) · fondo amarillo = supuesto clave · "
                  "texto negro = fórmula · Estado: VERIFICADO (fuente oficial consultada), REFERENCIA (fuente de "
                  "terceros), DEL PROYECTO (código/BD de Ruma), SUPUESTO (estimación editable). Fuentes: hoja «Fuentes y notas». "
                  "Indicadores 1/0: 1 = sí, 0 = no.",
    f_sub, align=wrap)
ws_sup.merge_cells("A3:G3")
ws_sup.row_dimensions[3].height = 40
header(ws_sup, 5, ["Parámetro", "Unidad", SC[0], SC[1], SC[2], "Estado", "Fuente / nota"])
srow = [6]


def ssec(text):
    section(ws_sup, srow[0], text, 7)
    srow[0] += 1


def ssub(text):
    put(ws_sup, f"A{srow[0]}", text, f_bold)
    srow[0] += 1


def add(key, label, unit, vals, estado, nota, fmt, keyfill=False):
    r = srow[0]
    put(ws_sup, f"A{r}", label, align=wrap)
    put(ws_sup, f"B{r}", unit, align=wrap)
    glob = not isinstance(vals, list)
    seq = [vals] if glob else vals
    for i, v in enumerate(seq):
        is_formula = isinstance(v, str) and v.startswith("=")
        put(ws_sup, f"{SUPC[i]}{r}", v, f_base if is_formula else f_in, fmt=fmt,
            fill=fill_key if keyfill else None, border=b_all)
    if glob:
        for col in ("D", "E"):
            put(ws_sup, f"{col}{r}", "(todos)", f_sub, align=Alignment(horizontal="center"))
    put(ws_sup, f"F{r}", estado, f_bold if estado == "SUPUESTO" else f_base)
    put(ws_sup, f"G{r}", nota, align=wrap)
    S[key] = (r, glob)
    srow[0] += 1


def sup(key, i=0):
    r, glob = S[key]
    return f"Supuestos!$C${r}" if glob else f"Supuestos!{SUPC[i]}${r}"


def sup_row(key):
    r, _ = S[key]
    return f"Supuestos!$C${r}:$E${r}"


ssec("1. Generales")
add("tc", "Tipo de cambio (S/ por US$)", "S/ por US$", 3.452, "VERIFICADO",
    "[F1] SUNAT venta del 06-nov-2026 (S/ 3,446 compra / 3,452 venta). Cierre BCRP del 05-nov: S/ 3,445.", DEC3, True)
add("igv", "IGV", "%", 0.18, "VERIFICADO", "[F2] 18 %. D. Leg. 1623 grava servicios digitales del exterior desde el 01-oct-2024.", PCT)
add("igv_on", "¿Sumar IGV a servicios digitales del exterior? (1/0)", "indicador", 1, "SUPUESTO",
    "Conservador: como persona natural el 18 % se cobra en la tarjeta. Una empresa con RUC lo paga como IGV por "
    "utilización de servicios y lo recupera como crédito fiscal: entonces poner 0.", FLAG, True)
add("dias", "Días operativos por mes", "días", 26, "SUPUESTO", "Lunes a sábado.", INT)

ssec("2. Volúmenes por escenario")
add("reg", "Recolectores registrados", "personas", [10, 300, 5000], "SUPUESTO",
    "MVP = cuentas de demo; piloto = un distrito; ciudad = varios distritos.", INT, True)
add("act_pct", "Recolectores activos en el mes", "% de registrados", [1.0, 0.6, 0.5], "SUPUESTO",
    "Hacen al menos un escaneo o recojo en el mes.", PCT, True)
add("dau_pct", "Recolectores activos en un día promedio (DAU)", "% de registrados", [0.3, 0.15, 0.15], "SUPUESTO",
    "Mueve el egress de Supabase.", PCT)
add("riders", "EcoRiders", "personas", [2, 2, 25], "SUPUESTO", "Del planteamiento de escenarios.", INT, True)
add("acopios", "Centros de acopio con panel", "centros", [1, 1, 3], "SUPUESTO", "Usan el panel web (dashboard/).", INT)
add("scans", "Escaneos por recolector activo al mes", "escaneos", [4, 4, 4], "SUPUESTO", "Un escaneo = una sesión de cámara.", DEC1, True)
add("photos", "Fotos analizadas por escaneo", "fotos", [2, 2, 2], "SUPUESTO",
    "[P4] Cada foto («Otra foto») es una llamada de visión; el commit no llama al modelo.", DEC1)
add("pick", "Recojos por recolector activo al mes", "recojos", [2, 2, 2], "SUPUESTO", "Un recojo cada dos semanas.", DEC1, True)
add("chat", "Mensajes al asistente (chat) por recolector activo al mes", "mensajes", [3, 3, 3], "SUPUESTO",
    "Edge Function maki-assistant.", DEC1)
add("real_pay", "¿Se paga con dinero real a riders y recolectores? (1/0)", "indicador", [0, 1, 1], "SUPUESTO",
    "MVP: los pagos de staging son de prueba.", FLAG, True)
add("ia_pay", "¿IA de visión y chat con facturación activa? (1/0)", "indicador", [0, 1, 1], "SUPUESTO",
    "[P3] El nivel gratis aguanta ~3 escáneres simultáneos: suficiente para una demo, no para un piloto.", FLAG, True)

ssec("3. Economía operativa (pagos por recojo)")
add("pps", "Puntos por S/ 1,00", "puntos", 1000, "DEL PROYECTO", "[P1] 1.000 pts = S/ 1,00 (canje Yape/Plin).", INT)
add("share", "Parte del valor del material que recibe el recolector", "%", 0.40, "DEL PROYECTO", "[P1] Regla del 40 %.", PCT, True)
add("canje", "Puntos que se llegan a canjear por dinero", "% de los emitidos", 1.0, "SUPUESTO",
    "Conservador: todo punto emitido se paga.", PCT)
add("rider_mode", "Modalidad de los EcoRiders: 1 = independientes por recojo, 2 = planilla", "código", [1, 1, 1], "SUPUESTO",
    "Hoy cobran por recojo como independientes (no planilla). Poner 2 para ver la alternativa en planilla.", FLAG, True)
add("fee_base", "Pago al EcoRider: tarifa base", "S/ por viaje", 2.50, "DEL PROYECTO",
    "[P2] Trigger trg_set_rider_fee en STAGING (S/ 2,50 + S/ 0,30 por km). Hoy un viaje = un recojo.", SOL, True)
add("fee_km", "Pago al EcoRider: adicional por km", "S/ por km", 0.30, "DEL PROYECTO", "[P2]", SOL)
add("paradas", "Recojos (paradas) por viaje", "recojos", 1, "SUPUESTO",
    "Hoy 1. Con rutas de varias paradas la tarifa base se reparte entre los recojos del viaje.", DEC1, True)
add("km", "Distancia promedio por recojo", "km", 2.0, "SUPUESTO", "La asignación busca al rider más cercano (≤ 20 km).", DEC1, True)
add("kg", "Peso promedio por recojo", "kg", 3.0, "SUPUESTO",
    "Palanca principal del margen: ver «Punto de equilibrio».", DEC1, True)
add("commission", "Comisión por enviar pagos (Yape/Plin/transferencia)", "% de lo pagado", 0.0, "SUPUESTO",
    "Por verificar con el banco o billetera que se use para pagos masivos.", PCT)
ssub("Mezcla de materiales y precio de compra del acopio")
add("p_pet", "Precio PET", "S/ por kg", 1.00, "DEL PROYECTO", "[P1] supabase/sql/2026-10-09_precios_reales_y_arbitrios.sql", SOL)
add("p_alu", "Precio aluminio", "S/ por kg", 4.00, "DEL PROYECTO", "[P1]", SOL)
add("p_car", "Precio cartón", "S/ por kg", 0.60, "DEL PROYECTO", "[P1]", SOL)
add("p_vid", "Precio vidrio", "S/ por kg", 0.20, "DEL PROYECTO", "[P1]", SOL)
add("p_pil", "Precio pilas", "S/ por kg", 0.00, "DEL PROYECTO", "[P1] Nadie las compra; incentivo cívico.", SOL)
add("m_pet", "Participación de PET en el peso del recojo", "% del peso", 0.35, "SUPUESTO", "", PCT)
add("m_alu", "Participación de aluminio", "% del peso", 0.05, "SUPUESTO", "", PCT)
add("m_car", "Participación de cartón", "% del peso", 0.40, "SUPUESTO", "", PCT)
add("m_vid", "Participación de vidrio", "% del peso", 0.20, "SUPUESTO", "", PCT)
add("m_pil", "Participación de pilas", "% del peso", 0.00, "SUPUESTO", "", PCT)
mats = ["pet", "alu", "car", "vid", "pil"]
add("mix_sum", "Control: suma de participaciones (debe ser 100 %)", "%",
    "=" + "+".join(sup(f"m_{m}") for m in mats), "CONTROL", "Si no da 100 %, el precio ponderado está mal.", PCT)
add("p_avg", "Precio promedio ponderado del material", "S/ por kg",
    "=" + "+".join(f"{sup('p_' + m)}*{sup('m_' + m)}" for m in mats), "FÓRMULA", "", SOL)

ssec("4. IA de visión y chat (Gemini, pago por token)")
add("tok_in", "Tokens de entrada por foto", "tokens", 5100, "SUPUESTO",
    "[P4] Prompt + catálogo de 54 marcas ≈ 14.000 caracteres ≈ 4.000 tokens, más la imagen de 1024 px ≈ 1.100.", INT)
add("tok_out", "Tokens de salida por foto (incluye razonamiento)", "tokens", 500, "SUPUESTO",
    "[P4] Tope en el código: 1.024 tokens por llamada a Gemini.", INT)
add("hedge", "Factor por llamadas extra (coberturas y reintentos)", "veces", 1.2, "SUPUESTO",
    "Si un proveedor calla 3 s se lanza el siguiente: a veces se pagan dos llamadas por foto.", DEC2)
add("vis_in", "Gemini 3.5 Flash-Lite — entrada (modelo de visión del cálculo)", "US$ por millón de tokens", 0.30,
    "VERIFICADO", "[F6] Precio de pago estándar. Supuesto: flash-lite-latest apunta a este modelo.", USD, True)
add("vis_out", "Gemini 3.5 Flash-Lite — salida", "US$ por millón de tokens", 2.50, "VERIFICADO", "[F6]", USD, True)
add("chat_tok_in", "Tokens de entrada por mensaje de chat", "tokens", 3000, "SUPUESTO",
    "Contexto: reglas de envases + puntos, detecciones y precios del usuario.", INT)
add("chat_tok_out", "Tokens de salida por mensaje de chat", "tokens", 400, "SUPUESTO", "Tope en el código: 1.024.", INT)
add("chat_in", "Gemini 2.5 Flash (chat) — entrada", "US$ por millón de tokens", 0.30, "VERIFICADO",
    "[F6] Deprecado: desde el 01-dic-2026 se enruta a 3.5 Flash-Lite, que cobra lo mismo.", USD)
add("chat_out", "Gemini 2.5 Flash (chat) — salida", "US$ por millón de tokens", 2.50, "VERIFICADO", "[F6]", USD)
ssub("Modelos alternativos (solo para la comparación de «Costos variables»)")
ALT = [
    ("g25", "Gemini 2.5 Flash-Lite", 0.10, 0.40, "VERIFICADO", "[F6]"),
    ("g31", "Gemini 3.1 Flash-Lite", 0.25, 1.50, "VERIFICADO", "[F6]"),
    ("g35", "Gemini 3.5 Flash-Lite (el del cálculo)", f"={sup('vis_in')}", f"={sup('vis_out')}", "VERIFICADO", "[F6]"),
    ("hk", "Claude Haiku 5.5", 0.10, 0.50, "VERIFICADO", "[F7] Prompts de hasta 100 mil tokens."),
    ("qw", "Groq Qwen3.8-27B (preview)", 0.80, 4.00, "REFERENCIA", "[F8] Precio de un agregador; no se vio en groq.com."),
]
for k, name, pin, pout, est, nota in ALT:
    add(f"alt_{k}_in", f"{name} — entrada", "US$ por millón de tokens", pin, est, nota, USD)
    add(f"alt_{k}_out", f"{name} — salida", "US$ por millón de tokens", pout, est, nota, USD)

ssec("5. Avisos y mensajería")
add("wa_ref", "WhatsApp Business, plantilla utility a Perú (tarifa de referencia)", "US$ por mensaje", 0.024,
    "REFERENCIA", "[F17] Tabla de un tercero (sep-2026), anterior al alza del 01-oct-2026.", USD4)
add("wa_alza", "Alza estimada de la tarifa de WhatsApp desde el 01-oct-2026", "%", 0.25, "SUPUESTO",
    "[F17] Meta anunció el alza para Perú sin que la página muestre el monto nuevo.", PCT)
add("wa_pct", "Recojos que también avisan por WhatsApp", "% de recojos", [0.0, 0.0, 0.10], "SUPUESTO",
    "Respaldo opcional del push (FCM y Web Push son gratis).", PCT)
add("wa_msgs", "Mensajes de WhatsApp por recojo avisado", "mensajes", 2, "SUPUESTO", "«Rider en camino» y «recojo completado».", DEC1)
add("sms", "SMS a Perú (Twilio)", "US$ por segmento", 0.2476, "VERIFICADO", "[F18]", USD4)
add("sms_pct", "Recojos que avisan por SMS", "% de recojos", [0.0, 0.0, 0.0], "SUPUESTO", "Alternativa cara; apagada por defecto.", PCT)
add("sms_msgs", "SMS por recojo avisado", "mensajes", 1, "SUPUESTO", "", DEC1)
add("push", "Avisos push por recojo (FCM / Web Push)", "avisos", 6, "SUPUESTO",
    "Uno por cambio de estado del recojo. Pendiente de implementar.", INT)

ssec("6. Infraestructura tecnológica")
add("sb_pro", "Supabase: organización en plan Pro (1/0)", "indicador", [0, 1, 1], "SUPUESTO",
    "MVP: 2 proyectos en Free (el Free permite 2 activos).", FLAG, True)
add("sb_pro_price", "Supabase Pro", "US$ por organización al mes", 25, "VERIFICADO", "[F3] Incluye US$ 10 de cómputo.", USD)
add("sb_credit", "Supabase: crédito de cómputo incluido en Pro", "US$ al mes", 10, "VERIFICADO", "[F3] Cubre una instancia Micro.", USD)
add("sb_micro_price", "Supabase cómputo Micro", "US$ por proyecto al mes", 10, "VERIFICADO", "[F3]", USD)
add("sb_small_price", "Supabase cómputo Small", "US$ por proyecto al mes", 15, "VERIFICADO", "[F3]", USD)
add("sb_micro_n", "Proyectos en instancia Micro", "proyectos", [0, 2, 1], "SUPUESTO",
    "Piloto: producción + staging en Micro. Ciudad: staging en Micro.", INT)
add("sb_small_n", "Proyectos en instancia Small", "proyectos", [0, 0, 1], "SUPUESTO", "Ciudad: producción en Small.", INT)
add("sb_egress_incl", "Supabase: egress incluido", "GB al mes", [5, 250, 250], "VERIFICADO", "[F3] Free 5 GB; Pro 250 GB.", INT)
add("sb_egress_over", "Supabase: egress excedente", "US$ por GB", 0.09, "VERIFICADO", "[F3]", USD)
add("sb_mb_dau", "Egress por recolector activo diario", "MB al mes", 80, "SUPUESTO",
    "[P5] Derivado del análisis previo: 5 GB del Free ≈ 50–75 DAU; 250 GB del Pro ≈ 2.500–3.700.", INT)
add("sb_inv_incl", "Supabase: invocaciones de Edge Functions incluidas", "invocaciones al mes",
    [500000, 2000000, 2000000], "VERIFICADO / SUPUESTO", "[F3] Pro 2 millones (verificado). Free 500 mil (no reverificado).", INT)
add("sb_inv_over", "Supabase: invocaciones excedentes", "US$ por millón", 2, "VERIFICADO", "[F3]", USD)
add("vc_seats", "Vercel Pro: asientos de desarrollador", "asientos", [0, 1, 2], "SUPUESTO",
    "MVP en Hobby (gratis, solo uso no comercial). PWA y panel del acopio en el mismo equipo.", INT)
add("vc_price", "Vercel Pro", "US$ por asiento al mes", 20, "VERIFICADO", "[F9] Incluye US$ 20 de uso; se supone que alcanza.", USD)
add("map_paid", "Mapas y rutas con proveedor de pago (1/0)", "indicador", [0, 1, 1], "SUPUESTO",
    "MVP: teselas de OpenStreetMap y OSRM públicos (sin SLA, no aptos para operar).", FLAG, True)
add("map_price", "Stadia Maps Starter (teselas + rutas)", "US$ al mes", 20, "VERIFICADO", "[F12] Uso comercial permitido.", USD)
add("map_incl", "Stadia Starter: créditos incluidos", "créditos al mes", 1000000, "VERIFICADO", "[F12]", INT)
add("map_over", "Stadia Starter: créditos excedentes", "US$ por 1.000 créditos", 0.03, "VERIFICADO", "[F12]", USD)
add("tiles_view", "Teselas por vista de mapa (familia)", "teselas", 30, "SUPUESTO", "1 crédito por tesela [F12].", INT)
add("views", "Vistas de mapa por recolector activo al mes", "vistas", 4, "SUPUESTO", "Seguimiento del rider.", DEC1)
add("tiles_rider", "Teselas por EcoRider por día de trabajo", "teselas", 600, "SUPUESTO", "Navegación durante el turno.", INT)
add("tiles_center", "Teselas por centro de acopio por día", "teselas", 200, "SUPUESTO", "Mapa del panel.", INT)
add("routes_pick", "Cálculos de ruta por recojo", "rutas", 6, "SUPUESTO", "Ruta inicial y recálculos.", DEC1)
add("cred_route", "Créditos por cálculo de ruta", "créditos", 20, "VERIFICADO", "[F12] Ruteo estándar.", INT)
add("resend_pro", "Correo transaccional en Resend Pro (1/0)", "indicador", [0, 0, 1], "SUPUESTO",
    "El SMTP por defecto de Supabase no sirve en producción [F4]. Free: 3.000/mes y 100/día.", FLAG)
add("resend_price", "Resend Pro", "US$ al mes", 20, "VERIFICADO", "[F5] 50.000 correos al mes.", USD)
add("dom_com_n", "Dominios .com", "dominios", [1, 1, 1], "SUPUESTO", "Aún no existe; recomendable desde la demo.", INT)
add("dom_com", "Dominio .com (registro o renovación)", "US$ al año", 11.17, "REFERENCIA",
    "[F20] Tarifa mayorista Verisign desde 01-nov-2026 (US$ 10,97) + ICANN (US$ 0,20); Cloudflare vende al costo.", USD)
add("dom_pe_n", "Dominios .pe", "dominios", [0, 1, 1], "SUPUESTO", "Marca local para el piloto.", INT)
add("dom_pe", "Dominio .pe (renovación)", "S/ al año", 150, "REFERENCIA",
    "[F21] Terceros: S/ 110 el primer año en promoción; renovación S/ 120–150. Se toma el tope.", SOL)
add("cl_n", "Claude Pro (Claude Code)", "suscripciones", [1, 1, 1], "SUPUESTO", "Plan real del equipo desconocido.", INT)
add("cl_price", "Claude Pro, pago mensual", "US$ al mes", 20, "VERIFICADO", "[F7] US$ 17 con pago anual; Max US$ 100.", USD)
add("gpt_n", "ChatGPT Plus (Codex CLI)", "suscripciones", [1, 1, 1], "SUPUESTO", "Plan real del equipo desconocido.", INT)
add("gpt_price", "ChatGPT Plus", "US$ al mes", 20, "REFERENCIA", "[F22] openai.com no permitió la consulta.", USD)
add("stitch", "Google Stitch", "US$ al mes", 0, "REFERENCIA", "[F23] Gratis mientras siga en Google Labs.", USD)

ssec("7. Costos de única vez")
add("play", "Cuenta de desarrollador de Google Play", "US$ (pago único)", 25, "VERIFICADO", "[F19]", USD)
add("play_n", "Cuentas de Google Play", "cuentas", [1, 1, 1], "SUPUESTO", "Para publicar la app Android.", INT)
add("rate_h", "Costo de una hora de desarrollo (configuración)", "S/ por hora", 60, "SUPUESTO",
    "Referencial para valorizar la configuración; cámbielo por el costo real del equipo.", SOL, True)
add("h_prod", "Horas: replicar esquema, funciones y secretos a PRODUCCIÓN", "horas", [0, 8, 8], "SUPUESTO",
    "Producción hoy está vacía.", INT)
add("h_push", "Horas: implementar avisos push (FCM + Web Push)", "horas", [0, 24, 24], "SUPUESTO", "Pendiente en el código.", INT)
add("h_maps", "Horas: pasar teselas y rutas a un proveedor de pago", "horas", [0, 8, 8], "SUPUESTO", "Android, PWA y panel.", INT)
add("h_smtp", "Horas: SMTP propio y dominio de correo", "horas", [0, 4, 4], "SUPUESTO", "", INT)
add("legal", "Revisión legal de protección de datos (Ley 29733)", "S/ (pago único)", 2000, "SUPUESTO",
    "Consentimientos y bancos de datos (docs/panel-duenos-analisis.md). Cotizar con un abogado.", SOL)
add("legal_n", "Revisiones legales", "revisiones", [0, 1, 1], "SUPUESTO", "", INT)
add("const", "Constitución de la empresa (SAC por SID-SUNARP + notaría)", "S/ (pago único)", 900, "REFERENCIA",
    "[F34] Guías 2026: S/ 840–900 por la vía digital; tasas oficiales en sunarp.gob.pe.", SOL)
add("const_n", "Constituciones", "empresas", [0, 1, 1], "SUPUESTO", "Para operar con dinero real hace falta RUC de empresa.", INT)
add("mkt_launch", "Marketing de lanzamiento", "S/ (pago único)", [0, 3000, 10000], "SUPUESTO",
    "Campaña de inscripción de recolectores en el distrito.", SOL)

ssec("8. Personas: parámetros legales (régimen laboral privado)")
add("rmv", "Remuneración mínima vital (RMV)", "S/ al mes", 1230, "VERIFICADO",
    "[F24] D.S. 015-2026-TR, vigente desde el 01-oct-2026. Segundo tramo: S/ 1.300 en el primer semestre de 2027.", SOL, True)
add("uit", "Unidad impositiva tributaria (UIT) 2026", "S/", 5500, "VERIFICADO", "[F25] D.S. 301-2025-EF.", SOL0)
add("essalud", "EsSalud (aporte del empleador)", "% del sueldo", 0.09, "VERIFICADO",
    "[F26] 9 %. No se aplica a las gratificaciones: en su lugar se paga la bonificación extraordinaria.", PCT)
add("bonif", "Bonificación extraordinaria sobre la gratificación (Ley 30334)", "% de la gratificación", 0.09, "VERIFICADO",
    "[F26] Igual al aporte de EsSalud que no se cobra sobre la gratificación.", PCT)
add("grati_gen", "Gratificaciones al año — régimen general", "sueldos al año", 2, "VERIFICADO",
    "[F26] Julio y diciembre, un sueldo cada una.", DEC1)
add("grati_peq", "Gratificaciones al año — pequeña empresa (REMYPE)", "sueldos al año", 1, "VERIFICADO",
    "[F28] Media gratificación en julio y media en diciembre. Microempresa: no corresponde.", DEC1)
add("cts_gen", "CTS — régimen general", "sueldos al año", "=1+" + "1/6", "VERIFICADO",
    "[F27] Un sueldo computable al año (sueldo + 1/6 de la gratificación), depositado en mayo y noviembre.", DEC3)
add("cts_peq", "CTS — pequeña empresa (REMYPE)", "días de sueldo al año", 15, "VERIFICADO",
    "[F28] 15 remuneraciones diarias por año (tope 90). Microempresa: no corresponde.", INT)
add("vac_gen", "Vacaciones — régimen general", "días al año", 30, "VERIFICADO", "[F28] Se provisionan como costo del año.", INT)
add("vac_mype", "Vacaciones — micro y pequeña empresa", "días al año", 15, "VERIFICADO", "[F28]", INT)
add("sis_micro", "SIS de la microempresa: aporte del empleador", "S/ por trabajador al mes", 15, "SUPUESTO",
    "Microempresa afilia al SIS semicontributivo [F28]; el monto del aporte no se verificó en fuente oficial.", SOL)
add("vida_ley", "Seguro Vida Ley (trabajadores en planilla)", "% del sueldo", 0.005, "SUPUESTO",
    "Obligatorio desde el inicio de la relación laboral; prima a cotizar con una aseguradora.", PCT2)
add("ret4", "Retención de renta de cuarta categoría (recibos por honorarios)", "% del recibo", 0.08, "REFERENCIA",
    "[F29] Se retiene en recibos de más de S/ 1.500; es impuesto del trabajador, no costo de la empresa.", PCT)
add("ret4_tope", "Tope anual para suspender retenciones de cuarta (2026)", "S/ al año", 48125, "REFERENCIA",
    "[F29] 8,75 UIT. Quien proyecta ganar menos lo pide con el Formulario 1609.", SOL0)
add("regimen", "Régimen laboral del equipo: 1 general, 2 pequeña empresa, 3 microempresa, 4 recibos por honorarios",
    "código", [4, 3, 2], "SUPUESTO",
    "MVP: equipo fundador por honorarios (o sin sueldo). Piloto: microempresa en REMYPE. Ciudad: pequeña empresa. "
    "Sin inscripción en REMYPE rige el régimen general.", FLAG, True)

ssec("9. Personas: equipo fijo (sueldos brutos de mercado, SUPUESTO)")
ROLES = [
    ("gg", "Fundador / gerente general", "Gasto administrativo", 4000, [0, 1, 1],
     "Sueldo de mercado supuesto; hoy los fundadores no cobran."),
    ("dev", "Desarrollador full stack (Android, PWA, backend)", "MOI (CIF)", 5000, [0, 1, 2],
     "[F33] Referencias: Indeed S/ 2.536 (promedio de avisos), Coderhouse ~S/ 5.000 junior."),
    ("ops", "Coordinador de operaciones (riders y acopios)", "MOI (CIF)", 2500, [0, 1, 2], "Supuesto."),
    ("sop", "Soporte / call center", "MOI (CIF)", 1500, [0, 0.5, 2],
     "[F33] Indeed: S/ 1.500–1.900 en Lima. 0,5 = medio tiempo."),
    ("com", "Ejecutivo comercial (municipalidades y marcas)", "Gasto de ventas", 2500, [0, 0, 1], "Supuesto."),
    ("aux", "Auxiliar de acopio (recepción y pesaje)", "MOD", "=RMV", [0, 0, 0],
     "Hoy pesa el acopio aliado; poner personas si Ruma asume el pesaje."),
]
for k, name, cls, sal, n, nota in ROLES:
    add(f"sal_{k}", f"Sueldo bruto: {name}", "S/ al mes", f"={sup('rmv')}" if sal == "=RMV" else sal, "SUPUESTO", nota, SOL)
    add(f"n_{k}", f"Personas: {name}", "personas", n, "SUPUESTO", cls, DEC1)
ssub("EcoRiders en planilla (alternativa)")
add("rider_sal", "Sueldo bruto de un EcoRider en planilla", "S/ al mes", 1500, "SUPUESTO",
    "Debe ser ≥ RMV. Jornada completa.", SOL)
add("rider_mov", "Asignación de movilidad (combustible y moto propia)", "S/ por rider al mes", 300, "SUPUESTO",
    "En la modalidad por recojo la tarifa ya cubre el vehículo.", SOL)
add("sctr", "SCTR (salud + pensión) para riders en planilla", "% del sueldo", 0.015, "SUPUESTO",
    "Solo si la actividad está en el Anexo 5 del D.S. 009-97-SA; la tasa la fija la aseguradora: cotizar.", PCT2)
add("seg_ind", "Seguro de accidentes personales para riders independientes", "S/ por rider al mes", 25, "SUPUESTO",
    "Beneficio voluntario; cotizar.", SOL)
add("seg_ind_on", "¿Ruma paga el seguro de los riders independientes? (1/0)", "indicador", [0, 1, 1], "SUPUESTO", "", FLAG)

ssec("10. Materiales indirectos, equipos y otros costos indirectos")
add("bolsa", "Bolsa o costal por recojo", "S/ por recojo", 0.50, "SUPUESTO",
    "No se halló precio unitario de saco de rafia en tiendas peruanas; cotizar con un distribuidor de empaques.", SOL)
add("precinto", "Etiqueta / precinto por recojo", "S/ por recojo", 0.10, "SUPUESTO", "", SOL)
add("chaleco", "Chaleco reflectivo con cinta", "S/ por unidad", 59.90, "REFERENCIA", "[F32] Precio de tienda (Falabella/Sodimac Perú).", SOL)
add("chaleco_vida", "Vida útil del chaleco", "meses", 6, "SUPUESTO", "", INT)
add("uniforme", "Polo y casaca con logo", "S/ por juego", 80, "SUPUESTO", "", SOL)
add("uniforme_vida", "Vida útil del uniforme", "meses", 12, "SUPUESTO", "", INT)
add("epp", "Guantes y otros EPP", "S/ por rider al mes", 15, "SUPUESTO", "", SOL)
add("impresos", "Volantes con instrucciones de separación", "S/ por recolector activo al mes", 0.30, "SUPUESTO", "", SOL)
add("laptop", "Laptop de desarrollo", "S/ por unidad", 3500, "SUPUESTO", "", SOL)
add("laptop_n", "Laptops que compra la empresa", "unidades", [0, 2, 4], "SUPUESTO", "MVP: equipos personales del equipo.", INT)
add("phone", "Teléfono Android de prueba", "S/ por unidad", 900, "SUPUESTO", "", SOL)
add("phone_n", "Teléfonos de prueba", "unidades", [0, 2, 3], "SUPUESTO", "", INT)
add("balanza", "Balanza de plataforma 300 kg", "S/ por unidad", 239, "REFERENCIA", "[F31] Ventus B-300A en Sodimac Perú.", SOL)
add("balanza_n", "Balanzas propias (si el acopio aliado no tiene)", "unidades", [0, 1, 3], "SUPUESTO", "", INT)
add("dep_pc", "Depreciación: equipos de procesamiento de datos", "% anual", 0.25, "VERIFICADO",
    "[F30] Tasa máxima del art. 22 del Reglamento de la Ley del Impuesto a la Renta. Teléfonos: clasificación SUPUESTA.", PCT)
add("dep_maq", "Depreciación: maquinaria y equipo (balanza)", "% anual", 0.10, "VERIFICADO", "[F30]", PCT)
add("linea", "Internet y telefonía móvil", "S/ por línea al mes", 50, "SUPUESTO", "", SOL)
add("lineas_n", "Líneas pagadas por la empresa", "líneas", [0, 3, 8], "SUPUESTO", "", INT)
add("cowork", "Coworking", "S/ por puesto al mes", 350, "SUPUESTO", "", SOL)
add("cowork_n", "Puestos de coworking", "puestos", [0, 0, 4], "SUPUESTO", "Piloto: trabajo remoto.", INT)

ssec("11. Gastos administrativos y de ventas")
add("contador", "Contador externo", "S/ al mes", [0, 400, 800], "SUPUESTO", "", SOL)
add("bancos", "Gastos bancarios y otros administrativos", "S/ al mes", [0, 50, 200], "SUPUESTO", "", SOL)
add("mkt", "Marketing continuo (redes, activaciones)", "S/ al mes", [0, 1000, 5000], "SUPUESTO", "", SOL)
add("com_ventas", "Comisión comercial sobre convenios y estudios", "% del ingreso", 0.05, "SUPUESTO", "", PCT)

ssec("12. Ingresos (cada fuente se enciende con 1 y se apaga con 0)")
add("on_mat", "Venta del material al acopio (1/0)", "indicador", [0, 1, 1], "SUPUESTO",
    "Al precio por kg del proyecto. MVP: sin operación real.", FLAG, True)
add("on_muni", "Convenios con municipalidades (1/0)", "indicador", [0, 1, 1], "SUPUESTO", "Hipótesis comercial.", FLAG, True)
add("muni_fee", "Convenio municipal: tarifa fija", "S/ por distrito al mes", 1500, "SUPUESTO",
    "Sin referencia pública; validar con municipalidades.", SOL, True)
add("muni_n", "Distritos con convenio", "distritos", [0, 1, 3], "SUPUESTO", "", INT)
add("muni_t", "Convenio municipal: pago por tonelada desviada del relleno", "S/ por tonelada", 0, "SUPUESTO",
    "Alternativa a la tarifa fija; 0 = no se usa.", SOL)
add("on_est", "Estudios de mercado de marcas y envases (1/0)", "indicador", [0, 0, 1], "SUPUESTO",
    "Solo agregados y anonimizados. Hoy no hay consentimiento (Ley 29733) ni volumen [F36].", FLAG, True)
add("est_price", "Estudio de mercado: suscripción", "S/ por cliente al mes", 2500, "SUPUESTO", "", SOL)
add("est_n", "Clientes de estudios de mercado", "clientes", [0, 0, 2], "SUPUESTO", "", INT)
add("on_rep", "Aportes por responsabilidad extendida del productor (REP) (1/0)", "indicador", [0, 0, 0], "SUPUESTO",
    "Solo hipótesis: falta verificar la norma de residuos vigente.", FLAG)
add("rep_t", "Aporte REP", "S/ por tonelada", 100, "SUPUESTO", "Hipótesis sin fuente.", SOL)

ssec("13. Punto de equilibrio, palancas e inversión")
add("kg_pal", "Caso «con palancas»: kg por recojo", "kg", 8, "SUPUESTO", "Recojo de varias semanas o de edificios.", DEC1, True)
add("paradas_pal", "Caso «con palancas»: recojos por viaje", "recojos", 3, "SUPUESTO", "Rutas con varias paradas.", DEC1, True)
add("sens_esc", "Escenario de la tabla de sensibilidad y del gráfico (1 MVP, 2 Piloto, 3 Ciudad)", "código", 3,
    "SUPUESTO", "", FLAG)
add("sens_kg0", "Sensibilidad: primer valor de kg por recojo", "kg", 2, "SUPUESTO", "", DEC1)
add("sens_kgd", "Sensibilidad: paso de kg", "kg", 2, "SUPUESTO", "", DEC1)
add("sens_p0", "Sensibilidad: primer valor del pago al rider por recojo", "S/", 1.0, "SUPUESTO", "", SOL)
add("sens_pd", "Sensibilidad: paso del pago al rider", "S/", 0.5, "SUPUESTO", "", SOL)
add("inversion", "Inversión que se busca", "S/", 100000, "SUPUESTO", "Para calcular los meses de pista.", SOL0, True)

ssec("14. Proyección a 60 meses (hoja «Proyección»)")
ssub("Común a ambos casos: rampa de recolectores y equipo por etapas")
add("pr_act0", "Recolectores activos en el mes 1", "personas", f"={sup('reg', 1)}*{sup('act_pct', 1)}", "FÓRMULA",
    "Igual al escenario Piloto.", INT)
add("pr_city", "Recolectores activos de la etapa Ciudad", "personas", f"={sup('reg', 2)}*{sup('act_pct', 2)}", "FÓRMULA",
    "Igual al escenario Ciudad.", INT)
add("pr_cap", "Tope de recolectores activos de la rampa", "personas", 10000, "SUPUESTO",
    "Más allá de la Ciudad del escenario: varios distritos de Lima.", INT, True)
add("pr_g", "Crecimiento mensual de recolectores activos", "% mensual", 0.10, "SUPUESTO", "", PCT, True)
add("pr_m2", "Mes en que se pasa a la estructura de Ciudad (equipo, infraestructura y gastos)", "mes", 19, "SUPUESTO",
    "Equipo por etapas: hasta ese mes, el del Piloto.", INT, True)
add("pr_extra", "Costo fijo adicional por cada 1.000 activos sobre la etapa Ciudad", "S/ al mes", 1500, "SUPUESTO",
    "Soporte y operaciones que crecen con la base.", SOL)
add("pr_capd", "Recojos por rider por día con 1 recojo por viaje", "recojos", 8, "SUPUESTO", "Escenario Ciudad ≈ 7,7.", DEC1)
add("pr_capx", "Recojos adicionales por rider por día por cada recojo extra en el viaje", "recojos", 4, "SUPUESTO", "", DEC1)
add("pr_rmin", "EcoRiders mínimos", "personas", 2, "SUPUESTO", "", INT)
ssub("Caso base: la economía por recojo de hoy; convenios y estudios como en los escenarios Piloto y Ciudad")
add("b_kg", "Caso base: kg por recojo", "kg", f"={sup('kg')}", "FÓRMULA", "Igual a §3.", DEC1)
add("b_par", "Caso base: recojos por viaje", "recojos", f"={sup('paradas')}", "FÓRMULA", "Igual a §3.", DEC1)
ssub("Plan con palancas: rampa (cada valor es un SUPUESTO)")
add("pl_kg0", "Plan: kg por recojo al inicio", "kg", f"={sup('kg')}", "FÓRMULA", "", DEC1)
add("pl_kgF", "Plan: kg por recojo al final de la rampa", "kg", f"={sup('kg_pal')}", "FÓRMULA",
    "Igual al caso «con palancas» de §13 (recojos agrupados por hogar o edificio).", DEC1)
add("pl_kgN", "Plan: meses para llegar a los kg finales", "meses", 18, "SUPUESTO", "", INT, True)
add("pl_par0", "Plan: recojos por viaje al inicio", "recojos", f"={sup('paradas')}", "FÓRMULA", "", DEC1)
add("pl_parF", "Plan: recojos por viaje al final", "recojos", f"={sup('paradas_pal')}", "FÓRMULA", "Igual a §13.", DEC1)
add("pl_parN", "Plan: meses para llegar a los recojos por viaje finales", "meses", 12, "SUPUESTO", "", INT, True)
add("pl_conv0", "Plan: convenios municipales en el mes 1", "convenios", 1, "SUPUESTO", "", INT)
add("pl_conv_cada", "Plan: se firma un convenio más cada", "meses", 4, "SUPUESTO", "", INT, True)
add("pl_conv_max", "Plan: máximo de convenios", "convenios", 8, "SUPUESTO", "", INT)
add("pl_est_mes", "Plan: mes desde el que se venden estudios de mercado", "mes", 24, "SUPUESTO",
    "Solo con consentimiento implementado y volumen suficiente (Ley 29733).", INT, True)
add("pl_est_umb", "Plan: recolectores activos mínimos para vender estudios", "personas", 1000, "SUPUESTO", "", INT)
add("pl_est_cada", "Plan: se suma un cliente de estudios cada", "meses", 6, "SUPUESTO", "", INT)
add("pl_est_max", "Plan: máximo de clientes de estudios", "clientes", 4, "SUPUESTO", "", INT)
ssub("Impuesto a la renta de tercera categoría")
add("ir_reg", "Régimen tributario: 1 = Régimen MYPE Tributario, 2 = Régimen General", "código", 1, "SUPUESTO",
    "RMT: ingresos anuales hasta 1.700 UIT [F37].", FLAG)
add("ir_rmt", "RMT: tasa sobre la renta neta hasta el tramo", "%", 0.10, "POR VERIFICAR",
    "[F37] Valor conocido del D. Leg. 1269; sunat.gob.pe no pudo consultarse el 2026-11-06.", PCT)
add("ir_tramo", "RMT: tramo con tasa reducida", "UIT", 15, "POR VERIFICAR", "[F37]", INT)
add("ir_gen", "Tasa general (y exceso del tramo en el RMT)", "%", 0.295, "POR VERIFICAR", "[F38] Art. 55 de la Ley del Impuesto a la Renta.", PCT)
add("ir_sis", "Arrastre de pérdidas: 1 = sistema A (100 %, 4 años), 2 = sistema B (50 % de la renta, sin plazo)", "código", 1,
    "POR VERIFICAR", "[F39] Art. 50 de la LIR. En 60 meses las pérdidas del año 1 se usan hasta el año 5, dentro del plazo del sistema A.", FLAG)
add("ir_bpct", "Sistema B: parte de la renta neta que se puede compensar", "%", 0.5, "POR VERIFICAR", "[F39]", PCT)
add("div_ret", "Retención sobre dividendos a personas naturales", "%", 0.05, "POR VERIFICAR", "[F40] Art. 52-A de la LIR.", PCT)
ssub("Evaluación financiera y términos hipotéticos para el inversionista")
add("tasa", "Tasa de descuento anual", "%", 0.25, "SUPUESTO",
    "Refleja el riesgo de una empresa en etapa temprana y sin ventas probadas; inversionistas de riesgo suelen exigir tasas mayores. Ajústela.",
    PCT, True)
add("tasa_m", "Tasa de descuento mensual equivalente", "%", f"=(1+{sup('tasa')})^(1/12)-1", "FÓRMULA", "", PCT2)
add("mult", "Valor terminal: múltiplo del EBITDA del año 5", "veces", 5, "SUPUESTO",
    "Método simple; 0 si el EBITDA del año 5 es negativo.", DEC1, True)
add("inv_pct", "Participación que recibe el inversionista", "% de la empresa", 0.20, "SUPUESTO", "Término hipotético de negociación.", PCT, True)
add("payout", "Utilidad que se reparte como dividendo (desde que hay utilidades acumuladas)", "%", 0.5, "SUPUESTO",
    "Término hipotético; la ley solo permite repartir utilidades realizadas.", PCT)
add("pk0", "Sensibilidad del payback: primer valor de kg finales", "kg", 4, "SUPUESTO", "", DEC1)
add("pkd", "Sensibilidad del payback: paso de kg", "kg", 1, "SUPUESTO", "", DEC1)
add("pp0", "Sensibilidad del payback: primer pago final al rider por recojo", "S/", 1.0, "SUPUESTO", "", SOL)
add("ppd", "Sensibilidad del payback: paso del pago", "S/", 0.5, "SUPUESTO", "", SOL)
widths(ws_sup, {"A": 56, "B": 22, "C": 16, "D": 16, "E": 16, "F": 16, "G": 80})
ws_sup.freeze_panes = "C6"

# ============================================================ Escenarios (volúmenes)
E = {}
title(ws_esc, "Escenarios — volúmenes y costos",
      "Volúmenes derivados de «Supuestos»; costos e ingresos traídos de las hojas de detalle. No se ingresa nada aquí.")
header(ws_esc, 4, ["Concepto", "Unidad", SC[0], SC[1], SC[2], "Cómo se calcula"])
erow = [5]


def esec(text):
    section(ws_esc, erow[0], text, 6)
    erow[0] += 1


def eadd(key, label, unit, fn, fmt, how, font=f_base, bold=False):
    r = erow[0]
    put(ws_esc, f"A{r}", label, f_bold if bold else f_base, align=wrap)
    put(ws_esc, f"B{r}", unit)
    for i in range(3):
        put(ws_esc, f"{SUPC[i]}{r}", fn(i), f_bold if bold else font, fmt=fmt, border=b_all,
            fill=fill_tot if bold else None)
    put(ws_esc, f"F{r}", how, f_sub, align=wrap)
    E[key] = r
    erow[0] += 1


def esc(key, i):
    return f"Escenarios!{SUPC[i]}${E[key]}"


def loc(key, i):
    return f"{SUPC[i]}{E[key]}"


esec("Definición")
for i, txt in enumerate([
    "Lo de hoy: demo para inversores en STAGING; IA y hosting en niveles gratis; pagos de prueba; el equipo no cobra.",
    "Un distrito: 300 recolectores, 2 EcoRiders, 1 acopio; producción en Supabase Pro, Vercel Pro y mapas de pago; "
    "equipo mínimo en microempresa.",
    "Varios distritos: 5.000 recolectores, 25 EcoRiders, 3 acopios; equipo completo en pequeña empresa.",
]):
    put(ws_esc, f"{SUPC[i]}{erow[0]}", txt, align=wrap, border=b_all)
put(ws_esc, f"A{erow[0]}", "Descripción", f_bold)
ws_esc.row_dimensions[erow[0]].height = 75
erow[0] += 1

esec("Volúmenes mensuales")
eadd("reg", "Recolectores registrados", "personas", lambda i: f"={sup('reg', i)}", INT, "Supuestos", f_link)
eadd("act", "Recolectores activos en el mes", "personas", lambda i: f"={sup('reg', i)}*{sup('act_pct', i)}", INT, "registrados × % activos")
eadd("dau", "Recolectores activos en un día (DAU)", "personas", lambda i: f"={sup('reg', i)}*{sup('dau_pct', i)}", INT, "registrados × % DAU")
eadd("riders", "EcoRiders", "personas", lambda i: f"={sup('riders', i)}", INT, "Supuestos", f_link)
eadd("acopios", "Centros de acopio", "centros", lambda i: f"={sup('acopios', i)}", INT, "Supuestos", f_link)
eadd("scans", "Escaneos", "escaneos", lambda i: f"={loc('act', i)}*{sup('scans', i)}", INT, "activos × escaneos por activo")
eadd("photos", "Fotos analizadas por la IA", "fotos", lambda i: f"={loc('scans', i)}*{sup('photos', i)}", INT, "escaneos × fotos por escaneo")
eadd("pick", "Recojos completados", "recojos", lambda i: f"={loc('act', i)}*{sup('pick', i)}", INT, "activos × recojos por activo")
eadd("pick_day", "Recojos por EcoRider por día", "recojos", lambda i: f"=IF({loc('riders', i)}*{sup('dias')}=0,0,{loc('pick', i)}/({loc('riders', i)}*{sup('dias')}))", DEC1,
     "recojos ÷ (riders × días operativos): control de capacidad")
eadd("kg", "Material recolectado", "kg", lambda i: f"={loc('pick', i)}*{sup('kg')}", INT, "recojos × kg por recojo")
eadd("chat", "Mensajes al asistente", "mensajes", lambda i: f"={loc('act', i)}*{sup('chat', i)}", INT, "activos × mensajes")
eadd("egress", "Egress estimado de Supabase", "GB", lambda i: f"={loc('dau', i)}*{sup('sb_mb_dau')}/1024", DEC1, "DAU × MB por DAU ÷ 1.024")
eadd("inv", "Invocaciones de Edge Functions", "invocaciones",
     lambda i: f"={loc('photos', i)}+{loc('scans', i)}+{loc('chat', i)}", INT, "fotos (preview) + escaneos (commit) + chat")
eadd("credits", "Créditos de mapas y rutas", "créditos",
     lambda i: (f"={loc('act', i)}*{sup('views')}*{sup('tiles_view')}+{loc('riders', i)}*{sup('dias')}*{sup('tiles_rider')}"
                f"+{loc('acopios', i)}*{sup('dias')}*{sup('tiles_center')}+{loc('pick', i)}*{sup('routes_pick')}*{sup('cred_route')}"),
     INT, "teselas de familias + riders + panel + rutas × créditos")
eadd("points", "Puntos emitidos a recolectores", "puntos",
     lambda i: f"={loc('kg', i)}*{sup('p_avg')}*{sup('share')}*{sup('pps')}", INT, "kg × precio ponderado × 40 % × 1.000")

esec("Controles de capacidad")
eadd("fit_free", "¿El egress cabe en el plan incluido?", "", lambda i: f'=IF({loc("egress", i)}<={sup("sb_egress_incl", i)},"Sí","No: se paga excedente")', None,
     "egress estimado ≤ egress incluido")
eadd("fit_ia", "¿Alcanza la IA gratis? (techo medido ≈ 3 escáneres simultáneos)", "",
     lambda i: f'=IF({sup("ia_pay", i)}=1,"Con facturación activa","Solo para demo")', None, "[P3] prueba de carga del 2026-11-01")
eadd("fit_map", "¿Los créditos de mapas caben en el plan?", "",
     lambda i: f'=IF({sup("map_paid", i)}=0,"OSM público: sin SLA",IF({loc("credits", i)}<={sup("map_incl")},"Sí","No: se paga excedente"))', None,
     "créditos ≤ incluidos en Stadia Starter")
esc_cost_start = erow[0]

# ============================================================ Infraestructura
title(ws_inf, "Infraestructura tecnológica y herramientas — costo fijo mensual",
      "Precio unitario en US$ (el dominio .pe se convierte con el tipo de cambio). Cantidades y precios vienen de «Supuestos». "
      "En el estado de costos es un costo indirecto del servicio (CIF).")
group_header(ws_inf, 4, [("E", "Cantidad al mes"), ("H", "Costo mensual (S/)"), ("K", "Costo mensual (US$)")])
header(ws_inf, 5, ["Servicio", "Plan / detalle", "Unidad de cobro", "Precio unitario (US$)",
                   *SC, *SC, *SC, "Estado", "Fuente", "Verificado el"])
QC, SOLC, USDC = ["E", "F", "G"], ["H", "I", "J"], ["K", "L", "M"]
irow = [6]
inf_rows, inf_foreign = [], []


def isec(text):
    section(ws_inf, irow[0], text, 16)
    irow[0] += 1


def iadd(svc, plan, unit, price, qty, estado, fuente, fecha=HOY, foreign=True, pfmt=USD):
    r = irow[0]
    put(ws_inf, f"A{r}", svc, align=wrap)
    put(ws_inf, f"B{r}", plan, align=wrap)
    put(ws_inf, f"C{r}", unit, align=wrap)
    put(ws_inf, f"D{r}", price, f_link, fmt=pfmt, border=b_all)
    for i in range(3):
        put(ws_inf, f"{QC[i]}{r}", qty(i), f_link, fmt=DEC2 if "GB" in unit or "millón" in unit or "1.000" in unit else INT, border=b_all)
        put(ws_inf, f"{USDC[i]}{r}", f"=$D{r}*{QC[i]}{r}", fmt=USD, border=b_all)
        put(ws_inf, f"{SOLC[i]}{r}", f"={USDC[i]}{r}*{sup('tc')}", fmt=SOL, border=b_all)
    put(ws_inf, f"N{r}", estado)
    put(ws_inf, f"O{r}", fuente, align=wrap)
    put(ws_inf, f"P{r}", fecha)
    inf_rows.append(r)
    if foreign:
        inf_foreign.append(r)
    irow[0] += 1
    return r


isec("Base de datos y backend — Supabase (2 proyectos: staging y producción)")
iadd("Supabase — plan de la organización", "Pro (Free en el MVP)", "organización", f"={sup('sb_pro_price')}",
     lambda i: f"={sup('sb_pro', i)}", "VERIFICADO", "[F3] https://supabase.com/pricing")
iadd("Supabase — cómputo Micro", "1 GB RAM compartido", "proyecto", f"={sup('sb_micro_price')}",
     lambda i: f"={sup('sb_micro_n', i)}", "VERIFICADO", "[F3] https://supabase.com/pricing")
iadd("Supabase — cómputo Small", "2 GB RAM", "proyecto", f"={sup('sb_small_price')}",
     lambda i: f"={sup('sb_small_n', i)}", "VERIFICADO", "[F3] https://supabase.com/pricing")
iadd("Supabase — crédito de cómputo", "Incluido en Pro (resta)", "crédito", f"=-{sup('sb_credit')}",
     lambda i: f"={sup('sb_pro', i)}", "VERIFICADO", "[F3] https://supabase.com/pricing")
iadd("Supabase — egress excedente", "Sobre lo incluido en el plan", "GB", f"={sup('sb_egress_over')}",
     lambda i: f"=MAX(0,{esc('egress', i)}-{sup('sb_egress_incl', i)})", "VERIFICADO", "[F3] https://supabase.com/pricing")
iadd("Supabase — invocaciones excedentes", "detect-material y maki-assistant", "millón de invocaciones", f"={sup('sb_inv_over')}",
     lambda i: f"=MAX(0,{esc('inv', i)}-{sup('sb_inv_incl', i)})/1000000", "VERIFICADO", "[F3] https://supabase.com/pricing")
isec("Hosting web — PWA (iPhone) y panel del acopio (Next.js)")
iadd("Vercel", "Pro por asiento (Hobby gratis en el MVP: no comercial)", "asiento", f"={sup('vc_price')}",
     lambda i: f"={sup('vc_seats', i)}", "VERIFICADO", "[F9] https://vercel.com/pricing")
isec("Mapas y rutas")
iadd("OpenStreetMap + OSRM públicos", "tile.openstreetmap.org y routing.openstreetmap.de", "servicio", 0,
     lambda i: f"=1-{sup('map_paid', i)}", "VERIFICADO", "[F10] [F11] Gratis con política de uso; sin SLA")
iadd("Stadia Maps — plan", "Starter: teselas + ruteo, uso comercial", "plan", f"={sup('map_price')}",
     lambda i: f"={sup('map_paid', i)}", "VERIFICADO", "[F12] https://stadiamaps.com/pricing")
iadd("Stadia Maps — créditos excedentes", "Sobre 1 millón de créditos", "1.000 créditos", f"={sup('map_over')}",
     lambda i: f"={sup('map_paid', i)}*MAX(0,{esc('credits', i)}-{sup('map_incl')})/1000", "VERIFICADO", "[F12] https://stadiamaps.com/pricing")
isec("Avisos y correo")
iadd("Firebase Cloud Messaging", "Push Android (pendiente de implementar)", "servicio", 0, lambda i: "=1",
     "VERIFICADO", "[F15] https://firebase.google.com/pricing")
iadd("Web Push (VAPID)", "Push de la PWA (pendiente de implementar)", "servicio", 0, lambda i: "=1",
     "SUPUESTO", "[F16] El protocolo no cobra por mensaje")
iadd("Resend", "Correo transaccional: Free (MVP, piloto) / Pro (ciudad)", "plan", f"={sup('resend_price')}",
     lambda i: f"={sup('resend_pro', i)}", "VERIFICADO", "[F5] https://resend.com/pricing")
isec("Dominio (anual, prorrateado por mes)")
iadd("Dominio .com", "Registrador al costo (p. ej. Cloudflare)", "dominio / mes", f"={sup('dom_com')}/12",
     lambda i: f"={sup('dom_com_n', i)}", "REFERENCIA", "[F20] https://www.cloudflare.com/products/registrar/")
iadd("Dominio .pe", "Renovación anual en soles (local, sin IGV adicional)", "dominio / mes", f"={sup('dom_pe')}/{sup('tc')}/12",
     lambda i: f"={sup('dom_pe_n', i)}", "REFERENCIA", "[F21] https://kom.pe/cuanto-cuesta-un-dominio-pe-en-2026-y-donde-comprarlo/",
     foreign=False)
isec("Herramientas de desarrollo y diseño (planes reales: supuesto)")
iadd("Claude Code", "Claude Pro, pago mensual", "suscripción", f"={sup('cl_price')}",
     lambda i: f"={sup('cl_n', i)}", "VERIFICADO (plan: SUPUESTO)", "[F7] https://claude.com/pricing")
iadd("Codex CLI", "ChatGPT Plus", "suscripción", f"={sup('gpt_price')}",
     lambda i: f"={sup('gpt_n', i)}", "REFERENCIA (plan: SUPUESTO)", "[F22] openai.com/chatgpt/pricing (no accesible)")
iadd("Google Stitch", "Google Labs (gratis)", "servicio", f"={sup('stitch')}", lambda i: "=1",
     "REFERENCIA", "[F23] https://stitch.withgoogle.com")
iadd("App en iPhone", "PWA: no requiere Apple Developer Program", "servicio", 0, lambda i: "=1",
     "DEL PROYECTO", "[P6] CLAUDE.md, sección PWA")
r = irow[0]
put(ws_inf, f"A{r}", "Subtotal antes de IGV", f_bold)
for cols in (SOLC, USDC):
    for c in cols:
        put(ws_inf, f"{c}{r}", "=" + "+".join(f"{c}{x}" for x in inf_rows), f_bold, fmt=SOL if cols is SOLC else USD, border=b_all)
inf_sub = r
r += 1
put(ws_inf, f"A{r}", "IGV sobre servicios digitales del exterior", align=wrap)
put(ws_inf, f"B{r}", "18 % × indicador de «Supuestos» (no aplica al .pe)", align=wrap)
put(ws_inf, f"N{r}", "VERIFICADO (tasa)")
put(ws_inf, f"O{r}", "[F2]")
for i in range(3):
    base_u = "+".join(f"{USDC[i]}{x}" for x in inf_foreign)
    put(ws_inf, f"{USDC[i]}{r}", f"=({base_u})*{sup('igv')}*{sup('igv_on')}", fmt=USD, border=b_all)
    put(ws_inf, f"{SOLC[i]}{r}", f"={USDC[i]}{r}*{sup('tc')}", fmt=SOL, border=b_all)
inf_igv = r
r += 1
put(ws_inf, f"A{r}", "TOTAL INFRAESTRUCTURA Y HERRAMIENTAS (mensual)", f_bold)
for cols in (SOLC, USDC):
    for c in cols:
        put(ws_inf, f"{c}{r}", f"={c}{inf_sub}+{c}{inf_igv}", f_bold, fmt=SOL if cols is SOLC else USD,
            fill=fill_tot, border=b_top)
INF_TOT = r
widths(ws_inf, {"A": 34, "B": 40, "C": 18, "D": 14, "E": 11, "F": 11, "G": 11, "H": 13, "I": 13, "J": 13,
                "K": 13, "L": 13, "M": 13, "N": 22, "O": 48, "P": 12})
ws_inf.freeze_panes = "B6"

# ============================================================ Costos variables
title(ws_var, "Costos variables — por recojo y por mes",
      "Escalan con escaneos y recojos. Los pagos a personas solo corren cuando «pagos reales» = 1; la IA, cuando «IA de pago» = 1. "
      "El pago por recojo al rider corre solo en la modalidad independiente (código 1).")
group_header(ws_var, 4, [("F", "Cantidad al mes"), ("I", "Costo mensual (S/)"), ("L", "Costo mensual (US$)"), ("O", "Costo por recojo (S/)")])
header(ws_var, 5, ["Concepto", "Detalle", "Moneda", "Costo unitario", "Unidad", *SC, *SC, *SC, *SC, "Estado", "Fuente"])
VQ, VS, VU, VR = ["F", "G", "H"], ["I", "J", "K"], ["L", "M", "N"], ["O", "P", "Q"]
vrow = [6]
V = {}


def vsec(text):
    section(ws_var, vrow[0], text, 19)
    vrow[0] += 1


def vadd(key, concept, detail, cur, unit_cost, unit, qty, estado, fuente, ufmt):
    r = vrow[0]
    put(ws_var, f"A{r}", concept, align=wrap)
    put(ws_var, f"B{r}", detail, align=wrap)
    put(ws_var, f"C{r}", cur)
    put(ws_var, f"D{r}", unit_cost, fmt=ufmt, border=b_all)
    put(ws_var, f"E{r}", unit, align=wrap)
    for i in range(3):
        put(ws_var, f"{VQ[i]}{r}", qty(i), fmt=INT, border=b_all)
        if cur == "US$":
            put(ws_var, f"{VU[i]}{r}", f"=$D{r}*{VQ[i]}{r}", fmt=USD, border=b_all)
            put(ws_var, f"{VS[i]}{r}", f"={VU[i]}{r}*{sup('tc')}", fmt=SOL, border=b_all)
        else:
            put(ws_var, f"{VS[i]}{r}", f"=$D{r}*{VQ[i]}{r}", fmt=SOL, border=b_all)
            put(ws_var, f"{VU[i]}{r}", f"={VS[i]}{r}/{sup('tc')}", fmt=USD, border=b_all)
        put(ws_var, f"{VR[i]}{r}", f"=IF({esc('pick', i)}=0,0,{VS[i]}{r}/{esc('pick', i)})", fmt=SOL, border=b_all)
    put(ws_var, f"R{r}", estado)
    put(ws_var, f"S{r}", fuente, align=wrap)
    V[key] = r
    vrow[0] += 1
    return r


def vtotal(key, label, rows, fill=fill_tot, border=b_top):
    r = vrow[0]
    put(ws_var, f"A{r}", label, f_bold)
    for cols, fmt in ((VS, SOL), (VU, USD), (VR, SOL)):
        for c in cols:
            put(ws_var, f"{c}{r}", "=" + "+".join(f"{c}{x}" for x in rows), f_bold, fmt=fmt, fill=fill, border=border)
    V[key] = r
    vrow[0] += 1
    return r


vsec("Tecnología variable (CIF variable)")
vadd("vision", "IA de visión (detect-material)", "Gemini 3.5 Flash-Lite; (entrada × precio + salida × precio) ÷ 1 millón × factor de reintentos",
     "US$", f"=({sup('tok_in')}*{sup('vis_in')}+{sup('tok_out')}*{sup('vis_out')})/1000000*{sup('hedge')}",
     "foto analizada", lambda i: f"={esc('photos', i)}*{sup('ia_pay', i)}", "VERIFICADO (precio) / SUPUESTO (tokens)",
     "[F6] [P4]", USD4)
vadd("chatc", "IA del asistente (maki-assistant)", "Gemini 2.5 Flash → 3.5 Flash-Lite desde el 01-dic-2026, misma tarifa",
     "US$", f"=({sup('chat_tok_in')}*{sup('chat_in')}+{sup('chat_tok_out')}*{sup('chat_out')})/1000000",
     "mensaje", lambda i: f"={esc('chat', i)}*{sup('ia_pay', i)}", "VERIFICADO (precio) / SUPUESTO (tokens)", "[F6]", USD4)
vadd("push", "Avisos push", "FCM (Android) y Web Push (PWA)", "US$", 0, "aviso",
     lambda i: f"={esc('pick', i)}*{sup('push')}", "VERIFICADO", "[F15] [F16]", USD4)
vadd("wa", "WhatsApp Business (respaldo opcional)", "Plantilla utility; tarifa de referencia × (1 + alza supuesta)",
     "US$", f"={sup('wa_ref')}*(1+{sup('wa_alza')})", "mensaje",
     lambda i: f"={esc('pick', i)}*{sup('wa_pct', i)}*{sup('wa_msgs')}", "REFERENCIA + SUPUESTO", "[F17]", USD4)
vadd("smsc", "SMS (alternativa)", "Twilio, Perú", "US$", f"={sup('sms')}", "segmento",
     lambda i: f"={esc('pick', i)}*{sup('sms_pct', i)}*{sup('sms_msgs')}", "VERIFICADO", "[F18]", USD4)
r = vrow[0]
put(ws_var, f"A{r}", "IGV sobre servicios digitales del exterior", align=wrap)
put(ws_var, f"B{r}", "18 % × indicador de «Supuestos»", align=wrap)
put(ws_var, f"R{r}", "VERIFICADO (tasa)")
put(ws_var, f"S{r}", "[F2]")
tech_rows = [V["vision"], V["chatc"], V["push"], V["wa"], V["smsc"]]
for i in range(3):
    put(ws_var, f"{VU[i]}{r}", "=(" + "+".join(f"{VU[i]}{x}" for x in tech_rows) + f")*{sup('igv')}*{sup('igv_on')}", fmt=USD, border=b_all)
    put(ws_var, f"{VS[i]}{r}", f"={VU[i]}{r}*{sup('tc')}", fmt=SOL, border=b_all)
    put(ws_var, f"{VR[i]}{r}", f"=IF({esc('pick', i)}=0,0,{VS[i]}{r}/{esc('pick', i)})", fmt=SOL, border=b_all)
V["igv"] = r
vrow[0] += 1
vtotal("tech", "Subtotal tecnología variable", tech_rows + [V["igv"]])
vrow[0] += 1

vsec("Pagos por recojo a personas")
vadd("points", "Material comprado al recolector (puntos canjeados)", "kg × precio ponderado × 40 % × % canjeado — materiales directos",
     "S/", f"={sup('kg')}*{sup('p_avg')}*{sup('share')}*{sup('canje')}", "recojo",
     lambda i: f"={esc('pick', i)}*{sup('real_pay', i)}", "DEL PROYECTO + SUPUESTO (kg, mezcla)", "[P1]", SOL)
vadd("rider", "Pago al EcoRider independiente", "Tarifa base ÷ recojos por viaje + adicional por km × km — mano de obra directa",
     "S/", f"={sup('fee_base')}/{sup('paradas')}+{sup('fee_km')}*{sup('km')}", "recojo",
     lambda i: f"={esc('pick', i)}*{sup('real_pay', i)}*IF({sup('rider_mode', i)}=1,1,0)", "DEL PROYECTO + SUPUESTO (km, paradas)", "[P2]", SOL)
r = vrow[0]
put(ws_var, f"A{r}", "Comisión por enviar pagos", align=wrap)
put(ws_var, f"B{r}", "% de «Supuestos» sobre lo pagado a riders y recolectores", align=wrap)
put(ws_var, f"R{r}", "SUPUESTO")
for i in range(3):
    put(ws_var, f"{VS[i]}{r}", f"=({VS[i]}{V['rider']}+{VS[i]}{V['points']})*{sup('commission')}", fmt=SOL, border=b_all)
    put(ws_var, f"{VU[i]}{r}", f"={VS[i]}{r}/{sup('tc')}", fmt=USD, border=b_all)
    put(ws_var, f"{VR[i]}{r}", f"=IF({esc('pick', i)}=0,0,{VS[i]}{r}/{esc('pick', i)})", fmt=SOL, border=b_all)
V["comm"] = r
vrow[0] += 1
vtotal("ops", "Subtotal pagos por recojo", [V["rider"], V["points"], V["comm"]])
vrow[0] += 1
vtotal("total", "TOTAL COSTOS VARIABLES DE ESTA HOJA (mensual)", [V["tech"], V["ops"]])
vrow[0] += 2

vsec("Referencia: costo de IA de visión por escaneo según el modelo (mismos tokens y fotos del MVP)")
r = vrow[0]
for j, h in enumerate(["Modelo", "Entrada (US$/M)", "Salida (US$/M)", "Por foto (US$)", "Por escaneo (US$)",
                       "Por 1.000 escaneos (US$)", "Por 1.000 escaneos (S/)"]):
    put(ws_var, f"{get_column_letter(1 + j)}{r}", h, f_hdr, fill=fill_hdr, align=center, border=b_all)
vrow[0] += 1
for k, name, *_ in ALT:
    r = vrow[0]
    put(ws_var, f"A{r}", name)
    put(ws_var, f"B{r}", f"={sup(f'alt_{k}_in')}", f_link, fmt=USD, border=b_all)
    put(ws_var, f"C{r}", f"={sup(f'alt_{k}_out')}", f_link, fmt=USD, border=b_all)
    put(ws_var, f"D{r}", f"=({sup('tok_in')}*B{r}+{sup('tok_out')}*C{r})/1000000*{sup('hedge')}", fmt=USD4, border=b_all)
    put(ws_var, f"E{r}", f"=D{r}*{sup('photos', 0)}", fmt=USD4, border=b_all)
    put(ws_var, f"F{r}", f"=E{r}*1000", fmt=USD, border=b_all)
    put(ws_var, f"G{r}", f"=F{r}*{sup('tc')}", fmt=SOL, border=b_all)
    vrow[0] += 1
note(ws_var, vrow[0], "Nota: la estimación previa de ~US$ 0,002 por escaneo (Claude Haiku 5.5) suponía un prompt y una imagen más "
     "pequeños; con el catálogo de marcas actual el prompt pesa ~4.000 tokens. Cambiar de proveedor no requiere más que la clave.", "K")
widths(ws_var, {"A": 34, "B": 44, "C": 8, "D": 14, "E": 14, **{c: 11 for c in VQ}, **{c: 13 for c in VS + VU + VR},
                "R": 26, "S": 16})
ws_var.freeze_panes = "B6"

# ============================================================ Mano de obra
title(ws_mo, "Mano de obra — cargas laborales, equipo fijo y EcoRiders",
      "Cargas por régimen calculadas con parámetros VERIFICADOS de «Supuestos»; sueldos y dotación son SUPUESTOS.")
M = {}
header(ws_mo, 4, ["Componente (sobre el sueldo bruto)", "Base", "1. Régimen general", "2. Pequeña empresa (REMYPE)",
                  "3. Microempresa (REMYPE)", "4. Recibos por honorarios", "Fuente"])
REG = ["C", "D", "E", "F"]
mrow = 5


def mreg(key, label, base, vals, fmt, src):
    global mrow
    put(ws_mo, f"A{mrow}", label, align=wrap)
    put(ws_mo, f"B{mrow}", base, align=wrap)
    for c, v in zip(REG, vals):
        put(ws_mo, f"{c}{mrow}", v, fmt=fmt, border=b_all)
    put(ws_mo, f"G{mrow}", src, align=wrap)
    M[key] = mrow
    mrow += 1


mreg("essalud", "EsSalud (empleador)", "% del sueldo", [f"={sup('essalud')}", f"={sup('essalud')}", 0, 0], PCT2, "[F26] Micro: SIS (fila siguiente)")
mreg("sis", "SIS semicontributivo (microempresa)", "S/ por persona al mes", [0, 0, f"={sup('sis_micro')}", 0], SOL, "SUPUESTO")
mreg("grati", "Gratificaciones (provisión mensual)", "% del sueldo", [f"={sup('grati_gen')}/12", f"={sup('grati_peq')}/12", 0, 0], PCT2, "[F26] [F28]")
mreg("bonif", "Bonificación extraordinaria (9 % de la gratificación)", "% del sueldo",
     [f"=C{M['grati']}*{sup('bonif')}", f"=D{M['grati']}*{sup('bonif')}", 0, 0], PCT2, "[F26] Ley 30334")
mreg("cts", "CTS (provisión mensual)", "% del sueldo", [f"={sup('cts_gen')}/12", f"={sup('cts_peq')}/360", 0, 0], PCT2, "[F27] [F28]")
mreg("vac", "Vacaciones (provisión mensual)", "% del sueldo", [f"={sup('vac_gen')}/360", f"={sup('vac_mype')}/360", f"={sup('vac_mype')}/360", 0], PCT2, "[F28]")
mreg("vida", "Seguro Vida Ley", "% del sueldo", [f"={sup('vida_ley')}", f"={sup('vida_ley')}", f"={sup('vida_ley')}", 0], PCT2, "SUPUESTO")
put(ws_mo, f"A{mrow}", "Carga laboral total", f_bold)
put(ws_mo, f"B{mrow}", "% del sueldo")
for c in REG:
    put(ws_mo, f"{c}{mrow}", f"={c}{M['essalud']}+{c}{M['grati']}+{c}{M['bonif']}+{c}{M['cts']}+{c}{M['vac']}+{c}{M['vida']}",
        f_bold, fmt=PCT, fill=fill_tot, border=b_top)
M["tot"] = mrow
mrow += 1
put(ws_mo, f"A{mrow}", "Costo fijo por persona", f_bold)
put(ws_mo, f"B{mrow}", "S/ al mes")
for c in REG:
    put(ws_mo, f"{c}{mrow}", f"={c}{M['sis']}", f_bold, fmt=SOL, fill=fill_tot, border=b_top)
M["fix"] = mrow
mrow += 1
note(ws_mo, mrow, "Recibos por honorarios (4.ª categoría): la empresa no paga cargas; retiene el 8 % de cada recibo de más de S/ 1.500 "
     "(impuesto del trabajador) salvo que tenga suspensión (ingresos anuales proyectados ≤ S/ 48.125) [F29]. Riesgo: si hay "
     "subordinación, horario y exclusividad, SUNAFIL puede reconocer la relación laboral. Microempresa: solo vacaciones de 15 días "
     "y SIS; pequeña empresa: media gratificación, CTS de 15 días y EsSalud. Ambos exigen inscripción en el REMYPE [F28].", "G", 55)
mrow += 2


def carga(i):
    return f"INDEX($C${M['tot']}:$F${M['tot']},1,{sup('regimen', i)})"


def fijo(i):
    return f"INDEX($C${M['fix']}:$F${M['fix']},1,{sup('regimen', i)})"


section(ws_mo, mrow, "Equipo fijo — costo empleador mensual (sueldo × (1 + carga) + costo fijo por persona)", 10)
mrow += 1
header(ws_mo, mrow, ["Rol", "Clasificación", "Sueldo bruto (S/)", f"Personas {SC[0]}", f"Personas {SC[1]}", f"Personas {SC[2]}",
                     f"Costo {SC[0]}", f"Costo {SC[1]}", f"Costo {SC[2]}", "Nota"])
mrow += 1
MOQ, MOC = ["D", "E", "F"], ["G", "H", "I"]
role_first = mrow
for k, name, cls, *_ in ROLES:
    put(ws_mo, f"A{mrow}", name, align=wrap)
    put(ws_mo, f"B{mrow}", cls)
    put(ws_mo, f"C{mrow}", f"={sup('sal_' + k)}", f_link, fmt=SOL, border=b_all)
    for i in range(3):
        put(ws_mo, f"{MOQ[i]}{mrow}", f"={sup('n_' + k, i)}", f_link, fmt=DEC1, border=b_all)
        put(ws_mo, f"{MOC[i]}{mrow}", f"=$C{mrow}*{MOQ[i]}{mrow}*(1+{carga(i)})+{MOQ[i]}{mrow}*{fijo(i)}", fmt=SOL, border=b_all)
    put(ws_mo, f"J{mrow}", f'=IF($C{mrow}<{sup("rmv")},"Debajo de la RMV","OK: ≥ RMV")', f_sub)
    M[k] = mrow
    mrow += 1
role_last = mrow - 1
for cls in ("MOI (CIF)", "Gasto administrativo", "Gasto de ventas", "MOD"):
    put(ws_mo, f"A{mrow}", f"Subtotal {cls}", f_bold)
    for i in range(3):
        put(ws_mo, f"{MOC[i]}{mrow}", f'=SUMIF($B${role_first}:$B${role_last},"{cls}",{MOC[i]}${role_first}:{MOC[i]}${role_last})',
            f_bold, fmt=SOL, fill=fill_tot, border=b_all)
    M["sub_" + cls] = mrow
    mrow += 1
put(ws_mo, f"A{mrow}", "Total equipo fijo", f_bold)
for i in range(3):
    put(ws_mo, f"{MOC[i]}{mrow}", f"=SUM({MOC[i]}{role_first}:{MOC[i]}{role_last})", f_bold, fmt=SOL, fill=fill_tot, border=b_top)
M["team_tot"] = mrow
mrow += 2

section(ws_mo, mrow, "EcoRiders — independientes por recojo (hoy) frente a planilla (alternativa)", 10)
mrow += 1
header(ws_mo, mrow, ["Concepto", "Unidad", "", "", "", "", SC[0], SC[1], SC[2], "Cómo se calcula"])
mrow += 1


def mline(key, label, unit, fn, fmt, how, bold=False):
    global mrow
    put(ws_mo, f"A{mrow}", label, f_bold if bold else f_base, align=wrap)
    put(ws_mo, f"B{mrow}", unit)
    for i in range(3):
        put(ws_mo, f"{MOC[i]}{mrow}", fn(i), f_bold if bold else f_base, fmt=fmt, border=b_top if bold else b_all,
            fill=fill_tot if bold else None)
    put(ws_mo, f"J{mrow}", how, f_sub, align=wrap)
    M[key] = mrow
    mrow += 1


mline("rmode", "Modalidad usada (1 independientes, 2 planilla)", "código", lambda i: f"={sup('rider_mode', i)}", FLAG, "Supuestos")
mline("r_ind", "Costo si son independientes (pago por recojo)", "S/ al mes",
      lambda i: f"={esc('pick', i)}*{sup('real_pay', i)}*'Costos variables'!$D${V['rider']}", SOL, "recojos × tarifa por recojo")
mline("r_pla", "Costo si están en planilla", "S/ al mes",
      lambda i: (f"={esc('riders', i)}*{sup('real_pay', i)}*({sup('rider_sal')}*(1+{carga(i)}+{sup('sctr')})"
                 f"+{fijo(i)}+{sup('rider_mov')})"), SOL,
      "riders × (sueldo × (1 + carga + SCTR) + costo fijo + movilidad), con el régimen del escenario")
mline("r_diff", "Diferencia: planilla − independientes", "S/ al mes", lambda i: f"={MOC[i]}{M['r_pla']}-{MOC[i]}{M['r_ind']}", SOL,
      "Positivo = la planilla cuesta más con este volumen")
mline("r_pla_used", "Planilla de riders que entra al estado de costos", "S/ al mes",
      lambda i: f"=IF({sup('rider_mode', i)}=2,{MOC[i]}{M['r_pla']},0)", SOL,
      "Con modalidad 1, el pago por recojo ya está en «Costos variables»", bold=True)
mline("r_rmv", "Control: sueldo del rider en planilla ≥ RMV", "", lambda i: f'=IF({sup("rider_sal")}>={sup("rmv")},"OK","Debajo de la RMV")', None, "")
mline("r_cap", "Recojos por rider por día", "recojos", lambda i: f"={esc('pick_day', i)}", DEC1, "Con pocos recojos por rider, la planilla sale cara")
widths(ws_mo, {"A": 46, "B": 22, "C": 16, "D": 16, "E": 16, "F": 16, "G": 16, "H": 16, "I": 16, "J": 50})
ws_mo.freeze_panes = "A5"

# ============================================================ Indirectos y gastos
title(ws_ind, "Materiales indirectos, otros costos indirectos y gastos de administración y ventas",
      "Clasificación: CIF = costo indirecto del servicio; Gasto = no forma parte del costo del servicio. "
      "Comportamiento: Variable = escala con los recojos (entra al margen de contribución); Fijo = no depende del volumen del mes.")
group_header(ws_ind, 4, [("F", "Cantidad al mes"), ("I", "Costo mensual (S/)")])
header(ws_ind, 5, ["Concepto", "Clasificación", "Comportamiento", "Precio unitario (S/)", "Unidad", *SC, *SC, "Estado", "Fuente / nota"])
IQ, IC = ["F", "G", "H"], ["I", "J", "K"]
nrow = [6]
N = {}
ind_rows = []


def nsec(text):
    section(ws_ind, nrow[0], text, 13)
    nrow[0] += 1


def nadd(key, concept, cls, beh, price, unit, qty, estado, fuente, qfmt=DEC1):
    r = nrow[0]
    put(ws_ind, f"A{r}", concept, align=wrap)
    put(ws_ind, f"B{r}", cls)
    put(ws_ind, f"C{r}", beh)
    if price is None:
        put(ws_ind, f"D{r}", "—", align=right)
    else:
        put(ws_ind, f"D{r}", price, f_link, fmt=SOL, border=b_all)
    put(ws_ind, f"E{r}", unit, align=wrap)
    for i in range(3):
        put(ws_ind, f"{IQ[i]}{r}", qty(i), f_link, fmt=SOL if price is None else qfmt, border=b_all)
        put(ws_ind, f"{IC[i]}{r}", f"={IQ[i]}{r}" if price is None else f"=$D{r}*{IQ[i]}{r}", fmt=SOL, border=b_all)
    put(ws_ind, f"L{r}", estado)
    put(ws_ind, f"M{r}", fuente, align=wrap)
    N[key] = r
    ind_rows.append(r)
    nrow[0] += 1


def rp(i):  # recojos con operación real
    return f"{esc('pick', i)}*{sup('real_pay', i)}"


def rd(i):  # riders en operación real
    return f"{esc('riders', i)}*{sup('real_pay', i)}"


CIF_MI, CIF_DEP, CIF_OT, G_ADM, G_VEN = "CIF – materiales indirectos", "CIF – depreciación", "CIF – otros", "Gasto administrativo", "Gasto de ventas"
nsec("Materiales indirectos")
nadd("bolsa", "Bolsas o costales", CIF_MI, "Variable", f"={sup('bolsa')}", "recojo", lambda i: f"={rp(i)}", "SUPUESTO", "Supuestos §10", INT)
nadd("precinto", "Etiquetas / precintos", CIF_MI, "Variable", f"={sup('precinto')}", "recojo", lambda i: f"={rp(i)}", "SUPUESTO", "Supuestos §10", INT)
nadd("impresos", "Volantes de separación para recolectores", CIF_MI, "Variable", f"={sup('impresos')}", "recolector activo",
     lambda i: f"={esc('act', i)}*{sup('real_pay', i)}", "SUPUESTO", "Supuestos §10", INT)
nadd("chaleco", "Chaleco reflectivo (prorrateo por vida útil)", CIF_MI, "Fijo", f"={sup('chaleco')}/{sup('chaleco_vida')}", "rider",
     lambda i: f"={rd(i)}", "REFERENCIA", "[F32]", INT)
nadd("uniforme", "Uniforme (prorrateo por vida útil)", CIF_MI, "Fijo", f"={sup('uniforme')}/{sup('uniforme_vida')}", "rider",
     lambda i: f"={rd(i)}", "SUPUESTO", "Supuestos §10", INT)
nadd("epp", "Guantes y otros EPP", CIF_MI, "Fijo", f"={sup('epp')}", "rider", lambda i: f"={rd(i)}", "SUPUESTO", "Supuestos §10", INT)
nsec("Depreciación (línea recta con las tasas de SUNAT)")
nadd("dep_lap", "Laptops", CIF_DEP, "Fijo", f"={sup('laptop')}*{sup('dep_pc')}/12", "unidad", lambda i: f"={sup('laptop_n', i)}",
     "VERIFICADO (tasa)", "[F30] 25 % anual", INT)
nadd("dep_tel", "Teléfonos de prueba", CIF_DEP, "Fijo", f"={sup('phone')}*{sup('dep_pc')}/12", "unidad", lambda i: f"={sup('phone_n', i)}",
     "SUPUESTO (clasificación)", "[F30] tratados como equipo de procesamiento de datos", INT)
nadd("dep_bal", "Balanza de plataforma", CIF_DEP, "Fijo", f"={sup('balanza')}*{sup('dep_maq')}/12", "unidad",
     lambda i: f"={sup('balanza_n', i)}", "VERIFICADO (tasa)", "[F30] 10 % anual", INT)
nsec("Otros costos indirectos del servicio")
nadd("lineas", "Internet y telefonía móvil", CIF_OT, "Fijo", f"={sup('linea')}", "línea", lambda i: f"={sup('lineas_n', i)}", "SUPUESTO", "", INT)
nadd("seguro", "Seguro de accidentes de riders independientes", CIF_OT, "Fijo", f"={sup('seg_ind')}", "rider",
     lambda i: f"={rd(i)}*{sup('seg_ind_on', i)}*IF({sup('rider_mode', i)}=1,1,0)", "SUPUESTO",
     "En planilla se usa el SCTR (hoja «Mano de obra»)", INT)
nadd("cowork", "Coworking", CIF_OT, "Fijo", f"={sup('cowork')}", "puesto", lambda i: f"={sup('cowork_n', i)}", "SUPUESTO", "", INT)
nsec("Gastos administrativos")
nadd("contador", "Contador externo", G_ADM, "Fijo", None, "S/ al mes", lambda i: f"={sup('contador', i)}", "SUPUESTO", "")
nadd("bancos", "Gastos bancarios y otros", G_ADM, "Fijo", None, "S/ al mes", lambda i: f"={sup('bancos', i)}", "SUPUESTO", "")
nsec("Gastos de ventas")
nadd("mkt", "Marketing continuo", G_VEN, "Fijo", None, "S/ al mes", lambda i: f"={sup('mkt', i)}", "SUPUESTO", "")
# la comisión comercial se llena cuando exista la hoja Ingresos (referencia hacia adelante, sin problema en Excel)
COM_ROW = nrow[0]
nadd("comision", "Comisión comercial sobre convenios y estudios", G_VEN, "Fijo", None, "S/ al mes", lambda i: "=0", "SUPUESTO",
     "% de «Supuestos» × ingresos fijos (convenios + estudios)")
nrow[0] += 1
for key, label, crit, col in (("sum_mi", "Subtotal CIF – materiales indirectos", CIF_MI, "B"),
                              ("sum_dep", "Subtotal CIF – depreciación", CIF_DEP, "B"),
                              ("sum_ot", "Subtotal CIF – otros", CIF_OT, "B"),
                              ("sum_adm", "Subtotal gastos administrativos", G_ADM, "B"),
                              ("sum_ven", "Subtotal gastos de ventas", G_VEN, "B"),
                              ("sum_var", "De los anteriores: variables", "Variable", "C")):
    r = nrow[0]
    put(ws_ind, f"A{r}", label, f_bold)
    first, last = ind_rows[0], ind_rows[-1]
    for i in range(3):
        put(ws_ind, f"{IC[i]}{r}", f'=SUMIF(${col}${first}:${col}${last},"{crit}",{IC[i]}${first}:{IC[i]}${last})',
            f_bold, fmt=SOL, fill=fill_tot, border=b_all)
    N[key] = r
    nrow[0] += 1
r = nrow[0]
put(ws_ind, f"A{r}", "TOTAL DE ESTA HOJA (mensual)", f_bold)
for i in range(3):
    put(ws_ind, f"{IC[i]}{r}", f"=SUM({IC[i]}{ind_rows[0]}:{IC[i]}{ind_rows[-1]})", f_bold, fmt=SOL, fill=fill_tot, border=b_top)
N["total"] = r
nrow[0] += 1
widths(ws_ind, {"A": 44, "B": 28, "C": 15, "D": 16, "E": 16, **{c: 12 for c in IQ}, **{c: 14 for c in IC}, "L": 22, "M": 46})
ws_ind.freeze_panes = "B6"

# ============================================================ Única vez
title(ws_uni, "Costos de única vez",
      "Se pagan una sola vez por escenario. Las horas se valorizan con el costo por hora de «Supuestos». "
      "La compra de equipos es inversión: su costo mensual es la depreciación de «Indirectos y gastos».")
group_header(ws_uni, 4, [("F", "Cantidad"), ("I", "Costo (S/)"), ("L", "Costo (US$)")])
header(ws_uni, 5, ["Concepto", "Detalle", "Moneda", "Precio unitario", "Unidad", *SC, *SC, *SC, "Estado", "Fuente"])
UQ, US_, UU = ["F", "G", "H"], ["I", "J", "K"], ["L", "M", "N"]
urow = [6]
u_rows = []


def uadd(concept, detail, cur, price, unit, qty, estado, fuente, qfmt=INT):
    r = urow[0]
    put(ws_uni, f"A{r}", concept, align=wrap)
    put(ws_uni, f"B{r}", detail, align=wrap)
    put(ws_uni, f"C{r}", cur)
    if price is None:
        put(ws_uni, f"D{r}", "—", align=right)
    else:
        put(ws_uni, f"D{r}", price, f_link, fmt=USD if cur == "US$" else SOL, border=b_all)
    put(ws_uni, f"E{r}", unit)
    for i in range(3):
        put(ws_uni, f"{UQ[i]}{r}", qty(i), f_link, fmt=SOL if price is None else qfmt, border=b_all)
        base = f"{UQ[i]}{r}" if price is None else f"$D{r}*{UQ[i]}{r}"
        if cur == "US$":
            put(ws_uni, f"{UU[i]}{r}", f"={base}", fmt=USD, border=b_all)
            put(ws_uni, f"{US_[i]}{r}", f"={UU[i]}{r}*{sup('tc')}", fmt=SOL, border=b_all)
        else:
            put(ws_uni, f"{US_[i]}{r}", f"={base}", fmt=SOL, border=b_all)
            put(ws_uni, f"{UU[i]}{r}", f"={US_[i]}{r}/{sup('tc')}", fmt=USD, border=b_all)
    put(ws_uni, f"O{r}", estado)
    put(ws_uni, f"P{r}", fuente, align=wrap)
    u_rows.append(r)
    urow[0] += 1
    return r


uadd("Cuenta de desarrollador de Google Play", "Pago único para publicar la app Android", "US$", f"={sup('play')}", "cuenta",
     lambda i: f"={sup('play_n', i)}", "VERIFICADO", "[F19] https://support.google.com/googleplay/android-developer/answer/6112435")
uadd("Apple Developer Program", "No se necesita: en iPhone se usa la PWA", "US$", 0, "cuenta", lambda i: "=0", "DEL PROYECTO", "[P6]")
uadd("Constitución de la empresa", "SAC por SID-SUNARP + notaría; RUC gratuito", "S/", f"={sup('const')}", "empresa",
     lambda i: f"={sup('const_n', i)}", "REFERENCIA", "[F34]")
uadd("Puesta en producción", "Replicar esquema, Edge Functions y secretos a PRODUCCIÓN (hoy vacía)", "S/", f"={sup('rate_h')}", "hora",
     lambda i: f"={sup('h_prod', i)}", "SUPUESTO", "Horas y tarifa en «Supuestos»")
uadd("Avisos push", "Implementar FCM (Android) y Web Push (PWA)", "S/", f"={sup('rate_h')}", "hora",
     lambda i: f"={sup('h_push', i)}", "SUPUESTO", "Horas y tarifa en «Supuestos»")
uadd("Proveedor de mapas y rutas", "Cambiar URLs de teselas y ruteo en Android, PWA y panel", "S/", f"={sup('rate_h')}", "hora",
     lambda i: f"={sup('h_maps', i)}", "SUPUESTO", "Horas y tarifa en «Supuestos»")
uadd("Correo propio", "SMTP (Resend) y registros DNS del dominio", "S/", f"={sup('rate_h')}", "hora",
     lambda i: f"={sup('h_smtp', i)}", "SUPUESTO", "Horas y tarifa en «Supuestos»")
uadd("Revisión legal", "Protección de datos personales: consentimientos y bancos de datos", "S/", f"={sup('legal')}", "revisión",
     lambda i: f"={sup('legal_n', i)}", "SUPUESTO", "Cotizar con un abogado")
uadd("Marketing de lanzamiento", "Campaña de inscripción en el distrito (gasto de ventas)", "S/", None, "S/",
     lambda i: f"={sup('mkt_launch', i)}", "SUPUESTO", "Supuestos §7")
uadd("Compra de laptops", "Inversión en equipos (se deprecia en «Indirectos y gastos»)", "S/", f"={sup('laptop')}", "unidad",
     lambda i: f"={sup('laptop_n', i)}", "SUPUESTO", "Supuestos §10")
uadd("Compra de teléfonos de prueba", "Inversión en equipos", "S/", f"={sup('phone')}", "unidad",
     lambda i: f"={sup('phone_n', i)}", "SUPUESTO", "Supuestos §10")
uadd("Compra de balanzas", "Inversión en equipos", "S/", f"={sup('balanza')}", "unidad",
     lambda i: f"={sup('balanza_n', i)}", "REFERENCIA", "[F31]")
uadd("Dominio", "Es anual: está prorrateado en «Infraestructura»; no se suma aquí", "S/", 0, "—", lambda i: "=0", "—", "Ver «Infraestructura»")
r = urow[0]
put(ws_uni, f"A{r}", "TOTAL ÚNICA VEZ", f_bold)
for cols, fmt in ((US_, SOL), (UU, USD)):
    for c in cols:
        put(ws_uni, f"{c}{r}", "=" + "+".join(f"{c}{x}" for x in u_rows), f_bold, fmt=fmt, fill=fill_tot, border=b_top)
UNI_TOT = r
widths(ws_uni, {"A": 34, "B": 52, "C": 8, "D": 14, "E": 10, **{c: 11 for c in UQ}, **{c: 13 for c in US_ + UU},
                "O": 16, "P": 52})
ws_uni.freeze_panes = "B6"

# ============================================================ Ingresos
title(ws_ing, "Ingresos — por fuente y escenario",
      "Montos sin IGV. Cada fuente se enciende o apaga en «Supuestos» §12. Las fuentes distintas a la venta del material "
      "son hipótesis comerciales sin contrato.")
header(ws_ing, 4, ["Fuente de ingreso", "Comportamiento", *[f"{s} — mensual" for s in SC], *[f"{s} — anual" for s in SC], "Estado", "Nota"])
ANN = ["F", "G", "H"]
G = {}
grow = [5]


def gadd(key, label, beh, fn, estado, nota, bold=False, fmt=SOL):
    r = grow[0]
    put(ws_ing, f"A{r}", label, f_bold if bold else f_base, align=wrap)
    put(ws_ing, f"B{r}", beh)
    for i in range(3):
        put(ws_ing, f"{SUPC[i]}{r}", fn(i), f_bold if bold else f_base, fmt=fmt, border=b_top if bold else b_all,
            fill=fill_tot if bold else None)
        if fmt == SOL:
            put(ws_ing, f"{ANN[i]}{r}", f"={SUPC[i]}{r}*12", f_bold if bold else f_base, fmt=SOL, border=b_top if bold else b_all,
                fill=fill_tot if bold else None)
    put(ws_ing, f"I{r}", estado)
    put(ws_ing, f"J{r}", nota, align=wrap)
    G[key] = r
    grow[0] += 1


def gl(key, i):
    return f"{SUPC[i]}{G[key]}"


gadd("mat", "Venta del material reciclable al acopio", "Variable",
     lambda i: f"={esc('kg', i)}*{sup('p_avg')}*{sup('on_mat', i)}", "DEL PROYECTO + SUPUESTO", "kg × precio ponderado [P1]")
gadd("muni_fix", "Convenios municipales — tarifa fija", "Fijo",
     lambda i: f"={sup('muni_n', i)}*{sup('muni_fee')}*{sup('on_muni', i)}", "SUPUESTO", "distritos × tarifa mensual")
gadd("muni_t", "Convenios municipales — por tonelada desviada", "Variable",
     lambda i: f"={esc('kg', i)}/1000*{sup('muni_t')}*{sup('on_muni', i)}", "SUPUESTO", "toneladas × S/ por tonelada")
gadd("est", "Estudios de mercado de marcas y envases", "Fijo",
     lambda i: f"={sup('est_n', i)}*{sup('est_price')}*{sup('on_est', i)}", "SUPUESTO",
     "Agregados y anonimizados; requiere consentimiento (Ley 29733) y volumen mínimo por celda [F36]")
gadd("rep", "Aportes por responsabilidad extendida del productor (hipótesis)", "Variable",
     lambda i: f"={esc('kg', i)}/1000*{sup('rep_t')}*{sup('on_rep', i)}", "SUPUESTO", "Hipótesis por verificar con la norma de residuos")
gadd("tot", "TOTAL INGRESOS", "", lambda i: f"={gl('mat', i)}+{gl('muni_fix', i)}+{gl('muni_t', i)}+{gl('est', i)}+{gl('rep', i)}",
     "", "", bold=True)
gadd("var", "De ellos, ingresos variables (escalan con los kg)", "", lambda i: f"={gl('mat', i)}+{gl('muni_t', i)}+{gl('rep', i)}", "", "")
gadd("fix", "De ellos, ingresos fijos", "", lambda i: f"={gl('muni_fix', i)}+{gl('est', i)}", "", "")
grow[0] += 1
section(ws_ing, grow[0], "Margen sobre el material (precio de venta − lo pagado en puntos al recolector)", 10)
grow[0] += 1
gadd("m_sale", "Venta del material", "", lambda i: f"={gl('mat', i)}", "", "")
gadd("m_pts", "Puntos pagados al recolector", "", lambda i: f"='Costos variables'!{VS[i]}{V['points']}", "", "Hoja «Costos variables»")
gadd("m_margin", "Margen sobre el material", "", lambda i: f"={gl('m_sale', i)}-{gl('m_pts', i)}", "", "", bold=True)
gadd("m_pct", "Margen sobre el material (% de la venta)", "", lambda i: f"=IF({gl('m_sale', i)}=0,0,{gl('m_margin', i)}/{gl('m_sale', i)})",
     "", "Equivale a 1 − 40 % × % canjeado", fmt=PCT)
grow[0] += 1
note(ws_ing, grow[0], "Advertencia: la venta de estudios de mercado solo es posible con consentimiento previo, libre, expreso e inequívoco "
     "(Ley 29733 y su reglamento D.S. 016-2024-JUS). Hoy la base no registra consentimiento ni hay volumen de datos suficiente "
     "(docs/panel-duenos-analisis.md). Los convenios municipales y los aportes REP no tienen contrato ni referencia pública de precio.", "J", 45)
widths(ws_ing, {"A": 46, "B": 15, **{c: 15 for c in SUPC + ANN}, "I": 22, "J": 50})
ws_ing.freeze_panes = "B5"

# comisión comercial (necesita Ingresos)
for i in range(3):
    ws_ind[f"{IQ[i]}{COM_ROW}"].value = f"=Ingresos!{SUPC[i]}{G['fix']}*{sup('com_ventas')}"

# ============================================================ Estado de costos
title(ws_est, "Estado de costos del servicio — por escenario",
      "Clasificación contable peruana adaptada a una plataforma de servicio. Mensual y anual (× 12). Todo traído de las hojas de detalle.")
header(ws_est, 4, ["Concepto", "Origen", *[f"{s} — mensual" for s in SC], *[f"{s} — anual" for s in SC]])
T = {}
trow = [5]


def tsec(text):
    section(ws_est, trow[0], text, 8)
    trow[0] += 1


def tadd(key, label, origin, fn, bold=False, font=f_link, fmt=SOL):
    r = trow[0]
    put(ws_est, f"A{r}", label, f_bold if bold else f_base, align=wrap)
    put(ws_est, f"B{r}", origin, f_sub, align=wrap)
    for i in range(3):
        put(ws_est, f"{SUPC[i]}{r}", fn(i), f_bold if bold else font, fmt=fmt, border=b_top if bold else b_all,
            fill=fill_tot if bold else None)
        if fmt == SOL:
            put(ws_est, f"{ANN[i]}{r}", f"={SUPC[i]}{r}*12", f_bold if bold else f_base, fmt=SOL, border=b_top if bold else b_all,
                fill=fill_tot if bold else None)
    T[key] = r
    trow[0] += 1


def tl(key, i):
    return f"{SUPC[i]}{T[key]}"


tsec("1. Materiales directos (MD)")
tadd("md", "Material reciclable comprado al recolector (puntos canjeados)", "Costos variables",
     lambda i: f"='Costos variables'!{VS[i]}{V['points']}")
tsec("2. Mano de obra directa (MOD)")
tadd("mod_ind", "EcoRiders independientes — pago por recojo", "Costos variables",
     lambda i: f"='Costos variables'!{VS[i]}{V['rider']}")
tadd("mod_pla", "EcoRiders en planilla (si la modalidad es 2)", "Mano de obra",
     lambda i: f"='Mano de obra'!{MOC[i]}{M['r_pla_used']}")
tadd("mod_aux", "Personal de acopio (recepción y pesaje)", "Mano de obra",
     lambda i: f"='Mano de obra'!{MOC[i]}{M['sub_MOD']}")
tadd("mod", "Subtotal MOD", "", lambda i: f"={tl('mod_ind', i)}+{tl('mod_pla', i)}+{tl('mod_aux', i)}", bold=True, font=f_base)
tadd("primo", "COSTO PRIMO (MD + MOD)", "", lambda i: f"={tl('md', i)}+{tl('mod', i)}", bold=True, font=f_base)
tsec("3. Costos indirectos del servicio (CIF)")
tadd("moi", "Mano de obra indirecta (desarrollo, operaciones, soporte)", "Mano de obra",
     lambda i: f"='Mano de obra'!{MOC[i]}{M['sub_MOI (CIF)']}")
tadd("mi", "Materiales indirectos (bolsas, precintos, EPP, uniformes, volantes)", "Indirectos y gastos",
     lambda i: f"='Indirectos y gastos'!{IC[i]}{N['sum_mi']}")
tadd("infra", "Infraestructura tecnológica y herramientas", "Infraestructura",
     lambda i: f"=Infraestructura!{SOLC[i]}{INF_TOT}")
tadd("techv", "Tecnología variable (IA, avisos)", "Costos variables",
     lambda i: f"='Costos variables'!{VS[i]}{V['tech']}")
tadd("dep", "Depreciación de equipos", "Indirectos y gastos",
     lambda i: f"='Indirectos y gastos'!{IC[i]}{N['sum_dep']}")
tadd("cif_ot", "Otros CIF (internet y telefonía, seguro de riders, coworking)", "Indirectos y gastos",
     lambda i: f"='Indirectos y gastos'!{IC[i]}{N['sum_ot']}")
tadd("comm", "Comisiones por enviar pagos", "Costos variables",
     lambda i: f"='Costos variables'!{VS[i]}{V['comm']}")
tadd("cif", "Subtotal CIF", "", lambda i: "=" + "+".join(tl(k, i) for k in ("moi", "mi", "infra", "techv", "dep", "cif_ot", "comm")),
     bold=True, font=f_base)
tadd("serv", "COSTO DEL SERVICIO (MD + MOD + CIF)", "", lambda i: f"={tl('primo', i)}+{tl('cif', i)}", bold=True, font=f_base)
tsec("4. Gastos operativos")
tadd("adm_p", "Administración: gerencia", "Mano de obra", lambda i: f"='Mano de obra'!{MOC[i]}{M['sub_Gasto administrativo']}")
tadd("adm_o", "Administración: contador, bancos y otros", "Indirectos y gastos", lambda i: f"='Indirectos y gastos'!{IC[i]}{N['sum_adm']}")
tadd("ven_p", "Ventas: ejecutivo comercial", "Mano de obra", lambda i: f"='Mano de obra'!{MOC[i]}{M['sub_Gasto de ventas']}")
tadd("ven_o", "Ventas: marketing y comisiones", "Indirectos y gastos", lambda i: f"='Indirectos y gastos'!{IC[i]}{N['sum_ven']}")
tadd("gastos", "Subtotal gastos operativos", "", lambda i: f"={tl('adm_p', i)}+{tl('adm_o', i)}+{tl('ven_p', i)}+{tl('ven_o', i)}",
     bold=True, font=f_base)
tadd("total", "COSTO TOTAL (costo del servicio + gastos operativos)", "",
     lambda i: f"={tl('serv', i)}+{tl('gastos', i)}", bold=True, font=f_base)
tsec("5. Resultado")
tadd("ing", "Ingresos", "Ingresos", lambda i: f"=Ingresos!{SUPC[i]}{G['tot']}")
tadd("res", "Resultado operativo (ingresos − costo total)", "", lambda i: f"={tl('ing', i)}-{tl('total', i)}", bold=True, font=f_base)
tadd("ebitda", "EBITDA (resultado operativo + depreciación)", "", lambda i: f"={tl('res', i)}+{tl('dep', i)}", bold=True, font=f_base)
tsec("Controles (deben dar 0)")
tadd("chk_detail", "Costo total − suma de las hojas de detalle", "Infraestructura + Costos variables + Mano de obra + Indirectos y gastos",
     lambda i: (f"=ROUND({tl('total', i)}-(Infraestructura!{SOLC[i]}{INF_TOT}+'Costos variables'!{VS[i]}{V['total']}"
                f"+'Mano de obra'!{MOC[i]}{M['team_tot']}+'Mano de obra'!{MOC[i]}{M['r_pla_used']}"
                f"+'Indirectos y gastos'!{IC[i]}{N['total']}),2)"), font=f_base)
trow[0] += 1
note(ws_est, trow[0], "Cómo se adaptó la clasificación: en una fábrica, los materiales directos son la materia prima. En Ruma, lo que se "
     "«compra» por cada recojo es el material reciclable del hogar, que se paga en puntos canjeables: por eso los puntos son el MD. "
     "La MOD es lo que se paga por ejecutar cada recojo (EcoRider) y por recibirlo y pesarlo. El equipo que sostiene la plataforma "
     "(desarrollo, operaciones, soporte) es MOI y va dentro de los CIF, junto con la tecnología, los materiales indirectos y la "
     "depreciación. Gerencia, contabilidad, comercial y marketing son gastos del periodo: no forman parte del costo del servicio.",
     "H", 75)
widths(ws_est, {"A": 52, "B": 24, **{c: 17 for c in SUPC + ANN}})
ws_est.freeze_panes = "C5"

# ============================================================ Punto de equilibrio
title(ws_pe, "Punto de equilibrio y palancas",
      "Margen de contribución por recojo, punto de equilibrio y qué valor de cada palanca lo vuelve positivo. "
      "Costos fijos = MOI + CIF fijos + gastos (y planilla de riders, si se usa).")
header(ws_pe, 4, ["Concepto", "Unidad", *SC, "Cómo se calcula"])
P = {}
prow = [5]


def psec(text):
    section(ws_pe, prow[0], text, 6)
    prow[0] += 1


def padd(key, label, unit, fn, fmt, how, bold=False, font=f_base):
    r = prow[0]
    put(ws_pe, f"A{r}", label, f_bold if bold else f_base, align=wrap)
    put(ws_pe, f"B{r}", unit)
    for i in range(3):
        put(ws_pe, f"{SUPC[i]}{r}", fn(i), f_bold if bold else font, fmt=fmt, border=b_top if bold else b_all,
            fill=fill_tot if bold else None, align=right)
    put(ws_pe, f"F{r}", how, f_sub, align=wrap)
    P[key] = r
    prow[0] += 1


def pl(key, i):
    return f"{SUPC[i]}{P[key]}"


def na(i, expr):  # sin operación real no hay punto de equilibrio que calcular
    return f'=IF({sup("real_pay", i)}=0,"n. a. (demo)",{expr})'


psec("1. Margen de contribución por recojo")
padd("pick", "Recojos al mes", "recojos", lambda i: f"={esc('pick', i)}", INT, "Escenarios", font=f_link)
padd("iv_m", "Ingresos variables del mes", "S/", lambda i: f"=Ingresos!{SUPC[i]}{G['var']}", SOL, "Ingresos", font=f_link)
padd("cv_m", "Costos variables del mes", "S/",
     lambda i: f"='Costos variables'!{VS[i]}{V['total']}+'Indirectos y gastos'!{IC[i]}{N['sum_var']}", SOL,
     "Costos variables + materiales variables (bolsas, precintos, volantes)", font=f_link)
padd("iv", "Ingreso variable por recojo", "S/ por recojo", lambda i: f"=IF({pl('pick', i)}=0,0,{pl('iv_m', i)}/{pl('pick', i)})", SOL, "")
padd("cv", "Costo variable por recojo", "S/ por recojo", lambda i: f"=IF({pl('pick', i)}=0,0,{pl('cv_m', i)}/{pl('pick', i)})", SOL, "")
padd("cm", "Margen de contribución por recojo", "S/ por recojo", lambda i: f"={pl('iv', i)}-{pl('cv', i)}", SOL,
     "ingreso variable − costo variable", bold=True)
padd("rc", "Razón de contribución", "% del ingreso variable", lambda i: f"=IF({pl('iv', i)}=0,0,{pl('cm', i)}/{pl('iv', i)})", PCT, "")
psec("2. Costos fijos y punto de equilibrio")
padd("ct", "Costo total del mes", "S/", lambda i: f"='Estado de costos'!{SUPC[i]}{T['total']}", SOL, "Estado de costos", font=f_link)
padd("cf", "Costos fijos del mes", "S/", lambda i: f"={pl('ct', i)}-{pl('cv_m', i)}", SOL, "costo total − costos variables")
padd("if", "Ingresos fijos del mes (convenios fijos, estudios)", "S/", lambda i: f"=Ingresos!{SUPC[i]}{G['fix']}", SOL, "Ingresos", font=f_link)
padd("cfn", "Costos fijos netos de ingresos fijos", "S/", lambda i: f"={pl('cf', i)}-{pl('if', i)}", SOL, "lo que deben cubrir los recojos")
padd("pe", "Punto de equilibrio en recojos", "recojos al mes",
     lambda i: na(i, f'IF({pl("cm", i)}<=0,"No existe: cada recojo pierde",MAX(0,{pl("cfn", i)}/{pl("cm", i)}))'), INT,
     "costos fijos netos ÷ margen por recojo", bold=True)
padd("pe_kg", "Punto de equilibrio en kg", "kg al mes", lambda i: f'=IF(ISNUMBER({pl("pe", i)}),{pl("pe", i)}*{sup("kg")},{pl("pe", i)})', INT, "")
padd("pe_act", "Punto de equilibrio en recolectores activos", "personas",
     lambda i: f'=IF(ISNUMBER({pl("pe", i)}),{pl("pe", i)}/{sup("pick", i)},{pl("pe", i)})', INT, "recojos ÷ recojos por activo")
padd("pe_sol", "Punto de equilibrio en ingresos", "S/ al mes",
     lambda i: f'=IF(ISNUMBER({pl("pe", i)}),{pl("pe", i)}*{pl("iv", i)}+{pl("if", i)},{pl("pe", i)})', SOL, "recojos × ingreso variable + ingresos fijos")
padd("res", "Resultado del mes con el volumen del escenario", "S/", lambda i: f"={pl('pick', i)}*{pl('cm', i)}-{pl('cfn', i)}", SOL,
     "recojos × margen − costos fijos netos", bold=True)
padd("chk", "Control: resultado − estado de costos (debe dar 0)", "S/",
     lambda i: f"=ROUND({pl('res', i)}-'Estado de costos'!{SUPC[i]}{T['res']},2)", SOL, "")

psec("3. Composición del costo variable por recojo")
padd("rider_pr", "Pago al rider por recojo", "S/", lambda i: f"=IF({pl('pick', i)}=0,0,'Costos variables'!{VS[i]}{V['rider']}/{pl('pick', i)})", SOL, "")
padd("pts_pr", "Puntos pagados por recojo", "S/", lambda i: f"=IF({pl('pick', i)}=0,0,'Costos variables'!{VS[i]}{V['points']}/{pl('pick', i)})", SOL, "")
padd("comm_r", "Tasa de comisión de pagos", "%", lambda i: f"={sup('commission')}", PCT, "")
padd("oth_pr", "Otros variables por recojo (IA, avisos, bolsas, precintos, volantes)", "S/",
     lambda i: f"={pl('cv', i)}-({pl('rider_pr', i)}+{pl('pts_pr', i)})*(1+{pl('comm_r', i)})", SOL, "")
padd("a", "Margen por kg antes del rider (ingreso − puntos)", "S/ por kg",
     lambda i: f"=({pl('iv', i)}-{pl('pts_pr', i)}*(1+{pl('comm_r', i)}))/{sup('kg')}", SOL, "lo que deja cada kg")
padd("b", "Costo por recojo que no depende de los kg", "S/ por recojo",
     lambda i: f"={pl('rider_pr', i)}*(1+{pl('comm_r', i)})+{pl('oth_pr', i)}", SOL, "rider + otros")

psec("4. Palancas: valor con el que se llega al equilibrio")
padd("lv_kg_now", "Kg por recojo — valor actual", "kg", lambda i: f"={sup('kg')}", DEC1, "Supuestos", font=f_link)
padd("lv_kg_unit", "Kg por recojo para que cada recojo deje de perder", "kg",
     lambda i: na(i, f'IF({pl("a", i)}<=0,"No alcanza",{pl("b", i)}/{pl("a", i)})'), DEC1, "costo por recojo ÷ margen por kg")
padd("lv_kg_full", "Kg por recojo para cubrir también los costos fijos", "kg",
     lambda i: na(i, f'IF({pl("a", i)}<=0,"No alcanza",({pl("b", i)}+{pl("cfn", i)}/{pl("pick", i)})/{pl("a", i)})'), DEC1,
     "con el volumen de recojos del escenario")
padd("lv_pay_now", "Pago al rider por recojo — valor actual", "S/", lambda i: f"='Costos variables'!$D${V['rider']}", SOL,
     "tarifa base ÷ paradas + km × S/ por km", font=f_link)
padd("lv_pay_unit", "Pago máximo al rider para que cada recojo deje de perder", "S/",
     lambda i: na(i, f'IF({sup("rider_mode", i)}=2,"n. a. (planilla)",IF({sup("kg")}*{pl("a", i)}-{pl("oth_pr", i)}<=0,"No alcanza ni con pago cero",'
                     f'({sup("kg")}*{pl("a", i)}-{pl("oth_pr", i)})/(1+{pl("comm_r", i)})))'), SOL, "")
padd("lv_pay_full", "Pago máximo al rider para cubrir también los fijos", "S/",
     lambda i: na(i, f'IF({sup("rider_mode", i)}=2,"n. a. (planilla)",IF({sup("kg")}*{pl("a", i)}-{pl("oth_pr", i)}-{pl("cfn", i)}/{pl("pick", i)}<=0,'
                     f'"No alcanza ni con pago cero",({sup("kg")}*{pl("a", i)}-{pl("oth_pr", i)}-{pl("cfn", i)}/{pl("pick", i)})/(1+{pl("comm_r", i)})))'),
     SOL, "")
padd("lv_par_now", "Recojos por viaje — valor actual", "recojos", lambda i: f"={sup('paradas')}", DEC1, "Supuestos", font=f_link)
padd("lv_par_unit", "Recojos por viaje para que cada recojo deje de perder", "recojos",
     lambda i: (f'=IF(ISNUMBER({pl("lv_pay_unit", i)}),IF({pl("lv_pay_unit", i)}-{sup("fee_km")}*{sup("km")}<=0,"No alcanza",'
                f'{sup("fee_base")}/({pl("lv_pay_unit", i)}-{sup("fee_km")}*{sup("km")})),{pl("lv_pay_unit", i)})'), DEC1,
     "tarifa base ÷ (pago máximo − adicional por km)")
padd("lv_par_full", "Recojos por viaje para cubrir también los fijos", "recojos",
     lambda i: (f'=IF(ISNUMBER({pl("lv_pay_full", i)}),IF({pl("lv_pay_full", i)}-{sup("fee_km")}*{sup("km")}<=0,"No alcanza",'
                f'{sup("fee_base")}/({pl("lv_pay_full", i)}-{sup("fee_km")}*{sup("km")})),{pl("lv_pay_full", i)})'), DEC1, "")
padd("lv_muni_t", "Pago municipal por tonelada para que cada recojo deje de perder", "S/ por tonelada",
     lambda i: na(i, f'MAX(0,-{pl("cm", i)})/{sup("kg")}*1000+{sup("muni_t")}*{sup("on_muni", i)}'), SOL0,
     "adicional al actual: pérdida por recojo ÷ kg × 1.000")
padd("lv_muni_m", "Ingreso fijo adicional para resultado cero (convenios o estudios)", "S/ al mes",
     lambda i: f"=MAX(0,-{pl('res', i)})", SOL0, "lo que falta este mes")

psec("5. Caso «con palancas» (kg y recojos por viaje de «Supuestos» §13)")
padd("pal_rider", "Pago al rider por recojo", "S/",
     lambda i: f"=IF({pl('rider_pr', i)}=0,0,{sup('fee_base')}/{sup('paradas_pal')}+{sup('fee_km')}*{sup('km')})", SOL, "")
padd("pal_iv", "Ingreso variable por recojo", "S/", lambda i: f"={pl('iv', i)}/{sup('kg')}*{sup('kg_pal')}", SOL, "")
padd("pal_cm", "Margen de contribución por recojo", "S/",
     lambda i: f"={sup('kg_pal')}*{pl('a', i)}-({pl('pal_rider', i)}*(1+{pl('comm_r', i)})+{pl('oth_pr', i)})", SOL, "", bold=True)
padd("pal_cv", "Costo variable por recojo", "S/", lambda i: f"={pl('pal_iv', i)}-{pl('pal_cm', i)}", SOL, "")
padd("pal_pe", "Punto de equilibrio en recojos", "recojos al mes",
     lambda i: na(i, f'IF({pl("pal_cm", i)}<=0,"No existe",MAX(0,{pl("cfn", i)}/{pl("pal_cm", i)}))'), INT, "", bold=True)
padd("pal_pe_act", "Punto de equilibrio en recolectores activos", "personas",
     lambda i: f'=IF(ISNUMBER({pl("pal_pe", i)}),{pl("pal_pe", i)}/{sup("pick", i)},{pl("pal_pe", i)})', INT, "")
padd("pal_pe_kg", "Punto de equilibrio en kg", "kg al mes",
     lambda i: f'=IF(ISNUMBER({pl("pal_pe", i)}),{pl("pal_pe", i)}*{sup("kg_pal")},{pl("pal_pe", i)})', INT, "")
padd("pal_res", "Resultado del mes con el volumen del escenario", "S/", lambda i: f"={pl('pick', i)}*{pl('pal_cm', i)}-{pl('cfn', i)}", SOL, "")

# sensibilidad
prow[0] += 1
psec("6. Sensibilidad: resultado mensual (S/) según kg por recojo × pago al rider por recojo")
sel = sup("sens_esc")


def selv(key):
    return f"INDEX($C${P[key]}:$E${P[key]},1,{sel})"


put(ws_pe, f"A{prow[0]}", "Escenario analizado:", f_bold)
put(ws_pe, f"C{prow[0]}", f'=INDEX(Supuestos!$C$5:$E$5,1,{sel})', f_link)
put(ws_pe, f"D{prow[0]}", "(se cambia en «Supuestos» §13; supone riders independientes)", f_sub)
prow[0] += 1
hdr = prow[0]
put(ws_pe, f"A{hdr}", "kg por recojo ↓ / pago al rider →", f_hdr, fill=fill_hdr, align=center)
NK, NP = 9, 7
for j in range(NP):
    col = get_column_letter(2 + j)
    put(ws_pe, f"{col}{hdr}", f"={sup('sens_p0')}+{j}*{sup('sens_pd')}", f_hdr, fmt=SOL, fill=fill_hdr, align=center)
for k in range(NK):
    r = hdr + 1 + k
    put(ws_pe, f"A{r}", f"={sup('sens_kg0')}+{k}*{sup('sens_kgd')}", f_bold, fmt=DEC1, align=center, border=b_all)
    for j in range(NP):
        col = get_column_letter(2 + j)
        put(ws_pe, f"{col}{r}",
            f"={selv('pick')}*($A{r}*{selv('a')}-({col}${hdr}*(1+{selv('comm_r')})+{selv('oth_pr')}))-{selv('cfn')}",
            fmt=SOL0, border=b_all)
grid = f"B{hdr + 1}:{get_column_letter(1 + NP)}{hdr + NK}"
ws_pe.conditional_formatting.add(grid, CellIsRule(operator="lessThan", formula=["0"], font=Font(name=F, size=10, color="C00000")))
ws_pe.conditional_formatting.add(grid, CellIsRule(operator="greaterThanOrEqual", formula=["0"],
                                                  font=Font(name=F, size=10, color="00703C", bold=True),
                                                  fill=PatternFill("solid", fgColor="E2EFDA")))
prow[0] = hdr + NK + 1
note(ws_pe, prow[0], "Verde = resultado mensual positivo con el volumen de recojos del escenario. Cada celda: recojos × (kg × margen por kg "
     "− (pago × (1 + comisión) + otros variables)) − costos fijos netos.", "H")
prow[0] += 2
psec("6b. Sensibilidad: margen de contribución por recojo (S/), mismos ejes — no depende del volumen")
hdr2 = prow[0]
put(ws_pe, f"A{hdr2}", "kg por recojo ↓ / pago al rider →", f_hdr, fill=fill_hdr, align=center)
for j in range(NP):
    col = get_column_letter(2 + j)
    put(ws_pe, f"{col}{hdr2}", f"={col}{hdr}", f_hdr, fmt=SOL, fill=fill_hdr, align=center)
for k in range(NK):
    r = hdr2 + 1 + k
    put(ws_pe, f"A{r}", f"=A{hdr + 1 + k}", f_bold, fmt=DEC1, align=center, border=b_all)
    for j in range(NP):
        col = get_column_letter(2 + j)
        put(ws_pe, f"{col}{r}", f"=$A{r}*{selv('a')}-({col}${hdr2}*(1+{selv('comm_r')})+{selv('oth_pr')})", fmt=SOL, border=b_all)
grid2 = f"B{hdr2 + 1}:{get_column_letter(1 + NP)}{hdr2 + NK}"
ws_pe.conditional_formatting.add(grid2, CellIsRule(operator="lessThan", formula=["0"], font=Font(name=F, size=10, color="C00000")))
ws_pe.conditional_formatting.add(grid2, CellIsRule(operator="greaterThanOrEqual", formula=["0"],
                                                   font=Font(name=F, size=10, color="00703C", bold=True),
                                                   fill=PatternFill("solid", fgColor="E2EFDA")))
prow[0] = hdr2 + NK + 2

psec("7. Gráfico de punto de equilibrio (escenario analizado): ingresos y costos totales según volumen")
ch_hdr = prow[0]
header(ws_pe, ch_hdr, ["Recojos al mes", "Ingresos (actual)", "Costo total (actual)", "Ingresos (con palancas)",
                       "Costo total (con palancas)"])
maxv = (f"MAX(10,2*{selv('pick')},IF(ISNUMBER({selv('pe')}),1.5*{selv('pe')},0),"
        f"IF(ISNUMBER({selv('pal_pe')}),1.5*{selv('pal_pe')},0))")
for k in range(11):
    r = ch_hdr + 1 + k
    put(ws_pe, f"A{r}", f"=ROUNDUP({maxv},-1)*{k}/10", fmt=INT, border=b_all)
    put(ws_pe, f"B{r}", f"={selv('if')}+A{r}*{selv('iv')}", fmt=SOL0, border=b_all)
    put(ws_pe, f"C{r}", f"={selv('cf')}+A{r}*{selv('cv')}", fmt=SOL0, border=b_all)
    put(ws_pe, f"D{r}", f"={selv('if')}+A{r}*{selv('pal_iv')}", fmt=SOL0, border=b_all)
    put(ws_pe, f"E{r}", f"={selv('cf')}+A{r}*{selv('pal_cv')}", fmt=SOL0, border=b_all)
sc = ScatterChart()
sc.title = "Punto de equilibrio: ingresos vs. costo total (S/ al mes)"
sc.x_axis.title = "Recojos al mes"
sc.x_axis.majorGridlines = None
sc.legend.overlay = False
sc.y_axis.title = None
sc.x_axis.delete = False
sc.y_axis.delete = False
sc.y_axis.numFmt = '#,##0'
sc.x_axis.numFmt = '#,##0'
xs = Reference(ws_pe, min_col=1, min_row=ch_hdr + 1, max_row=ch_hdr + 11)
for col, color, dash in ((2, "1F4E3D", None), (3, "C00000", None), (4, "1F4E3D", "dash"), (5, "C00000", "dash")):
    ys = Reference(ws_pe, min_col=col, min_row=ch_hdr, max_row=ch_hdr + 11)
    s = Series(ys, xs, title_from_data=True)
    s.marker.symbol = "none"
    s.graphicalProperties.line.solidFill = color
    s.graphicalProperties.line.width = 22000
    if dash:
        s.graphicalProperties.line.dashStyle = dash
    sc.series.append(s)
sc.legend.position = "b"
sc.height, sc.width = 9, 18
ws_pe.add_chart(sc, f"G{ch_hdr}")
widths(ws_pe, {"A": 52, "B": 16, "C": 18, "D": 18, "E": 18, "F": 46, "G": 14, "H": 14})
ws_pe.freeze_panes = "C5"

# ============================================================ Escenarios (costos)
erow[0] = esc_cost_start
esec("Costo mensual por clasificación (S/) — traído del estado de costos")
for key, label, src in (("c_md", "Materiales directos (puntos)", "md"), ("c_mod", "Mano de obra directa", "mod"),
                        ("c_cif", "Costos indirectos del servicio", "cif"), ("c_gastos", "Gastos operativos", "gastos")):
    eadd(key, label, "S/ al mes", lambda i, s=src: f"='Estado de costos'!{SUPC[i]}{T[s]}", SOL, "Estado de costos", f_link)
eadd("c_total", "Costo total mensual", "S/ al mes",
     lambda i: f"={loc('c_md', i)}+{loc('c_mod', i)}+{loc('c_cif', i)}+{loc('c_gastos', i)}", SOL, "suma", bold=True)
eadd("c_total_usd", "Costo total mensual", "US$ al mes", lambda i: f"={loc('c_total', i)}/{sup('tc')}", USD, "S/ ÷ tipo de cambio")
eadd("c_ing", "Ingresos mensuales", "S/ al mes", lambda i: f"=Ingresos!{SUPC[i]}{G['tot']}", SOL, "Ingresos", f_link)
eadd("c_res", "Resultado operativo mensual", "S/ al mes", lambda i: f"={loc('c_ing', i)}-{loc('c_total', i)}", SOL, "ingresos − costo total", bold=True)
eadd("c_uni", "Costos de única vez", "S/", lambda i: f"='Única vez'!{US_[i]}{UNI_TOT}", SOL, "Única vez, total", f_link)
esec("Indicadores")
eadd("k_pick", "Costo total por recojo", "S/ por recojo", lambda i: f"=IF({loc('pick', i)}=0,0,{loc('c_total', i)}/{loc('pick', i)})", SOL,
     "costo total ÷ recojos")
eadd("k_kg", "Costo total por kg recolectado", "S/ por kg", lambda i: f"=IF({loc('kg', i)}=0,0,{loc('c_total', i)}/{loc('kg', i)})", SOL,
     "costo total ÷ kg")
eadd("k_tech_user", "Costo tecnológico por recolector activo", "S/ al mes",
     lambda i: (f"=IF({loc('act', i)}=0,0,('Estado de costos'!{SUPC[i]}{T['infra']}+'Estado de costos'!{SUPC[i]}{T['techv']})"
                f"/{loc('act', i)})"), SOL, "(infraestructura + tecnología variable) ÷ activos")
eadd("k_tech_share", "Peso de la tecnología en el costo total", "%",
     lambda i: (f"=IF({loc('c_total', i)}=0,0,('Estado de costos'!{SUPC[i]}{T['infra']}+'Estado de costos'!{SUPC[i]}{T['techv']})"
                f"/{loc('c_total', i)})"), PCT, "")
eadd("k_people_share", "Peso del equipo fijo en el costo total", "%",
     lambda i: f"=IF({loc('c_total', i)}=0,0,'Mano de obra'!{MOC[i]}{M['team_tot']}/{loc('c_total', i)})", PCT, "")
eadd("k_cm", "Margen de contribución por recojo", "S/ por recojo", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['cm']}", SOL,
     "Punto de equilibrio", f_link)
widths(ws_esc, {"A": 46, "B": 16, "C": 24, "D": 24, "E": 24, "F": 60})
ws_esc.freeze_panes = "C5"

# ============================================================ Resumen
title(ws_res, "Ruma — Detalle de costos de operación",
      f"Cifras al {HOY}. Montos en soles (S/) y dólares (US$). Todas las celdas son fórmulas sobre las hojas de detalle.")
put(ws_res, "A3", "Tipo de cambio usado (S/ por US$):", f_bold)
put(ws_res, "C3", f"={sup('tc')}", f_link, fmt=DEC3)
header(ws_res, 5, ["Concepto", "Unidad", *SC])
R = {}
rr = [6]


def rsec(text):
    section(ws_res, rr[0], text, 5)
    rr[0] += 1


def radd(key, label, unit, fn, fmt, bold=False, font=f_link, ws=ws_res, reg=R, cur=rr):
    r = cur[0]
    put(ws, f"A{r}", label, f_bold if bold else f_base, align=wrap)
    put(ws, f"B{r}", unit)
    for i in range(3):
        put(ws, f"{SUPC[i]}{r}", fn(i), f_bold if bold else font, fmt=fmt, border=b_top if bold else b_all,
            fill=fill_tot if bold else None, align=right)
    reg[key] = r
    cur[0] += 1


def rl(key, i):
    return f"{SUPC[i]}{R[key]}"


rsec("Volumen del escenario")
radd("reg", "Recolectores registrados", "personas", lambda i: f"={esc('reg', i)}", INT)
radd("act", "Recolectores activos en el mes", "personas", lambda i: f"={esc('act', i)}", INT)
radd("riders", "EcoRiders", "personas", lambda i: f"={esc('riders', i)}", INT)
radd("pick", "Recojos al mes", "recojos", lambda i: f"={esc('pick', i)}", INT)
radd("kg", "Material recolectado al mes", "kg", lambda i: f"={esc('kg', i)}", INT)
rsec("Costo mensual (estado de costos)")
radd("md", "Materiales directos (puntos al recolector)", "S/", lambda i: f"='Estado de costos'!{SUPC[i]}{T['md']}", SOL)
radd("mod", "Mano de obra directa (EcoRiders)", "S/", lambda i: f"='Estado de costos'!{SUPC[i]}{T['mod']}", SOL)
radd("cif", "Costos indirectos del servicio (equipo, tecnología, materiales)", "S/", lambda i: f"='Estado de costos'!{SUPC[i]}{T['cif']}", SOL)
radd("gastos", "Gastos de administración y ventas", "S/", lambda i: f"='Estado de costos'!{SUPC[i]}{T['gastos']}", SOL)
radd("tot", "Costo total mensual", "S/", lambda i: f"={rl('md', i)}+{rl('mod', i)}+{rl('cif', i)}+{rl('gastos', i)}", SOL, bold=True)
radd("tot_usd", "Costo total mensual", "US$", lambda i: f"={rl('tot', i)}/{sup('tc')}", USD, font=f_base)
radd("tech", "  de ello, tecnología (infraestructura + IA + avisos)", "S/",
     lambda i: f"='Estado de costos'!{SUPC[i]}{T['infra']}+'Estado de costos'!{SUPC[i]}{T['techv']}", SOL)
rsec("Ingresos y resultado mensual")
radd("ing", "Ingresos", "S/", lambda i: f"=Ingresos!{SUPC[i]}{G['tot']}", SOL)
radd("res", "Resultado operativo", "S/", lambda i: f"={rl('ing', i)}-{rl('tot', i)}", SOL, bold=True)
radd("ebitda", "EBITDA", "S/", lambda i: f"='Estado de costos'!{SUPC[i]}{T['ebitda']}", SOL)
rsec("Anual y primer año")
radd("anual", "Costo total anual", "S/", lambda i: f"={rl('tot', i)}*12", SOL, bold=True)
radd("anual_usd", "Costo total anual", "US$", lambda i: f"={rl('anual', i)}/{sup('tc')}", USD, font=f_base)
radd("ing_anual", "Ingresos anuales", "S/", lambda i: f"={rl('ing', i)}*12", SOL, font=f_base)
radd("uni", "Costos de única vez (incluye compra de equipos)", "S/", lambda i: f"='Única vez'!{US_[i]}{UNI_TOT}", SOL)
radd("y1", "Desembolso del primer año (costo anual sin depreciación + única vez)", "S/",
     lambda i: f"=({rl('tot', i)}-'Estado de costos'!{SUPC[i]}{T['dep']})*12+{rl('uni', i)}", SOL, bold=True)
radd("y1_usd", "Desembolso del primer año", "US$", lambda i: f"={rl('y1', i)}/{sup('tc')}", USD, font=f_base)
rsec("Indicadores")
radd("k_pick", "Costo total por recojo", "S/", lambda i: f"={esc('k_pick', i)}", SOL)
radd("k_cm", "Margen de contribución por recojo", "S/", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['cm']}", SOL)
radd("k_pe", "Punto de equilibrio (recojos al mes)", "recojos", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['pe']}", INT)
radd("k_pe_pal", "Punto de equilibrio con palancas (recojos al mes)", "recojos", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['pal_pe']}", INT)
rsec("Controles (deben dar 0)")
radd("chk1", "Resumen − estado de costos", "S/", lambda i: f"=ROUND({rl('tot', i)}-'Estado de costos'!{SUPC[i]}{T['total']},2)", SOL, font=f_base)
radd("chk2", "Estado de costos − suma de hojas de detalle", "S/", lambda i: f"='Estado de costos'!{SUPC[i]}{T['chk_detail']}", SOL)
radd("chk3", "Resultado − punto de equilibrio", "S/", lambda i: f"=ROUND({rl('res', i)}-'Punto de equilibrio'!{SUPC[i]}{P['res']},2)", SOL, font=f_base)

ch = BarChart()
ch.type = "col"
ch.grouping = "stacked"
ch.overlap = 100
ch.title = "Costo mensual por escenario (S/)"
ch.y_axis.numFmt = '#,##0'
ch.y_axis.majorGridlines = None
for key, color in (("md", "A9D08E"), ("mod", "70AD47"), ("cif", "1F4E3D"), ("gastos", "7F7F7F")):
    s = Series(Reference(ws_res, min_col=3, max_col=5, min_row=R[key], max_row=R[key]),
               title_from_data=False, title=ws_res[f"A{R[key]}"].value)
    s.graphicalProperties.solidFill = color
    s.graphicalProperties.line.solidFill = color
    ch.series.append(s)
ch.set_categories(Reference(ws_res, min_col=3, max_col=5, min_row=5, max_row=5))
ch.height, ch.width = 9, 17
ch.x_axis.delete = False
ch.y_axis.delete = False
ch.legend.position = "b"
ch.legend.overlay = False
ws_res.add_chart(ch, f"A{rr[0] + 1}")

nota_row = rr[0] + 20
put(ws_res, f"A{nota_row}", "Alcance", f_sec)
alcance = [
    f"Costo de operar Ruma tal como existe el {HOY}: app Android, PWA para iPhone y panel web del acopio; backend Supabase con "
    "dos proyectos (staging y producción) y Edge Functions detect-material (visión por IA) y maki-assistant (chat).",
    "Incluye: pagos por recojo (EcoRiders y puntos), equipo fijo con cargas laborales, materiales indirectos, depreciación, "
    "infraestructura, IA, avisos, mapas, dominio, herramientas, gastos de administración y ventas, y costos de única vez.",
    "No incluye: impuesto a la renta, IGV de las ventas, financiamiento, vehículos de los riders independientes ni el local del acopio aliado.",
    "«MVP / demo» es la situación de hoy: niveles gratuitos, pagos de prueba y equipo sin sueldo.",
    "Cada precio tiene fuente y fecha en «Fuentes y notas». Lo marcado SUPUESTO es una estimación editable en «Supuestos».",
]
for k, t in enumerate(alcance):
    put(ws_res, f"A{nota_row + 1 + k}", "• " + t, align=wrap)
    ws_res.merge_cells(f"A{nota_row + 1 + k}:E{nota_row + 1 + k}")
    ws_res.row_dimensions[nota_row + 1 + k].height = 30
widths(ws_res, {"A": 58, "B": 12, "C": 20, "D": 20, "E": 20})
ws_res.freeze_panes = "A6"

# ============================================================ Inversionistas
title(ws_inv, "Ruma — Resumen para inversionistas",
      f"Reciclaje domiciliario con recojo a domicilio en el Perú. Cifras al {HOY}, en soles (S/) salvo indicación. "
      "Todas las celdas se calculan desde «Supuestos».")
put(ws_inv, "A3", "Inversión considerada:", f_bold)
put(ws_inv, "C3", f"={sup('inversion')}", f_link, fmt=SOL0)
put(ws_inv, "D3", "Tipo de cambio:", f_bold, align=right)
put(ws_inv, "E3", f"={sup('tc')}", f_link, fmt=DEC3)
header(ws_inv, 5, ["Indicador", "Unidad", *SC])
I = {}
ir = [6]


def vsec_inv(text):
    section(ws_inv, ir[0], text, 5)
    ir[0] += 1


def vadd_inv(key, label, unit, fn, fmt, bold=False, font=f_link):
    radd(key, label, unit, fn, fmt, bold=bold, font=font, ws=ws_inv, reg=I, cur=ir)


def il(key, i):
    return f"{SUPC[i]}{I[key]}"


vsec_inv("Operación")
vadd_inv("act", "Recolectores activos al mes", "personas", lambda i: f"={esc('act', i)}", INT)
vadd_inv("riders", "EcoRiders", "personas", lambda i: f"={esc('riders', i)}", INT)
vadd_inv("pick", "Recojos al mes", "recojos", lambda i: f"={esc('pick', i)}", INT)
vadd_inv("kg", "Material recolectado al mes", "kg", lambda i: f"={esc('kg', i)}", INT)
vsec_inv("Resultados mensuales")
vadd_inv("ing", "Ingresos", "S/ al mes", lambda i: f"='Estado de costos'!{SUPC[i]}{T['ing']}", SOL0)
vadd_inv("serv", "Costo del servicio", "S/ al mes", lambda i: f"='Estado de costos'!{SUPC[i]}{T['serv']}", SOL0)
vadd_inv("gastos", "Gastos de administración y ventas", "S/ al mes", lambda i: f"='Estado de costos'!{SUPC[i]}{T['gastos']}", SOL0)
vadd_inv("tot", "Costo total", "S/ al mes", lambda i: f"={il('serv', i)}+{il('gastos', i)}", SOL0, bold=True, font=f_base)
vadd_inv("res", "Resultado operativo", "S/ al mes", lambda i: f"={il('ing', i)}-{il('tot', i)}", SOL0, font=f_base)
vadd_inv("ebitda", "EBITDA estimado", "S/ al mes", lambda i: f"='Estado de costos'!{SUPC[i]}{T['ebitda']}", SOL0, bold=True)
vadd_inv("ebitda_pct", "Margen EBITDA", "% de ingresos", lambda i: f"=IF({il('ing', i)}=0,0,{il('ebitda', i)}/{il('ing', i)})", PCT, font=f_base)
vsec_inv("Resultados anuales")
vadd_inv("ing_a", "Ingresos", "S/ al año", lambda i: f"={il('ing', i)}*12", SOL0, font=f_base)
vadd_inv("tot_a", "Costo total", "S/ al año", lambda i: f"={il('tot', i)}*12", SOL0, font=f_base)
vadd_inv("tot_a_usd", "Costo total", "US$ al año", lambda i: f"={il('tot_a', i)}/{sup('tc')}", USD, font=f_base)
vadd_inv("ebitda_a", "EBITDA", "S/ al año", lambda i: f"={il('ebitda', i)}*12", SOL0, bold=True, font=f_base)
vsec_inv("Economía por unidad")
vadd_inv("u_iv", "Ingreso variable por recojo", "S/", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['iv']}", SOL)
vadd_inv("u_cv", "Costo variable por recojo", "S/", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['cv']}", SOL)
vadd_inv("u_cm", "Margen de contribución por recojo", "S/", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['cm']}", SOL, bold=True)
vadd_inv("u_ing_act", "Ingreso por recolector activo", "S/ al mes", lambda i: f"=IF({il('act', i)}=0,0,{il('ing', i)}/{il('act', i)})", SOL, font=f_base)
vadd_inv("u_cm_act", "Margen de contribución por recolector activo", "S/ al mes",
     lambda i: f"=IF({il('act', i)}=0,0,{il('u_cm', i)}*{il('pick', i)}/{il('act', i)})", SOL, font=f_base)
vadd_inv("u_cost_act", "Costo total por recolector activo", "S/ al mes", lambda i: f"=IF({il('act', i)}=0,0,{il('tot', i)}/{il('act', i)})", SOL, font=f_base)
vsec_inv("Punto de equilibrio")
vadd_inv("pe", "Con los supuestos actuales", "recojos al mes", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['pe']}", INT)
vadd_inv("pe_pal", "Con palancas (más kg por recojo y rutas de varias paradas)", "recojos al mes",
     lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['pal_pe']}", INT)
vadd_inv("pe_pal_act", "Con palancas, en recolectores activos", "personas", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['pal_pe_act']}", INT)
vadd_inv("kg_unit", "Kg por recojo para que cada recojo deje de perder", "kg", lambda i: f"='Punto de equilibrio'!{SUPC[i]}{P['lv_kg_unit']}", DEC1)
vsec_inv("Caja")
vadd_inv("inv", "Inversión", "S/", lambda i: f"={sup('inversion')}", SOL0)
vadd_inv("uni", "Desembolsos de única vez (constitución, equipos, lanzamiento)", "S/", lambda i: f"='Única vez'!{US_[i]}{UNI_TOT}", SOL0)
vadd_inv("caja", "Caja disponible para operar", "S/", lambda i: f"={il('inv', i)}-{il('uni', i)}", SOL0, font=f_base)
vadd_inv("burn", "Consumo de caja mensual (burn)", "S/ al mes", lambda i: f"=MAX(0,-{il('ebitda', i)})", SOL0, bold=True, font=f_base)
vadd_inv("runway", "Meses de pista (runway)", "meses",
     lambda i: f'=IF({il("burn", i)}=0,"Sin consumo de caja",IF({il("caja", i)}<=0,0,{il("caja", i)}/{il("burn", i)}))', DEC1, bold=True, font=f_base)
vsec_inv("Controles (deben dar 0)")
vadd_inv("chk1", "Costo total − Resumen", "S/", lambda i: f"=ROUND({il('tot', i)}-Resumen!{SUPC[i]}{R['tot']},2)", SOL, font=f_base)
vadd_inv("chk2", "Resultado − punto de equilibrio", "S/", lambda i: f"=ROUND({il('res', i)}-'Punto de equilibrio'!{SUPC[i]}{P['res']},2)", SOL, font=f_base)
ir[0] += 1
put(ws_inv, f"A{ir[0]}", "Lectura", f_sec)
ir[0] += 1
for t in [
    "El burn es el EBITDA negativo: incluye sueldos con cargas, pagos por recojo y tecnología; la depreciación no es salida de caja.",
    "La tecnología pesa poco en el costo; lo que define la rentabilidad es el margen por recojo (kg por recojo, tarifa del rider, "
    "paradas por viaje) y los ingresos que no dependen del material (convenios municipales, estudios de mercado).",
    "Con los supuestos actuales cada recojo pierde: ver la hoja «Punto de equilibrio» para el valor de cada palanca que lo corrige.",
    "Convenios municipales, estudios de mercado y aportes REP son hipótesis sin contrato; los estudios requieren consentimiento "
    "según la Ley 29733 y aún no existen.",
    "Todas las cifras marcadas SUPUESTO se cambian en «Supuestos» y el libro se recalcula.",
]:
    put(ws_inv, f"A{ir[0]}", "• " + t, align=wrap)
    ws_inv.merge_cells(f"A{ir[0]}:E{ir[0]}")
    ws_inv.row_dimensions[ir[0]].height = 30
    ir[0] += 1
widths(ws_inv, {"A": 60, "B": 16, "C": 20, "D": 20, "E": 20})
ws_inv.freeze_panes = "A6"

# ============================================================ Fuentes y notas
title(ws_src, "Fuentes y notas", f"Consultadas el {HOY}. VERIFICADO = leído en la fuente oficial ese día; "
      "REFERENCIA = solo fuente de terceros; DEL PROYECTO = código o base de datos de Ruma; SUPUESTO = sin fuente externa.")
header(ws_src, 4, ["ID", "Concepto", "Valor usado", "Estado", "Fuente (URL)", "Fecha", "Nota"])
SRC = [
    ("F1", "Tipo de cambio", "S/ 3,452 por US$ (venta SUNAT)", "VERIFICADO",
     "https://dsn.pe/precio-del-dolar-hoy-en-peru-6-de-noviembre-baja-la-venta-en-el-mercado-paralelo-y-asi-cotiza-en-sunat/",
     "Medio que reporta la cifra SUNAT del 06-nov-2026 (compra 3,446). Cruce: Gestión, cierre BCRP del 05-nov S/ 3,445 "
     "(https://gestion.pe/economia/mercados/tipo-de-cambio-dolar-hoy-en-peru-cuanto-esta-el-precio-del-dolar-este-viernes-6-de-noviembre-dolares-a-soles-compra-y-venta-bcrp-noticia/)."),
    ("F2", "IGV a servicios digitales del exterior", "18 %", "VERIFICADO",
     "https://andina.pe/agencia/noticia-desde-1-octubre-se-aplicara-pago-igv-a-servicios-streaming-923874.aspx",
     "D. Leg. 1623, vigente desde el 01-oct-2024. Hubo proyectos para derogarlo; su estado actual no se reconfirmó."),
    ("F3", "Supabase", "Pro US$ 25/organización; Micro US$ 10; Small US$ 15; crédito US$ 10; egress 250 GB + US$ 0,09/GB; "
     "2 M invocaciones + US$ 2/M; Free: 5 GB egress, 500 MB de BD, 2 proyectos activos", "VERIFICADO", "https://supabase.com/pricing",
     "Pro se cobra por organización; cada proyecto paga su cómputo. Free pausa los proyectos tras 1 semana sin actividad."),
    ("F4", "SMTP por defecto de Supabase", "2 correos/hora, solo al equipo", "VERIFICADO",
     "https://supabase.com/docs/guides/auth/auth-smtp", "No apto para producción; sin SLA."),
    ("F5", "Resend (correo)", "Free 3.000/mes y 100/día; Pro US$ 20 por 50.000", "VERIFICADO", "https://resend.com/pricing", ""),
    ("F6", "Gemini API", "3.5 Flash-Lite US$ 0,30 / 2,50; 3.1 Flash-Lite 0,25 / 1,50; 2.5 Flash-Lite 0,10 / 0,40; "
     "2.5 Flash 0,30 / 2,50 (por millón de tokens de entrada / salida)", "VERIFICADO", "https://aistudio.google.com/docs/pricing",
     "Todos tienen nivel gratis. 2.5 Flash está deprecado: desde el 01-dic-2026 se enruta a 3.5 Flash-Lite y se cobra a su "
     "tarifa. No se confirmó a qué modelo apunta el alias flash-lite-latest."),
    ("F7", "Claude (Anthropic)", "Haiku 5.5 US$ 0,10 / 0,50 por millón; Claude Pro US$ 20 mensual (US$ 17 anual); Max US$ 100",
     "VERIFICADO", "https://claude.com/pricing", ""),
    ("F8", "Groq Qwen3.8-27B (preview)", "US$ 0,80 / 4,00 por millón", "REFERENCIA",
     "https://www.usagepricing.com/blueprint/activity/groq-2026-08-27-qwen-self-serve-pricing",
     "groq.com/pricing no mostró precios por modelo. Solo comparación."),
    ("F9", "Vercel", "Pro US$ 20 por asiento de desarrollador al mes, con US$ 20 de uso incluido", "VERIFICADO",
     "https://vercel.com/pricing", "Hobby: solo uso personal y no comercial."),
    ("F10", "Teselas de OpenStreetMap", "Gratis", "VERIFICADO", "https://operations.osmfoundation.org/policies/tiles/",
     "Sin SLA; pueden bloquear sin aviso; exige User-Agent propio; prohíbe descarga para uso sin conexión."),
    ("F11", "OSRM público (FOSSGIS)", "Gratis", "VERIFICADO", "https://routing.openstreetmap.de/about.html",
     "Máximo 1 consulta por segundo; nada de uso intensivo."),
    ("F12", "Stadia Maps", "Starter US$ 20/mes, 1 M créditos, US$ 0,03 por 1.000 extra; 1 crédito por tesela, 20 por ruta",
     "VERIFICADO", "https://stadiamaps.com/pricing", "Plan Free no permite uso comercial."),
    ("F13", "MapTiler Cloud (alternativa)", "Flex US$ 30/mes: 25 mil sesiones y 500 mil consultas", "VERIFICADO",
     "https://www.maptiler.com/cloud/pricing/", "Free solo para pruebas y uso no comercial. No incluido en los totales."),
    ("F14", "Servidor propio para OSRM (alternativa)", "Hetzner CPX32: 4 vCPU, 8 GB, € 35,99/mes sin IVA", "VERIFICADO",
     "https://www.hetzner.com/cloud/regular-performance/", "No incluido en los totales."),
    ("F15", "Firebase Cloud Messaging", "Sin costo (planes Spark y Blaze)", "VERIFICADO", "https://firebase.google.com/pricing", ""),
    ("F16", "Web Push (VAPID)", "Sin costo por mensaje", "SUPUESTO", "https://developer.mozilla.org/en-US/docs/Web/API/Push_API",
     "El estándar no cobra y los servicios push de los navegadores no publican tarifa. En iPhone exige la PWA instalada."),
    ("F17", "WhatsApp Business (Meta)", "US$ 0,024 por plantilla utility a Perú (referencia) + 25 % de alza supuesta",
     "REFERENCIA + SUPUESTO", "https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing",
     "Meta confirma alza de utility y autenticación para Perú desde el 01-oct-2026; el monto nuevo no está en la página. "
     "Referencia: https://chatalog.ai/en/pricing-whatsapp-conversation-fees/ (sep-2026)."),
    ("F18", "SMS Twilio a Perú", "US$ 0,2476 por segmento", "VERIFICADO", "https://www.twilio.com/en-us/sms/pricing/pe",
     "Pueden sumarse cargos de operador."),
    ("F19", "Google Play", "US$ 25, pago único", "VERIFICADO",
     "https://support.google.com/googleplay/android-developer/answer/6112435", ""),
    ("F20", "Dominio .com", "US$ 11,17 al año", "REFERENCIA",
     "https://webhosting.today/2026/05/21/verisign-starts-a-four-year-com-price-cycle-the-first-7-hits-in-november/",
     "Mayorista Verisign US$ 10,97 desde el 01-nov-2026 + ICANN US$ 0,20; Cloudflare dice vender al costo "
     "(https://www.cloudflare.com/products/registrar/). Puede subir hasta 7 % al año en 2027–2029."),
    ("F21", "Dominio .pe", "S/ 150 al año (tope del rango de renovación)", "REFERENCIA",
     "https://kom.pe/cuanto-cuesta-un-dominio-pe-en-2026-y-donde-comprarlo/",
     "Terceros: S/ 110 el primer año en promoción y S/ 120–150 la renovación. No se confirmó en punto.pe."),
    ("F22", "ChatGPT Plus (Codex)", "US$ 20 al mes", "REFERENCIA", "https://automationatlas.io/answers/chatgpt-codex-pricing-explained-2026/",
     "openai.com/chatgpt/pricing respondió 403. Codex viene incluido en Plus."),
    ("F23", "Google Stitch", "Gratis", "REFERENCIA", "https://www.nxcode.io/resources/news/google-stitch-pricing-plans-complete-guide-2026",
     "Experimento de Google Labs; podría pasar a ser de pago."),
    ("F24", "Remuneración mínima vital", "S/ 1.230 desde el 01-oct-2026", "VERIFICADO",
     "https://elperuano.pe/noticia/305879-desde-hoy-rige-la-nueva-rmv-sueldo-minimo-sube-a-s-1230-y-cambia-otros-conceptos-laborales",
     "D.S. 015-2026-TR (diario oficial El Peruano). Segundo tramo a S/ 1.300 en el primer semestre de 2027."),
    ("F25", "UIT 2026", "S/ 5.500", "VERIFICADO", "https://elperuano.pe/noticia/285208-mef-establece-en-s-5-500-la-unidad-impositiva-tributaria-para-2026",
     "D.S. 301-2025-EF."),
    ("F26", "Gratificaciones, bonificación extraordinaria y EsSalud", "2 gratificaciones al año; bonificación = 9 % (aporte EsSalud)",
     "VERIFICADO", "https://cdn.www.gob.pe/uploads/document/file/3321103/guia_gratificaciones.pdf",
     "Guía del MTPE (gob.pe). Ley 30334: https://www2.congreso.gob.pe/sicr/cendocbib/con4_uibd.nsf/655742D28BB4292C05257EF400058AC6/$FILE/30334.pdf. "
     "Excluye a locadores de servicios y trabajadores de microempresa."),
    ("F27", "CTS (régimen general)", "Sueldo computable (sueldo + 1/6 gratificación) ÷ 12 por mes; depósitos en mayo y noviembre",
     "VERIFICADO", "https://cdn.www.gob.pe/uploads/document/file/8050277/6757898-guia_compensacion_tiempo_servicios.pdf", "Guía del MTPE (gob.pe)."),
    ("F28", "Régimen laboral MYPE (REMYPE)", "Micro: vacaciones 15 días, SIS, sin gratificación ni CTS. Pequeña: 15 días, "
     "media gratificación, CTS 15 días/año, EsSalud", "VERIFICADO", "https://www.sunat.gob.pe/orientacion/mypes/regimenLaboral.html",
     "SUNAFIL: sin inscripción en REMYPE se paga CTS completa "
     "(https://www.gob.pe/institucion/sunafil/noticias/1389500-pequenas-empresas-que-no-esten-inscritas-en-el-remype-tienen-que-pagar-cts-completa). "
     "Régimen general: vacaciones de 30 días."),
    ("F29", "Recibos por honorarios (4.ª categoría)", "Retención 8 % en recibos > S/ 1.500; suspensión hasta S/ 48.125 al año (2026)",
     "REFERENCIA", "https://modelo.pe/blog/retencion-cuarta-categoria-2026-cuando-aplica-8-por-ciento/",
     "Guía de terceros coherente con la UIT 2026 (8,75 UIT); confirmar en sunat.gob.pe (Formulario 1609)."),
    ("F30", "Tasas de depreciación", "Equipos de procesamiento de datos 25 %; maquinaria y equipo 10 % (máximos anuales)", "VERIFICADO",
     "https://www.sunat.gob.pe/legislacion/oficios/2020/informe-oficios/i151-2020-7T0000.pdf",
     "Informe SUNAT sobre el art. 22 del Reglamento de la Ley del Impuesto a la Renta. La tasa de 10 % se confirmó en fuentes secundarias."),
    ("F31", "Balanza de plataforma 300 kg", "S/ 239 (Ventus B-300A)", "REFERENCIA",
     "https://www.sodimac.com.pe/sodimac-pe/lista/CATG15233/Pesas-y-medidores", "Precio de tienda; varía con promociones."),
    ("F32", "Chaleco reflectivo", "S/ 59,90 (con cinta, talla L)", "REFERENCIA",
     "https://www.falabella.com.pe/falabella-pe/product/113527459/Chaleco-Reflectivo-Naranja-con-Cinta-Talla-L/113527467",
     "En Sodimac Perú hay modelos desde S/ 4,90 (malla) hasta S/ 84,90."),
    ("F33", "Sueldos de mercado", "Desarrollador: Indeed S/ 2.536 promedio; Coderhouse ~S/ 5.000 junior. Call center: S/ 1.500–1.900",
     "REFERENCIA", "https://pe.indeed.com/career/full-stack-developer/salaries",
     "Coderhouse: https://www.coderhouse.com/pe/sueldos/sueldo-desarrollador-full-stack-peru-2025. Los sueldos del libro son SUPUESTOS."),
    ("F34", "Constitución de SAC", "S/ 840–900 por SID-SUNARP", "REFERENCIA", "https://cuantomecuesta.com/pe/crear-empresa/",
     "Guías de terceros (abril 2026); tasas oficiales en sunarp.gob.pe."),
    ("F35", "SCTR", "Tasa según aseguradora y riesgo", "REFERENCIA", "https://www.interseguro.pe/blog/sctr",
     "No hay tasa única; se usó 1,5 % como SUPUESTO."),
    ("F36", "Protección de datos personales", "Consentimiento previo, libre, expreso e inequívoco", "DEL PROYECTO",
     "https://www.congreso.gob.pe/Docs/DGP/DIDP/files/ds_016-2024-jus.pdf",
     "Ley 29733 y reglamento D.S. 016-2024-JUS; análisis en docs/panel-duenos-analisis.md."),
    ("P1", "Economía de puntos", "1.000 pts = S/ 1; 40 % para el recolector; PET 1,00, aluminio 4,00, cartón 0,60, vidrio 0,20, pilas 0 S/ por kg",
     "DEL PROYECTO", "supabase/sql/2026-10-09_precios_reales_y_arbitrios.sql", "Acordado el 2026-10-09."),
    ("P2", "Pago al EcoRider", "S/ 2,50 + S/ 0,30 por km", "DEL PROYECTO",
     "Trigger trg_set_rider_fee (STAGING); citado en app/src/main/java/com/example/maki/data/MakiRepository.kt",
     "Registrado en la memoria del proyecto (2026-09-01). La función no está en supabase/sql/ y no se consultó la base de datos."),
    ("P3", "Capacidad de la IA gratis", "≈ 30 cuadros/min ≈ 3 escáneres simultáneos", "DEL PROYECTO",
     "Prueba de carga contra STAGING del 2026-11-01", "Hoy se agotó la cuota diaria de un modelo."),
    ("P4", "Tamaño de una llamada de visión", "≈ 4.000 tokens de prompt + ≈ 1.100 de imagen; salida máx. 1.024", "DEL PROYECTO",
     "supabase/functions/detect-material/index.ts y brands.ts", "Prompt + catálogo ≈ 14.000 caracteres; imagen a 1024 px."),
    ("P5", "Egress de Supabase", "Free ≈ 50–75 DAU; Pro ≈ 2.500–3.700 DAU", "DEL PROYECTO", "Análisis de backend previo",
     "De ahí sale el supuesto de 80 MB por DAU al mes."),
    ("P6", "App en iPhone", "PWA, sin Apple Developer Program", "DEL PROYECTO", "CLAUDE.md, sección PWA", ""),
]
r = 5
for row in SRC:
    for j, v in enumerate([row[0], row[1], row[2], row[3], row[4], HOY, row[5]]):
        c = put(ws_src, f"{get_column_letter(1 + j)}{r}", v, f_bold if j == 0 else f_base, align=wrap, border=b_all)
        if j == 4 and v.startswith("http"):
            c.hyperlink = v
            c.font = Font(name=F, size=10, color="0563C1", underline="single")
    r += 1
r += 1
put(ws_src, f"A{r}", "Supuestos sin fuente externa (todos editables en «Supuestos»)", f_sec)
r += 1
for t in [
    "Volúmenes de cada escenario: recolectores, % activos, DAU, escaneos, fotos, recojos, mensajes de chat.",
    "Kg por recojo (3 kg), mezcla de materiales, km por recojo (2 km) y recojos por viaje (1): mueven casi todo el margen.",
    "Sueldos de mercado y dotación del equipo; régimen laboral de cada escenario; aporte SIS de la microempresa (S/ 15); "
    "Seguro Vida Ley (0,5 %); SCTR (1,5 %); seguro de accidentes de riders independientes (S/ 25).",
    "Materiales indirectos (bolsas, precintos, uniformes, EPP, volantes), equipos (laptops, teléfonos), internet, coworking.",
    "Gastos de administración y ventas: contador, bancos, marketing, comisión comercial.",
    "Ingresos distintos a la venta del material: convenios municipales (S/ 1.500 por distrito), estudios de mercado "
    "(S/ 2.500 por cliente) y aportes REP: hipótesis sin contrato ni referencia pública.",
    "Tokens por foto y por mensaje de chat, factor de reintentos de la IA, egress por DAU, teselas y rutas.",
    "Planes de herramientas de desarrollo, horas de configuración, costo por hora (S/ 60) y revisión legal (S/ 2.000).",
    "Alza de WhatsApp desde el 01-oct-2026 (25 %) y comisión de pagos masivos (0 %).",
]:
    put(ws_src, f"A{r}", "• " + t, align=wrap)
    ws_src.merge_cells(f"A{r}:G{r}")
    ws_src.row_dimensions[r].height = 30
    r += 1
r += 1
put(ws_src, f"A{r}", "Riesgos de costo", f_sec)
r += 1
for t in [
    "Margen por recojo: con los supuestos actuales el valor del material no cubre el pago al EcoRider más los puntos. "
    "Las palancas (kg por recojo, rutas de varias paradas, tarifa) y su valor de equilibrio están en «Punto de equilibrio».",
    "Ingresos no probados: convenios municipales y estudios de mercado no tienen contrato; los estudios exigen consentimiento "
    "(Ley 29733) que hoy no se registra.",
    "Laboral: la RMV sube a S/ 1.300 en el primer semestre de 2027. Los regímenes MYPE exigen inscripción en el REMYPE. "
    "Con riders independientes, si hay subordinación SUNAFIL puede reconocer la relación laboral.",
    "Cuotas gratis de IA: techo medido de ~3 escáneres simultáneos; sin facturación activa un pico deja la cámara sin detectar.",
    "Precio de la IA: depende del modelo (tabla en «Costos variables»). Gemini 2.5 Flash (chat) se retira el 01-dic-2026.",
    "Vercel Hobby es solo para uso no comercial: hay que pasar a Pro antes de operar con dinero real.",
    "OpenStreetMap y OSRM públicos no tienen SLA y pueden bloquear sin aviso; los planes gratis de Stadia y MapTiler no permiten uso comercial.",
    "Supabase Free pausa los proyectos tras una semana sin actividad y tiene 5 GB de egress: no sirve para producción real.",
    "El SMTP por defecto de Supabase envía 2 correos por hora y solo al equipo: bloquea el registro con confirmación de correo.",
    "WhatsApp: Meta subió la tarifa de Perú el 01-oct-2026 sin monto confirmado aquí.",
    "Tipo de cambio: la infraestructura y la IA se pagan en dólares. IGV de 18 % sobre servicios digitales del exterior.",
    "Tarifa del EcoRider tomada de la memoria del proyecto, no reconfirmada en la base de datos.",
]:
    put(ws_src, f"A{r}", "• " + t, align=wrap)
    ws_src.merge_cells(f"A{r}:G{r}")
    ws_src.row_dimensions[r].height = 30
    r += 1
widths(ws_src, {"A": 6, "B": 30, "C": 42, "D": 18, "E": 50, "F": 12, "G": 70})
ws_src.freeze_panes = "A5"

for ws in wb.worksheets:
    ws.sheet_view.showGridLines = False
    ws.page_setup.orientation = "landscape"
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 0
    ws.sheet_properties.pageSetUpPr.fitToPage = True

from proyeccion import ampliar
ampliar(globals())
os.makedirs(os.path.dirname(OUT), exist_ok=True)
wb.save(OUT)
print("ok", OUT)
