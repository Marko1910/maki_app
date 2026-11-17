"""Ruma — costo del PROYECTO DE IMPLEMENTACIÓN según el diagrama de la profesora (2026-11-06).

Siete hojas, una por bloque del diagrama, de abajo hacia arriba:
  1 Recursos físicos (MD, MOD, CIF)   2 Software (MD, MOD, CIF)
  3 Gastos administrativos   4 Gastos de ventas   5 Gastos financieros   6 Utilidad   7 IGV y precio de venta
Las hojas 3–7 parten de los totales de 1 y 2. Todo número se calcula con fórmulas; las entradas editables
están en azul. Ejecutar desde la raíz del repo:  py -3 -X utf8 docs/costos/scripts/build_proyecto_profesora.py
"""
from pathlib import Path

from openpyxl import Workbook
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.workbook.defined_name import DefinedName

OUT = Path(__file__).resolve().parents[1] / "Ruma_costos_diagrama_profesora.xlsx"

# Colores del diagrama de la profesora.
COLOR = {"MD": "E3A817", "MOD": "5BC8E0", "CIF": "B5C99A", "GA": "E7E6E6", "GV": "B7E1EC",
         "GF": "E3D9BF", "UT": "D9A6E8", "IGV": "EFD32A", "TOT": "DDEBF7"}
BLUE = Font(color="1F4E9E", bold=True)
BOLD = Font(bold=True)
TITLE = Font(bold=True, size=14)
THIN = Side(style="thin", color="BFBFBF")
BOX = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
MONEY = '"S/ "#,##0.00'
HEAD = ["Código", "Concepto", "Unidad", "Cantidad", "Meses / veces", "Costo unitario", "Costo total", "Dato", "Criterio y fuente"]
WIDTH = [10, 48, 14, 11, 11, 16, 17, 13, 58]
DARK = "1F4E3D"
ZEBRA = PatternFill("solid", fgColor="F5F7F6")
INPUT = PatternFill("solid", fgColor="EAF1FB")
WHITE_BOLD = Font(bold=True, color="FFFFFF")

wb = Workbook()
wb.remove(wb.active)
names = {}


def sheet(title, heading, subtitle):
    ws = wb.create_sheet(title)
    for i, w in enumerate(WIDTH, start=1):
        ws.column_dimensions[chr(64 + i)].width = w
    ws["A1"] = heading
    ws["A1"].font = TITLE
    ws["A2"] = subtitle
    ws["A2"].alignment = Alignment(wrap_text=True, vertical="top")
    ws.merge_cells("A2:I2")
    ws.row_dimensions[1].height = 24
    ws.row_dimensions[2].height = 34
    ws.sheet_view.showGridLines = False
    ws.page_setup.orientation = "landscape"
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 0
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    return ws


def lines(text, width):
    """Rows a wrapped text needs in a column of [width] characters."""
    return max(1, -(-len(str(text or "")) // max(1, int(width * 1.15))))


def summary(ws, r, rows):
    """Small results box at the top so the reader sees the totals before the detail."""
    title(ws, r, "RESUMEN DE LA HOJA")
    r += 1
    for label, formula, key in rows:
        ws.cell(r, 2, label).font = BOLD
        cell = ws.cell(r, 4, formula)
        cell.number_format = MONEY
        cell.font = BOLD
        ws.merge_cells(start_row=r, start_column=4, end_row=r, end_column=5)
        for c in range(1, 6):
            ws.cell(r, c).fill = PatternFill("solid", fgColor=COLOR[key])
            ws.cell(r, c).border = BOX
        r += 1
    return r + 1


def title(ws, r, text):
    """Dark band that opens a block (parameters, summary, table)."""
    for c in range(1, 10):
        ws.cell(r, c).fill = PatternFill("solid", fgColor=DARK)
    ws.cell(r, 1, text).font = WHITE_BOLD
    ws.row_dimensions[r].height = 20
    return r + 1


def name(ws, key, cell):
    wb.defined_names[key] = DefinedName(key, attr_text=f"'{ws.title}'!${cell[0]}${cell[1:]}")
    names[key] = cell


def header(ws, r):
    for c, text in enumerate(HEAD, start=1):
        cell = ws.cell(r, c, text)
        cell.font = WHITE_BOLD
        cell.fill = PatternFill("solid", fgColor=DARK)
        cell.border = BOX
        cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center" if c > 2 else "left")
    ws.row_dimensions[r].height = 30
    return r + 1


def band(ws, r, text, key):
    r += 1  # air before each block of the diagram
    ws.cell(r, 1, text).font = Font(bold=True, size=12)
    for c in range(1, 10):
        ws.cell(r, c).fill = PatternFill("solid", fgColor=COLOR[key])
    ws.row_dimensions[r].height = 24
    ws.cell(r, 1).alignment = Alignment(vertical="center")
    return r + 1


def items(ws, r, rows):
    """rows: (code, concept, unit, qty, times, unit_cost, kind, note), or ("#", subgroup title) to open a group.
    Numbers in qty/times/unit_cost may be formulas (str starting with '='). Literal inputs are blue on a tinted cell.
    Returns (first, last) rows; SUM over that range skips the subgroup rows, which have no amount."""
    first = r
    zebra = False
    count = {}
    for row in rows:
        if row[0] == "#":
            ws.cell(r, 2, row[1]).font = Font(bold=True, italic=True, color=DARK)
            for c in range(1, 10):
                ws.cell(r, c).border = Border(bottom=Side(style="thin", color=DARK))
            ws.row_dimensions[r].height = 20
            r += 1
            zebra = False
            continue
        code, concept, unit, qty, times, cost, kind, note = row
        prefix = code.rsplit("-", 1)[0]
        count[prefix] = count.get(prefix, 0) + 1
        code = f"{prefix}-{count[prefix]:02d}"
        vals = [code, concept, unit, qty, times, cost, f"=D{r}*E{r}*F{r}", kind, note]
        for c, v in enumerate(vals, start=1):
            cell = ws.cell(r, c, v)
            cell.border = BOX
            cell.alignment = Alignment(wrap_text=c in (2, 9), vertical="center",
                                       horizontal="center" if c in (1, 3, 4, 5, 8) else None)
            if zebra:
                cell.fill = ZEBRA
            if c in (4, 5, 6) and not (isinstance(v, str) and v.startswith("=")):
                cell.font = BLUE
                cell.fill = INPUT
        for c in (4, 5):
            if ws.cell(r, c).number_format != MONEY:
                ws.cell(r, c).number_format = "General"
        ws.cell(r, 8).font = Font(size=9, color="595959")
        ws.cell(r, 9).font = Font(size=9, color="404040")
        ws.row_dimensions[r].height = 16 * max(lines(concept, WIDTH[1]), lines(note, WIDTH[8] * 1.1)) + 4
        zebra = not zebra
        # A rate applied to an amount (SCTR, ITF) reads as a percentage, not as rounded soles.
        ws.cell(r, 6).number_format = "0.000%" if unit.startswith(("%", "S/ pagados")) else MONEY
        if unit.startswith(("%", "S/ pagados")):
            ws.cell(r, 4).number_format = MONEY
        ws.cell(r, 7).number_format = MONEY
        r += 1
    return first, r - 1


def total(ws, r, label, formula, key, key_name=None, big=False):
    ws.cell(r, 2, label).font = Font(bold=True, size=12 if big else 11)
    cell = ws.cell(r, 7, formula)
    cell.font = Font(bold=True, size=12 if big else 11)
    cell.number_format = MONEY
    top = Side(style="medium" if big else "thin", color=DARK)
    for c in range(1, 10):
        ws.cell(r, c).fill = PatternFill("solid", fgColor=COLOR[key])
        ws.cell(r, c).border = Border(top=top, bottom=top, left=THIN, right=THIN)
        ws.cell(r, c).alignment = Alignment(vertical="center")
    ws.row_dimensions[r].height = 24 if big else 20
    if key_name:
        name(ws, key_name, f"G{r}")
    return r + 1


def note(ws, r, text):
    ws.cell(r, 1, text).alignment = Alignment(wrap_text=True, vertical="top")
    ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=9)
    ws.row_dimensions[r].height = 15 * max(1, len(text) // 150 + 1)
    return r + 1


def param(ws, r, label, value, unit, kind, src, key, fmt=None):
    ws.cell(r, 2, label).alignment = Alignment(wrap_text=True, vertical="center")
    cell = ws.cell(r, 4, value)
    editable = not (isinstance(value, str) and value.startswith("="))
    cell.font = BLUE if editable else BOLD
    if editable:
        cell.fill = INPUT
    cell.alignment = Alignment(horizontal="center")
    if fmt:
        cell.number_format = fmt
    ws.cell(r, 3, unit).alignment = Alignment(horizontal="center")
    ws.cell(r, 8, kind).font = Font(size=9, color="595959")
    ws.cell(r, 9, src).font = Font(size=9, color="404040")
    ws.cell(r, 9).alignment = Alignment(wrap_text=True, vertical="center")
    for c in range(1, 10):
        ws.cell(r, c).border = Border(bottom=Side(style="hair", color="BFBFBF"))
    ws.row_dimensions[r].height = 16 * max(lines(src, WIDTH[8] * 1.1), lines(label, WIDTH[1])) + 4
    name(ws, key, f"D{r}")
    return r + 1


# ---------------------------------------------------------------- 1. RECURSOS FÍSICOS
ws = sheet("1. Recursos físicos", "1. RECURSOS FÍSICOS — Materiales directos, mano de obra directa y CIF",
           "Proyecto de implementación de Ruma: montaje físico del piloto en un distrito para recolectar SOLO botellas PET y cartón "
           "(local, maquinaria de acopio, EPP, materiales y personal de campo). Los tres recuadros inferiores del diagrama. Azul = dato editable. "
           "Los parámetros del proyecto están aquí y los usan todas las hojas.")
r = summary(ws, 4, [("Materiales directos (MD)", "=F_MD", "MD"), ("Mano de obra directa (MOD)", "=F_MOD", "MOD"),
                     ("Costo primo = MD + MOD", "=F_PRIMO", "TOT"), ("Costos indirectos de fabricación (CIF)", "=F_CIF", "CIF"),
                     ("COSTO DE PRODUCCIÓN FÍSICO", "=F_PROD", "TOT")])
r = title(ws, r, "PARÁMETROS DEL PROYECTO — los usan todas las hojas (azul = editable)")
r = param(ws, r, "Duración del proyecto (desarrollo + piloto)", 6, "meses", "SUPUESTO", "Calendario del proyecto; el equipo de software trabaja todos los meses.", "MESES")
r = param(ws, r, "Meses de piloto con operación física", 2, "meses", "SUPUESTO", "Los últimos meses del proyecto: riders, acopio y local operan solo en el piloto.", "PILOTO")
r = param(ws, r, "Recojos por mes en el piloto", 360, "recojos", "SUPUESTO", "Piloto distrital del libro de inversionistas (180 recolectores activos × 2 recojos).", "RECOJOS")
r = param(ws, r, "Kilos por recojo (solo PET y cartón)", 3, "kg", "SUPUESTO", "Promedio del libro de inversionistas.", "KG_RECOJO")
r = param(ws, r, "Peso de un fardo prensado", 50, "kg", "SUPUESTO", "Fardo pequeño de prensa vertical; cotizar según la prensa.", "KG_FARDO")
r = param(ws, r, "Recolectores inscritos en el piloto", 180, "personas", "SUPUESTO", "Reciben su kit de bolsas al inscribirse.", "RECOLECTORES")
r = param(ws, r, "Tipo de cambio", 3.452, "S/ por US$", "VERIFICADO", "SUNAT, venta del 06-nov-2026 (libro de inversionistas, fuente F1).", "TC", "0.000")
r = param(ws, r, "Remuneración mínima vital (RMV)", 1230, "S/ al mes", "VERIFICADO", "D.S. 015-2026-TR, vigente desde el 01-oct-2026 (libro de inversionistas, F24).", "RMV", MONEY)
ws.cell(r, 2, "Cargas laborales — régimen pequeña empresa (REMYPE), sobre el sueldo").font = Font(bold=True, italic=True, color=DARK)
ws.row_dimensions[r].height = 20
r += 1
r = param(ws, r, "  EsSalud del empleador", 0.09, "% del sueldo", "VERIFICADO", "9 % (F26).", "C_ESSALUD", "0.00%")
r = param(ws, r, "  Gratificación: 1 sueldo al año + 9 % de bonificación (Ley 30334)", "=1/12*(1+C_ESSALUD)", "% mensual", "FÓRMULA", "Media gratificación en julio y media en diciembre (F28).", "C_GRATI", "0.00%")
r = param(ws, r, "  CTS: 15 días de sueldo al año", "=15/30/12", "% mensual", "FÓRMULA", "REMYPE pequeña empresa (F28).", "C_CTS", "0.00%")
r = param(ws, r, "  Seguro Vida Ley", 0.005, "% del sueldo", "SUPUESTO", "Prima a cotizar.", "C_VIDA", "0.00%")
r = param(ws, r, "Factor de costo del empleador (1 + cargas)", "=1+C_ESSALUD+C_GRATI+C_CTS+C_VIDA", "factor", "FÓRMULA", "Las vacaciones (15 días) se pagan dentro de los 12 sueldos.", "CARGAS", "0.0000")
r += 2
r = header(ws, r)

r = band(ws, r, "MATERIALES DIRECTOS (MD) — se consumen en cada recojo del piloto", "MD")
md = items(ws, r, [
    ("MDF-01", "Costales de rafia para el material recolectado", "costal", "=RECOJOS", "=PILOTO", 0.5, "SUPUESTO", "Uno por recojo. No se halló precio unitario peruano publicado; cotizar por millar."),
    ("MDF-02", "Precintos / etiquetas con código del recojo", "unidad", "=RECOJOS", "=PILOTO", 0.1, "SUPUESTO", "Identifican cada costal hasta el pesaje en el acopio."),
    ("MDF-03", "Kit de bolsas de separación para el recolector", "kit", "=RECOLECTORES", 1, 1.2, "SUPUESTO", "Bolsas de colores por material, una vez por recolector inscrito."),
])
r = md[1] + 1
r = total(ws, r, "TOTAL MATERIALES DIRECTOS FÍSICOS", f"=SUM(G{md[0]}:G{md[1]})", "MD", "F_MD")

r = band(ws, r, "MANO DE OBRA DIRECTA (MOD) — personal que hace la recolección y el pesaje", "MOD")
mod = items(ws, r, [
    ("MODF-01", "EcoRider en planilla (recolección y transporte)", "persona/mes", 2, "=PILOTO", "=1500*CARGAS", "SUPUESTO", "Sueldo S/ 1.500 (≥ RMV) × factor del empleador. En operación normal se paga por recojo; en el piloto se contrata para controlar la calidad."),
    ("MODF-02", "Operario de acopio (recepción, clasificación y pesaje)", "persona/mes", 1, "=PILOTO", "=RMV*CARGAS", "SUPUESTO", "RMV × factor del empleador."),
])
r = mod[1] + 1
r = total(ws, r, "TOTAL MANO DE OBRA DIRECTA FÍSICA", f"=SUM(G{mod[0]}:G{mod[1]})", "MOD", "F_MOD")
r = total(ws, r, "COSTO PRIMO FÍSICO = MD + MOD", "=F_MD+F_MOD", "TOT", "F_PRIMO")

r = band(ws, r, "COSTOS INDIRECTOS DE FABRICACIÓN (CIF) — local, maquinaria, EPP y apoyo", "CIF")
cif = items(ws, r, [
    ("#", "Mano de obra indirecta"),
    ("CIFF-01", "Supervisor de operaciones (mano de obra indirecta)", "persona/mes", 1, "=PILOTO", "=2500*CARGAS", "SUPUESTO", "Coordina riders, rutas y acopio. Sueldo S/ 2.500 × factor del empleador."),
    ("#", "Local"),
    ("CIFF-02", "Alquiler del local (almacén y punto de acopio)", "mes", 1, "=PILOTO", 1500, "SUPUESTO", "Local pequeño en Trujillo; cotizar. La garantía no es costo: se devuelve."),
    ("CIFF-03", "Adecuación del local (estantería, pintura, señalización)", "global", 1, 1, 1200, "SUPUESTO", "Una vez al inicio del piloto."),
    ("CIFF-04", "Luz y agua del local", "mes", 1, "=PILOTO", 180, "SUPUESTO", ""),
    ("#", "Maquinaria y equipo — depreciación por los meses de uso"),
    ("CIFF-05", "Depreciación: balanza de plataforma 300 kg", "unidad/mes", 1, "=PILOTO", "=239*0.10/12", "REFERENCIA", "Compra S/ 239 (Ventus B-300A, Sodimac Perú, F31); maquinaria 10 % anual (SUNAT, F30). Solo se carga el tiempo del piloto."),
    ("CIFF-06", "Depreciación: balanzas colgantes 50 kg (riders)", "unidad/mes", 2, "=PILOTO", "=60*0.10/12", "SUPUESTO", "Compra S/ 60 c/u; maquinaria 10 % anual."),
    ("CIFF-07", "Depreciación: triciclos de carga", "unidad/mes", 2, "=PILOTO", "=1800*0.20/12", "SUPUESTO", "Compra S/ 1.800 c/u; vehículos de transporte terrestre 20 % anual (SUNAT)."),
    ("CIFF-08", "Depreciación: carretilla", "unidad/mes", 1, "=PILOTO", "=220*0.10/12", "SUPUESTO", "Compra S/ 220; 10 % anual."),
    ("CIFF-09", "Depreciación: contenedores de 120 L para puntos de acopio", "unidad/mes", 10, "=PILOTO", "=150*0.10/12", "SUPUESTO", "Compra S/ 150 c/u; 10 % anual."),
    ("CIFF-09", "Depreciación: prensa enfardadora vertical para PET y cartón", "unidad/mes", 1, "=PILOTO", "=12000*0.10/12", "SUPUESTO", "Compacta botellas y cartón en fardos: el comprador paga mejor por kilo y el local guarda varias veces más material. Precio a cotizar con distribuidores peruanos; referencia: prensa vertical en Brasil desde R$ 19.900 (mfrural.com.br, 2026). Maquinaria 10 % anual."),
    ("CIFF-09", "Depreciación: transpaleta manual 2,5 t (mover fardos)", "unidad/mes", 1, "=PILOTO", "=1100*0.10/12", "SUPUESTO", "Referencia: entre CLP 217.990 y 517.990 en Sodimac/Falabella Chile; cotizar en Perú. 10 % anual."),
    ("CIFF-09", "Depreciación: mesa de clasificación", "unidad/mes", 1, "=PILOTO", "=400*0.10/12", "SUPUESTO", "Separar PET y cartón y retirar lo que no se recicla. 10 % anual."),
    ("CIFF-10", "Depreciación: teléfonos de los riders (app)", "unidad/mes", 2, "=PILOTO", "=900*0.25/12", "SUPUESTO", "Compra S/ 900 c/u; equipos de procesamiento de datos 25 % anual (SUNAT, F30)."),
    ("#", "Insumos del acopio (PET y cartón)"),
    ("CIFF-11", "Sacas grandes (big bags) para material suelto", "unidad", 10, 1, 35, "SUPUESTO", "Guardan PET y cartón antes de prensar; vida útil de varios meses."),
    ("CIFF-11", "Alambre o zuncho para amarrar fardos", "fardo", "=RECOJOS*KG_RECOJO/KG_FARDO", "=PILOTO", 2, "SUPUESTO", "Un amarre por fardo; los fardos salen de los kilos recolectados."),
    ("#", "Mantenimiento, energía y comunicaciones"),
    ("CIFF-11", "Energía y mantenimiento de la prensa (aceite hidráulico)", "mes", 1, "=PILOTO", 80, "SUPUESTO", ""),
    ("CIFF-11", "Mantenimiento de triciclos", "unidad/mes", 2, "=PILOTO", 40, "SUPUESTO", ""),
    ("CIFF-12", "Plan de datos móviles de los riders", "línea/mes", 2, "=PILOTO", 50, "SUPUESTO", ""),
    ("#", "Equipos de protección personal (EPP)"),
    ("CIFF-13", "EPP: chaleco reflectivo", "unidad", 3, 1, 59.9, "REFERENCIA", "Falabella / Sodimac Perú (F32). 2 riders + 1 operario."),
    ("CIFF-14", "EPP: guantes de trabajo", "par/mes", 3, "=PILOTO", 15, "SUPUESTO", "Un par por persona al mes."),
    ("CIFF-15", "EPP: botas de seguridad", "par", 3, 1, 90, "SUPUESTO", ""),
    ("CIFF-16", "EPP: casco de ciclista", "unidad", 2, 1, 70, "SUPUESTO", "Riders en triciclo."),
    ("CIFF-17", "EPP: mascarillas (caja de 50)", "caja/mes", 1, "=PILOTO", 25, "SUPUESTO", ""),
    ("CIFF-18", "EPP: poncho impermeable", "unidad", 3, 1, 35, "SUPUESTO", ""),
    ("#", "Seguridad y seguros"),
    ("CIFF-19", "Seguridad del local: extintor PQS 6 kg, botiquín y señalización", "global", 1, 1, 260, "SUPUESTO", "Requisitos usuales de la inspección ITSE."),
    ("CIFF-20", "SCTR del personal de campo", "% de sueldos", f"=1500*2+RMV", "=PILOTO", 0.015, "SUPUESTO", "1,5 % de los sueldos de riders y operario; cotizar y confirmar si la actividad lo exige."),
])
r = cif[1] + 1
r = total(ws, r, "TOTAL CIF FÍSICOS", f"=SUM(G{cif[0]}:G{cif[1]})", "CIF", "F_CIF")
r = total(ws, r, "COSTO DE PRODUCCIÓN FÍSICO = COSTO PRIMO + CIF", "=F_PRIMO+F_CIF", "TOT", "F_PROD", big=True)
r += 1
r = note(ws, r, "Criterio: la maquinaria y los equipos duraderos entran como depreciación por el tiempo que se usan en el proyecto (tasas máximas de SUNAT); la compra completa es inversión en activo fijo, no costo. Los EPP y consumibles de vida corta entran completos.")
r += 1
r = total(ws, r, "Referencia: compra de maquinaria y equipos físicos (inversión en activo fijo, no es costo)",
          "=239+2*60+2*1800+220+10*150+12000+1100+400+2*900", "GA", "F_INVERSION")

# ---------------------------------------------------------------- 2. SOFTWARE
ws = sheet("2. Software", "2. SOFTWARE — Materiales directos, mano de obra directa y CIF del desarrollo",
           "Desarrollo de la app Android, la PWA para iPhone, el panel del acopio y el backend en Supabase durante los meses "
           "del proyecto. Materiales directos = recursos digitales que pasan a formar parte del producto (cuentas, dominios, "
           "nube y APIs). Mano de obra directa = quienes programan y diseñan. Azul = dato editable.")
r = summary(ws, 4, [("Materiales directos (MD)", "=S_MD", "MD"), ("Mano de obra directa (MOD)", "=S_MOD", "MOD"),
                     ("Costo primo = MD + MOD", "=S_PRIMO", "TOT"), ("Costos indirectos de fabricación (CIF)", "=S_CIF", "CIF"),
                     ("COSTO DE PRODUCCIÓN SOFTWARE", "=S_PROD", "TOT")])
r = header(ws, r)
r = band(ws, r, "MATERIALES DIRECTOS (MD) — recursos digitales que forman parte del producto", "MD")
mds = items(ws, r, [
    ("#", "Cuentas y dominios"),
    ("MDS-01", "Cuenta de desarrollador de Google Play (pago único)", "cuenta", 1, 1, "=25*TC", "VERIFICADO", "US$ 25 (F19). La PWA de iPhone no requiere Apple Developer Program."),
    ("MDS-02", "Dominio .pe (un año)", "dominio", 1, 1, 150, "REFERENCIA", "S/ 110–150 según registrador (F21)."),
    ("MDS-03", "Dominio .com (un año)", "dominio", 1, 1, "=11.17*TC", "REFERENCIA", "Tarifa mayorista + ICANN (F20)."),
    ("#", "Nube: base de datos y hosting"),
    ("MDS-04", "Supabase Pro: base de datos, autenticación y funciones", "mes", 1, "=MESES", "=25*TC", "VERIFICADO", "US$ 25 al mes con US$ 10 de cómputo incluidos (F3)."),
    ("MDS-05", "Supabase: segundo proyecto (staging) en instancia Micro", "mes", 1, "=MESES", "=10*TC", "VERIFICADO", "US$ 10 al mes (F3). Staging y producción separados."),
    ("MDS-06", "Vercel Pro: PWA y panel web", "asiento/mes", 1, "=MESES", "=20*TC", "VERIFICADO", "US$ 20 por asiento (F9). Hobby no permite uso comercial."),
    ("#", "APIs del piloto: mapas e inteligencia artificial"),
    ("MDS-07", "Mapas y rutas (Stadia Maps Starter)", "mes", 1, "=PILOTO", "=20*TC", "VERIFICADO", "US$ 20 al mes, uso comercial (F12). Solo durante el piloto."),
    ("MDS-08", "IA de visión: fotos analizadas en el piloto", "foto", "=RECOJOS*4", "=PILOTO", "=0.0067*TC/2", "REFERENCIA", "US$ 0,0067 por escaneo de ~2 fotos con Gemini Flash-Lite (libro de inversionistas, F6). 4 fotos por recojo."),
    ("MDS-09", "IA del asistente (chat) en el piloto", "mensaje", "=RECOJOS*1.5", "=PILOTO", 0.0066, "REFERENCIA", "S/ 0,0066 por mensaje (libro de inversionistas, F6)."),
])
r = mds[1] + 1
r = total(ws, r, "TOTAL MATERIALES DIRECTOS SOFTWARE", f"=SUM(G{mds[0]}:G{mds[1]})", "MD", "S_MD")

r = band(ws, r, "MANO DE OBRA DIRECTA (MOD) — equipo que construye el software", "MOD")
mods = items(ws, r, [
    ("MODS-01", "Desarrollador Android (Kotlin / Compose)", "persona/mes", 1, "=MESES", "=4500*CARGAS", "SUPUESTO", "Sueldo S/ 4.500 × factor del empleador. Referencias de mercado en el libro de inversionistas (F33)."),
    ("MODS-02", "Desarrollador web y backend (PWA, panel, Supabase)", "persona/mes", 1, "=MESES", "=4500*CARGAS", "SUPUESTO", "Ídem."),
    ("MODS-03", "Diseñador UX/UI", "persona/mes", 1, 3, "=3000*CARGAS", "SUPUESTO", "Primeros 3 meses (diseño en Stitch y pruebas con usuarios)."),
    ("MODS-04", "Tester / QA", "persona/mes", 1, 2, "=2200*CARGAS", "SUPUESTO", "Últimos 2 meses, en el piloto."),
])
r = mods[1] + 1
r = total(ws, r, "TOTAL MANO DE OBRA DIRECTA SOFTWARE", f"=SUM(G{mods[0]}:G{mods[1]})", "MOD", "S_MOD")
r = total(ws, r, "COSTO PRIMO SOFTWARE = MD + MOD", "=S_MD+S_MOD", "TOT", "S_PRIMO")

r = band(ws, r, "COSTOS INDIRECTOS DE FABRICACIÓN (CIF) — equipos, herramientas y apoyo al desarrollo", "CIF")
cifs = items(ws, r, [
    ("#", "Mano de obra indirecta"),
    ("CIFS-01", "Jefe de proyecto / product owner (medio tiempo)", "persona/mes", 0.5, "=MESES", "=4000*CARGAS", "SUPUESTO", "Mano de obra indirecta del desarrollo."),
    ("#", "Equipos — depreciación por los meses de uso"),
    ("CIFS-02", "Depreciación: laptops de desarrollo", "unidad/mes", 4, "=MESES", "=3500*0.25/12", "SUPUESTO", "Compra S/ 3.500 c/u; equipos de procesamiento de datos 25 % anual (SUNAT, F30)."),
    ("CIFS-03", "Depreciación: teléfonos de prueba (Android + iPhone)", "unidad/mes", 2, "=MESES", "=1450*0.25/12", "SUPUESTO", "Promedio S/ 1.450 (un Android S/ 900 y un iPhone usado S/ 2.000); 25 % anual."),
    ("#", "Herramientas de desarrollo y diseño"),
    ("CIFS-04", "Herramientas de IA para programar (Claude Pro)", "suscripción/mes", 1, "=MESES", "=20*TC", "VERIFICADO", "US$ 20 al mes (F7)."),
    ("CIFS-05", "Herramientas de IA para programar (ChatGPT Plus / Codex)", "suscripción/mes", 1, "=MESES", "=20*TC", "REFERENCIA", "US$ 20 al mes (F22)."),
    ("CIFS-06", "Diseño de pantallas (Google Stitch)", "mes", 1, "=MESES", 0, "REFERENCIA", "Gratis mientras siga en Google Labs (F23)."),
    ("#", "Servicios y otros"),
    ("CIFS-07", "Internet del equipo de desarrollo", "mes", 1, "=MESES", 120, "SUPUESTO", ""),
    ("CIFS-08", "Energía eléctrica de los equipos", "mes", 1, "=MESES", 80, "SUPUESTO", ""),
    ("CIFS-09", "Pruebas de campo con usuarios (movilidad y refrigerios)", "jornada", 4, 1, 80, "SUPUESTO", ""),
    ("CIFS-10", "Capacitación del equipo", "global", 1, 1, 300, "SUPUESTO", ""),
])
r = cifs[1] + 1
r = total(ws, r, "TOTAL CIF SOFTWARE", f"=SUM(G{cifs[0]}:G{cifs[1]})", "CIF", "S_CIF")
r = total(ws, r, "COSTO DE PRODUCCIÓN SOFTWARE = COSTO PRIMO + CIF", "=S_PRIMO+S_CIF", "TOT", "S_PROD", big=True)
r += 1
r = note(ws, r, "Criterio: el hosting y las APIs se tratan como materiales directos del software porque se incorporan al producto que se entrega; si la profesora prefiere tratarlos como CIF, basta mover las filas MDS-04 a MDS-09 al bloque CIF: los totales de producción no cambian.")


# ---------------------------------------------------------------- helper for sheets 3–7
def base_block(ws, r):
    ws.cell(r, 1, "PUNTO DE PARTIDA — hojas 1 y 2").font = BOLD
    r += 1
    for label, f in [("Costo de producción físico (hoja 1)", "=F_PROD"), ("Costo de producción software (hoja 2)", "=S_PROD")]:
        ws.cell(r, 2, label)
        ws.cell(r, 7, f).number_format = MONEY
        r += 1
    return total(ws, r, "COSTO DE PRODUCCIÓN DEL PROYECTO", "=F_PROD+S_PROD", "TOT")


# ---------------------------------------------------------------- 3. GASTOS ADMINISTRATIVOS
ws = sheet("3. Gastos administrativos", "3. GASTOS ADMINISTRATIVOS",
           "Dirección y administración de la empresa durante el proyecto. No forman parte del costo de producción.")
r = base_block(ws, 4) + 1
r = header(ws, r)
r = band(ws, r, "GASTOS ADMINISTRATIVOS", "GA")
ga = items(ws, r, [
    ("GA-01", "Gerente general (fundador)", "persona/mes", 1, "=MESES", "=4000*CARGAS", "SUPUESTO", "Hoy los fundadores no cobran; se valoriza para un costo completo."),
    ("GA-02", "Contador externo", "mes", 1, "=MESES", 400, "SUPUESTO", ""),
    ("GA-03", "Constitución de la empresa (SAC por SID-SUNARP + notaría)", "global", 1, 1, 900, "REFERENCIA", "S/ 840–900 por vía digital (F34)."),
    ("GA-04", "Licencia de funcionamiento municipal e inspección ITSE", "global", 1, 1, 400, "SUPUESTO", "Según el TUPA del distrito del local."),
    ("GA-05", "Registro de la marca Ruma (INDECOPI)", "clase", 1, 1, 535, "SUPUESTO", "Tasa referencial por una clase; verificar en INDECOPI."),
    ("GA-06", "Asesoría legal: términos de uso y protección de datos (Ley 29733)", "global", 1, 1, 1500, "SUPUESTO", "Incluye inscribir los bancos de datos ante la autoridad."),
    ("GA-07", "Útiles de oficina", "mes", 1, "=MESES", 60, "SUPUESTO", ""),
    ("GA-08", "Facturación electrónica y software administrativo", "mes", 1, "=MESES", 50, "SUPUESTO", ""),
])
r = ga[1] + 1
r = total(ws, r, "TOTAL GASTOS ADMINISTRATIVOS", f"=SUM(G{ga[0]}:G{ga[1]})", "GA", "T_GA", big=True)

# ---------------------------------------------------------------- 4. GASTOS DE VENTAS
ws = sheet("4. Gastos de ventas", "4. GASTOS DE VENTAS",
           "Lanzamiento del piloto: captar recolectores y presentar Ruma a la municipalidad y a los acopios.")
r = base_block(ws, 4) + 1
r = header(ws, r)
r = band(ws, r, "GASTOS DE VENTAS", "GV")
gv = items(ws, r, [
    ("GV-01", "Publicidad en redes sociales", "mes", 1, "=PILOTO", 600, "SUPUESTO", "Campaña de inscripción durante el piloto."),
    ("GV-02", "Community manager (medio tiempo)", "mes", 1, "=PILOTO", 1200, "SUPUESTO", "Por recibo por honorarios."),
    ("GV-03", "Material impreso (volantes y afiches)", "global", 1, 1, 400, "SUPUESTO", ""),
    ("GV-04", "Bolsas reutilizables con la marca para recolectores", "unidad", "=RECOLECTORES", 1, 3, "SUPUESTO", ""),
    ("GV-05", "Evento de lanzamiento con la municipalidad", "global", 1, 1, 800, "SUPUESTO", ""),
    ("GV-06", "Movilidad para visitas a municipalidades y acopios", "mes", 1, "=MESES", 150, "SUPUESTO", ""),
])
r = gv[1] + 1
r = total(ws, r, "TOTAL GASTOS DE VENTAS", f"=SUM(G{gv[0]}:G{gv[1]})", "GV", "T_GV", big=True)

# ---------------------------------------------------------------- 5. GASTOS FINANCIEROS
ws = sheet("5. Gastos financieros", "5. GASTOS FINANCIEROS",
           "Costos bancarios del proyecto y, si se financia con deuda, sus intereses. Ruma no tiene préstamo: el monto está en 0 "
           "y se puede editar para simular uno.")
r = base_block(ws, 4) + 1
ws.cell(r, 2, "Gastos administrativos + de ventas (hojas 3 y 4)")
ws.cell(r, 7, "=T_GA+T_GV").number_format = MONEY
r += 2
ws.cell(r, 1, "PRÉSTAMO (opcional)").font = BOLD
r += 1
r = param(ws, r, "Monto del préstamo", 0, "S/", "SUPUESTO", "0 = el proyecto se financia con aportes de los socios.", "PRESTAMO", MONEY)
r = param(ws, r, "Tasa efectiva anual (TEA)", 0.30, "% anual", "SUPUESTO", "Referencial para crédito MYPE; cotizar.", "TEA", "0.00%")
r += 1
r = header(ws, r)
r = band(ws, r, "GASTOS FINANCIEROS", "GF")
gf = items(ws, r, [
    ("GF-01", "Mantenimiento de la cuenta corriente de la empresa", "mes", 1, "=MESES", 15, "SUPUESTO", ""),
    ("GF-02", "Comisiones por transferencias (planilla y proveedores)", "mes", 1, "=MESES", 30, "SUPUESTO", ""),
    ("GF-03", "ITF: 0,005 % de lo que se paga por banco", "S/ pagados", "=F_PROD+S_PROD+T_GA+T_GV", 1, 0.00005, "VERIFICADO", "Impuesto a las Transacciones Financieras, tasa 0,005 % (Ley 28194)."),
    ("GF-04", "Intereses del préstamo durante el proyecto", "S/", "=PRESTAMO", 1, "=(1+TEA)^(MESES/12)-1", "FÓRMULA", "Monto × ((1 + TEA)^(meses/12) − 1). Cero si no hay préstamo."),
])
r = gf[1] + 1
r = total(ws, r, "TOTAL GASTOS FINANCIEROS", f"=SUM(G{gf[0]}:G{gf[1]})", "GF", "T_GF", big=True)

# ---------------------------------------------------------------- 6. UTILIDAD
ws = sheet("6. Utilidad", "6. UTILIDAD — del costo total al valor de venta",
           "Costo total = costo de producción + gastos administrativos + gastos de ventas + gastos financieros. "
           "La utilidad es un margen que se fija sobre el costo total; no es una ganancia ya obtenida.")
r = 4
r = header(ws, r)
rows = [
    ("Materiales directos (físico + software)", "=F_MD+S_MD", "MD"),
    ("Mano de obra directa (físico + software)", "=F_MOD+S_MOD", "MOD"),
    ("COSTO PRIMO = MD + MOD", "=F_PRIMO+S_PRIMO", "TOT"),
    ("Costos indirectos de fabricación (físico + software)", "=F_CIF+S_CIF", "CIF"),
    ("COSTO DE PRODUCCIÓN = COSTO PRIMO + CIF", "=F_PROD+S_PROD", "TOT"),
    ("Gastos administrativos (hoja 3)", "=T_GA", "GA"),
    ("Gastos de ventas (hoja 4)", "=T_GV", "GV"),
    ("Gastos financieros (hoja 5)", "=T_GF", "GF"),
]
for label, f, k in rows:
    r = total(ws, r, label, f, k)
r = total(ws, r, "COSTO TOTAL", "=F_PROD+S_PROD+T_GA+T_GV+T_GF", "TOT", "T_TOTAL", big=True)
r += 1
r = param(ws, r, "Margen de utilidad sobre el costo total", 0.20, "% del costo", "SUPUESTO", "Dato didáctico editable: la profesora o el equipo deben fijarlo.", "MARGEN", "0.00%")
r += 1
r = total(ws, r, "UTILIDAD = COSTO TOTAL × MARGEN", "=T_TOTAL*MARGEN", "UT", "T_UT", big=True)
r = total(ws, r, "VALOR DE VENTA (sin IGV) = COSTO TOTAL + UTILIDAD", "=T_TOTAL+T_UT", "TOT", "T_VV", big=True)

# ---------------------------------------------------------------- 7. IGV Y PRECIO DE VENTA
ws = sheet("7. IGV y precio de venta", "7. IGV Y PRECIO DE VENTA — escalera completa del diagrama",
           "Precio de venta = valor de venta + IGV. El IGV (18 % = 16 % IGV + 2 % IPM) se cobra al cliente y se paga a SUNAT: "
           "no es costo ni ganancia de Ruma. 'Precio de venta' es lo que costaría el proyecto de implementación si se vendiera "
           "llave en mano, por ejemplo a una municipalidad.")
r = 4
r = param(ws, r, "Tasa del IGV", 0.18, "%", "VERIFICADO", "SUNAT: 16 % IGV + 2 % Impuesto de Promoción Municipal = 18 %. https://orientacion.sunat.gob.pe/3053-concepto-tasa-y-operaciones-gravadas-igv-empresas", "IGV", "0.00%")
r += 1
ws.cell(r, 2, "Valor de venta (hoja 6)").font = BOLD
ws.cell(r, 7, "=T_VV").number_format = MONEY
r += 1
r = total(ws, r, "IGV = VALOR DE VENTA × 18 %", "=T_VV*IGV", "IGV", "T_IGV", big=True)
r = total(ws, r, "PRECIO DE VENTA (con IGV) = VALOR DE VENTA + IGV", "=T_VV+T_IGV", "TOT", "T_PV", big=True)
r += 1
ws.cell(r, 1, "ESCALERA DEL DIAGRAMA (de arriba hacia abajo)").font = Font(bold=True, size=12)
r += 1
ladder = [
    ("PRECIO DE VENTA", "=T_PV", "IGV"), ("   IGV", "=T_IGV", "IGV"),
    ("VALOR DE VENTA", "=T_VV", "UT"), ("   Utilidad", "=T_UT", "UT"),
    ("COSTO TOTAL", "=T_TOTAL", "TOT"), ("   Gastos financieros", "=T_GF", "GF"),
    ("   Gastos de ventas", "=T_GV", "GV"), ("   Gastos administrativos", "=T_GA", "GA"),
    ("COSTO DE PRODUCCIÓN", "=F_PROD+S_PROD", "TOT"), ("   Costos indirectos de fabricación", "=F_CIF+S_CIF", "CIF"),
    ("COSTO PRIMO", "=F_PRIMO+S_PRIMO", "TOT"), ("   Mano de obra directa", "=F_MOD+S_MOD", "MOD"),
    ("   Materiales directos", "=F_MD+S_MD", "MD"),
]
for label, f, k in ladder:
    r = total(ws, r, label, f, k, big=not label.startswith("   "))
r += 1
ws.cell(r, 2, "Control: precio − (MD + MOD + CIF + gastos + utilidad + IGV) debe ser 0").font = BOLD
ws.cell(r, 7, "=T_PV-(F_MD+S_MD+F_MOD+S_MOD+F_CIF+S_CIF+T_GA+T_GV+T_GF+T_UT+T_IGV)").number_format = MONEY
r += 1
ws.cell(r, 2, "Inversión en maquinaria y equipos físicos (activo fijo, fuera del costo; ver hoja 1)")
ws.cell(r, 7, "=F_INVERSION").number_format = MONEY

wb.save(OUT)
print("guardado", OUT)
