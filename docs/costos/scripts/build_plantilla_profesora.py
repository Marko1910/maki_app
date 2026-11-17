"""Libro independiente de tres hojas, siguiendo el diagrama de la profesora.

Importa una foto de los supuestos del piloto; no modifica el libro de origen ni
crea vínculos externos. Regenerar reemplaza las ediciones del archivo de salida.
"""
from pathlib import Path
from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.comments import Comment
from openpyxl.worksheet.datavalidation import DataValidation
import json

ROOT=Path(__file__).resolve().parent.parent
SOURCE=ROOT/'Ruma_costos_2026-11.xlsx'
OUT=ROOT/'Ruma_costos_diagrama_profesora.xlsx'
src=load_workbook(SOURCE,data_only=True)
wb=Workbook(); wb.remove(wb.active)
physical=wb.create_sheet('1. Parte física')
software=wb.create_sheet('2. Parte software')
final=wb.create_sheet('3. Gastos y precio')
COLORS={'md':'E9B836','mod':'72C8D5','cif':'C4D3A0','admin':'DDE5E4','sales':'C8E8EE','finance':'E4DDBB','profit':'D9B5E6','tax':'EBDD31','total':'E3ECE7'}
MONEY='"S/" #,##0.00;[Red]("S/" #,##0.00);"S/" 0.00'
LINE=Border(bottom=Side(style='hair',color='C9D2CD'))
refs={}; provenance=[]

def get(sheet,cell):
    v=src[sheet][cell].value
    if not isinstance(v,(float,int)): raise ValueError((sheet,cell,v,'Recalcular primero el libro de origen'))
    return v

def write(ws,cell,value,editable=False,bold=False,fmt=None):
    c=ws[cell]; c.value=value
    c.font=Font(name='Calibri',size=11,bold=bold,color='1565C0' if editable else '18392B')
    c.alignment=Alignment(vertical='center',wrap_text=True)
    if fmt: c.number_format=fmt
    return c

def text(ws,r,msg,height=40):
    ws.merge_cells(start_row=r,start_column=1,end_row=r,end_column=7)
    write(ws,f'A{r}',msg); ws.row_dimensions[r].height=height

def section(ws,r,title,kind):
    ws.merge_cells(start_row=r,start_column=1,end_row=r,end_column=7)
    write(ws,f'A{r}',title,bold=True)
    for row in ws.iter_rows(min_row=r,max_row=r,max_col=7):
        for c in row: c.fill=PatternFill('solid',fgColor=COLORS[kind])
    ws.row_dimensions[r].height=27

def setup(ws,title,sub):
    text(ws,1,title,32); ws['A1'].font=Font(name='Calibri',size=18,bold=True,color='1F4E3D')
    text(ws,2,sub,42)
    text(ws,3,'BASE: piloto distrital mensual, soles. Azul = dato editable; negro/verde = cálculo. Son estimaciones heredadas del modelo, no cotizaciones nuevas.',38)
    widths={'A':9,'B':43,'C':13,'D':18,'E':19,'F':20,'G':67}
    for c,width in widths.items(): ws.column_dimensions[c].width=width
    ws.sheet_view.showGridLines=False; ws.sheet_view.zoomScale=85
    ws.freeze_panes='C12'
    ws.sheet_properties.pageSetUpPr.fitToPage=True
    ws.page_setup.orientation='landscape'; ws.page_setup.paperSize=ws.PAPERSIZE_A3
    ws.page_setup.fitToWidth=1; ws.page_setup.fitToHeight=0
    ws.print_title_rows='1:3'
    ws.oddFooter.center.text='Ruma | Diagrama de costos de la profesora | 06/11/2026'
    ws.oddFooter.right.text='Página &P'

def table(ws,r):
    for col,label in zip('ABCDEFG',['Código','Concepto','Cantidad/mes','Unidad','Costo unitario S/','Costo mensual S/','Criterio / fuente / supuesto']):
        c=write(ws,f'{col}{r}',label,bold=True); c.fill=PatternFill('solid',fgColor='1F4E3D'); c.font=Font(name='Calibri',size=10,bold=True,color='FFFFFF')
    ws.row_dimensions[r].height=31

def item(ws,r,code,label,qty,unit,price,explanation,origin=None):
    vals=[code,label,qty,unit,price,f'=C{r}*E{r}',explanation]
    for c,v in zip('ABCDEFG',vals):
        cell=write(ws,f'{c}{r}',v,editable=c in ('C','E') and not isinstance(v,str),fmt=MONEY if c in ('E','F') else None)
        cell.border=LINE
    ws.row_dimensions[r].height=43
    if origin:
        s,loc=origin
        ws[f'E{r}'].comment=Comment(f'Importado de {SOURCE.name}, hoja {s}, celda {loc}. Corte 2026-11-06. Copia independiente; sin vínculo externo.','Ruma')
        provenance.append({'hoja':ws.title,'fila':r,'origen':f'{s}!{loc}'})
    return r

def total(ws,r,label,formula):
    ws.merge_cells(start_row=r,start_column=1,end_row=r,end_column=5)
    write(ws,f'A{r}',label,bold=True); write(ws,f'F{r}',formula,bold=True,fmt=MONEY)
    for c in 'ABCDEF': ws[f'{c}{r}'].fill=PatternFill('solid',fgColor=COLORS['total'])
    ws.row_dimensions[r].height=28
    return f"'{ws.title}'!F{r}"

setup(physical,'1 | PARTE FÍSICA','Los tres recuadros inferiores del diagrama: materiales directos, mano de obra directa y costos indirectos de fabricación (CIF), aplicados al servicio físico de Ruma.')
for r,label,value in [(4,'Recojos al mes',get('Escenarios','D15')),(5,'Recolectores activos al mes',get('Escenarios','D9')),(6,'EcoRiders',get('Escenarios','D11')),(7,'Kilogramos recolectados al mes',get('Escenarios','D17'))]:
    physical.merge_cells(start_row=r,start_column=1,end_row=r,end_column=5)
    write(physical,f'A{r}',label,bold=True); write(physical,f'F{r}',value,editable=True,fmt='0.00'); physical.row_dimensions[r].height=24
text(physical,8,'Escenario heredado: 300 registrados, 180 activos, 360 recojos y 1.080 kg/mes. Cantidades editables; no es el escenario de crecimiento de la proyección de inversionistas.',38)
table(physical,10)
section(physical,11,'1. MATERIALES DIRECTOS (MD)','md')
item(physical,12,'MD-01','Material reciclable adquirido a las familias','=F4','recojo',get('Costos variables','D16'),'Pago en puntos canjeables: adquisición del material. S/ 0,996 por recojo en este piloto.',('Costos variables','D16'))
refs['physical_md']=total(physical,13,'TOTAL MATERIALES DIRECTOS','=SUM(F12:F12)')
section(physical,15,'2. MANO DE OBRA DIRECTA (MOD)','mod')
item(physical,16,'MOD-01','EcoRider independiente: servicio de recojo','=F4','recojo',get('Costos variables','D17'),'S/ 3,10 por recojo supuesto con 2 km. No sumar además un sueldo por la misma labor.',('Costos variables','D17'))
item(physical,17,'MOD-02','Auxiliar de acopio: recepción y pesaje',0,'persona/mes',get('Mano de obra','C23')*1.005+15,'En el piloto pesa el acopio aliado: 0 personas a cargo de Ruma. Tarifa cargada según supuesto laboral del piloto.',('Mano de obra','H23'))
refs['physical_mod']=total(physical,18,'TOTAL MANO DE OBRA DIRECTA','=SUM(F16:F17)')
refs['physical_prime']=total(physical,19,'COSTO PRIMO FÍSICO = MD + MOD','=F13+F18')
section(physical,21,'3. COSTOS INDIRECTOS DE FABRICACIÓN / SERVICIO (CIF)','cif')
data=[
 ('Coordinación de operaciones',1,'persona/mes',get('Mano de obra','H20'),'Mano de obra indirecta: coordina riders y acopios. Costo empleador heredado.','Mano de obra','H20'),
 ('Bolsas o costales','=F4','recojo',get('Indirectos y gastos','D7'),'Material indirecto de recolección.','Indirectos y gastos','D7'),
 ('Etiquetas / precintos','=F4','recojo',get('Indirectos y gastos','D8'),'Material indirecto.','Indirectos y gastos','D8'),
 ('Volantes de separación','=F5','persona activa',get('Indirectos y gastos','D9'),'Instrucciones de operación; publicidad comercial está en hoja 3.','Indirectos y gastos','D9'),
 ('Chalecos: consumo mensual','=F6','rider',get('Indirectos y gastos','D10'),'Prorrateo mensual por vida útil; no sumar compra íntegra otra vez.','Indirectos y gastos','D10'),
 ('Uniformes: consumo mensual','=F6','rider',get('Indirectos y gastos','D11'),'Prorrateo mensual.','Indirectos y gastos','D11'),
 ('Guantes y otros EPP','=F6','rider',get('Indirectos y gastos','D12'),'Implementos de protección.','Indirectos y gastos','D12'),
 ('Depreciación de laptops',2,'equipo',get('Indirectos y gastos','D14'),'Hardware usado por software, presentado aquí por ser físico. No se duplica en hoja 2.','Indirectos y gastos','D14'),
 ('Depreciación de teléfonos de prueba',2,'equipo',get('Indirectos y gastos','D15'),'Hardware para pruebas; costo mensual, no precio de compra.','Indirectos y gastos','D15'),
 ('Depreciación de balanza',1,'equipo',get('Indirectos y gastos','D16'),'Activo de operación física.','Indirectos y gastos','D16'),
 ('Internet y telefonía',3,'línea',get('Indirectos y gastos','D18'),'Servicio compartido asignado una sola vez a la parte física.','Indirectos y gastos','D18'),
 ('Seguro de accidentes de riders','=F6','rider',get('Indirectos y gastos','D19'),'Supuesto de prima; pendiente de cotización.','Indirectos y gastos','D19'),
 ('Coworking',0,'puesto',get('Indirectos y gastos','D20'),'Piloto sin puestos contratados.','Indirectos y gastos','D20'),
]
for r,(label,qty,unit,price,why,s,c) in enumerate(data,22): item(physical,r,f'CIF-{r-21:02}',label,qty,unit,price,why,(s,c))
refs['physical_cif']=total(physical,35,'TOTAL CIF FÍSICOS','=SUM(F22:F34)')
refs['physical_total']=total(physical,37,'COSTO DE PRODUCCIÓN / SERVICIO FÍSICO','=F19+F35')
text(physical,39,'Criterio: se presenta el hardware en la hoja física aunque lo use el equipo de software. Este es un presupuesto mensual: las compras iniciales de equipos no se suman a su depreciación mensual.',45)
text(physical,40,'Personal: se conservan los costos empleador del piloto anterior (microempresa REMYPE como supuesto, sujeto a requisitos). No se está afirmando que Ruma ya esté inscrita ni que esos sueldos se estén pagando.',45)

setup(software,'2 | PARTE SOFTWARE','Los mismos tres recuadros del diagrama aplicados al software. Se distingue el trabajo directo de desarrollo de los servicios y herramientas que lo apoyan.')
text(software,4,'Material directo no es lo mismo que herramienta: el software no transforma materia prima física. Se deja MD en S/ 0; hosting, APIs y licencias están en CIF. Es una adaptación didáctica del esquema industrial.',48)
text(software,5,'El desarrollador se reclasifica como MOD del software bajo dedicación directa a Ruma. En el libro anterior figuraba como MOI; cambia la clasificación, no se añade un segundo sueldo.',42)
table(software,7)
section(software,8,'1. MATERIALES DIRECTOS (MD)','md')
item(software,9,'MD-01','Materia prima física incorporada al software',0,'unidad',0,'No aplica en este modelo. No inventar un material para llenar el recuadro.')
refs['software_md']=total(software,10,'TOTAL MATERIALES DIRECTOS SOFTWARE','=F9')
section(software,12,'2. MANO DE OBRA DIRECTA (MOD)','mod')
item(software,13,'MOD-01','Desarrollador full stack dedicado a Ruma',1,'persona/mes',get('Mano de obra','H19'),'Desarrollo y mantenimiento directo de Android, PWA y backend. Supuesto de dedicación completa; incluye cargas heredadas.',('Mano de obra','H19'))
refs['software_mod']=total(software,14,'TOTAL MANO DE OBRA DIRECTA SOFTWARE','=F13')
refs['software_prime']=total(software,15,'COSTO PRIMO SOFTWARE = MD + MOD','=F10+F14')
section(software,17,'3. COSTOS INDIRECTOS DE FABRICACIÓN / SERVICIO (CIF)','cif')
item(software,18,'CIF-01','Soporte y atención de la plataforma',.5,'persona/mes',get('Mano de obra','H21')/.5,'Medio puesto de soporte; se asigna aquí y no se duplica en físico ni administración.',('Mano de obra','H21'))
r=19
for sr in [7,8,9,10,11,12,14,16,17,18,20,21,22,24,25,27,28,29,30]:
    qty=get('Infraestructura',f'F{sr}'); value=get('Infraestructura',f'D{sr}')*get('Supuestos','C7')
    item(software,r,f'CIF-{r-17:02}',str(src['Infraestructura'][f'A{sr}'].value),qty,str(src['Infraestructura'][f'C{sr}'].value),value,
         'Copia de tarifa convertida a soles; revisar cotización. '+str(src['Infraestructura'][f'B{sr}'].value),('Infraestructura',f'I{sr}'))
    r+=1
infra_rows=list(range(19,r))
for sr in [7,8,9,10,11]:
    label=str(src['Costos variables'][f'A{sr}'].value)
    qty=get('Costos variables',f'G{sr}'); value=get('Costos variables',f'D{sr}')*get('Supuestos','C7')
    item(software,r,f'CIF-{r-17:02}',label,qty,str(src['Costos variables'][f'E{sr}'].value),value,
         'Consumo digital mensual estimado; cantidad y tarifa editables. No corresponde a materia prima física.',('Costos variables',f'J{sr}'))
    r+=1
tech_rows=list(range(r-5,r))
software.merge_cells(start_row=r,start_column=1,end_row=r,end_column=4)
write(software,f'A{r}','Tasa IGV compras digitales (solo si es costo no recuperable)',bold=True)
write(software,f'E{r}',.18,editable=True,fmt='0%')
write(software,f'G{r}','Supuesto conservado del libro base. Si corresponde crédito fiscal recuperable, cambiar esta tasa de COSTO a 0 %. El IGV de ventas se calcula aparte en hoja 3.')
software.row_dimensions[r].height=56
taxrow=r
taxbase='+'.join(f'F{j}' for j in infra_rows if j!=infra_rows[14])+ '+' + '+'.join(f'F{j}' for j in tech_rows)
# .pe es el ítem de la fila fuente 25, posición 14 en la lista, sin IGV adicional.
write(software,f'F{r}',f'=({taxbase})*E{r}',fmt=MONEY)
refs['software_cif']=total(software,r+2,'TOTAL CIF SOFTWARE',f'=SUM(F18:F{r})')
refs['software_total']=total(software,r+4,'COSTO DE PRODUCCIÓN / SERVICIO SOFTWARE',f'=F15+F{r+2}')
text(software,r+6,'No se repiten laptops, teléfonos, internet ni coworking: están en la hoja física. Las tarifas son una copia de los supuestos del piloto al 06/11/2026; el archivo no actualiza precios de internet automáticamente.',45)

setup(final,'3 | GASTOS, UTILIDAD E IGV','Desde gastos administrativos hasta IGV/IVA, como pidió la profesora. Se enlazan los costos físico y software para obtener costo total, valor de venta y precio de venta.')
for rr,label,value,fmt in [(5,'Recargo de utilidad deseada SOBRE EL COSTO',.20,'0%'),(6,'IGV total de la operación gravada',.18,'0%')]:
    final.merge_cells(start_row=rr,start_column=1,end_row=rr,end_column=4)
    write(final,f'A{rr}',label,bold=True);write(final,f'E{rr}',value,editable=True,fmt=fmt);final.row_dimensions[rr].height=28
write(final,'G5','SUPUESTO didáctico editable: 20 % de recargo sobre costo. No equivale a margen de 20 % sobre ventas ni a utilidad real.')
write(final,'G6','18 % para la operación gravada del ejercicio. IGV de venta, no impuesto a la renta ni utilidad. Fuente SUNAT al pie.')
final.row_dimensions[5].height=48;final.row_dimensions[6].height=42
text(final,8,'Importante: los montos calculan el ingreso objetivo del conjunto del servicio en un mes. Ruma combina venta de material, convenios y otros ingresos; esto no demuestra que un cliente pagaría ese precio.',45)
table(final,10)
section(final,11,'PUNTO DE PARTIDA: COSTO DE PRODUCCIÓN / SERVICIO','total')
refs['production_physical']=total(final,12,'Parte física',f'={refs["physical_total"]}')
refs['production_software']=total(final,13,'Parte software',f'={refs["software_total"]}')
refs['production']=total(final,14,'COSTO DE PRODUCCIÓN = FÍSICO + SOFTWARE','=F12+F13')
section(final,16,'4. GASTOS ADMINISTRATIVOS','admin')
item(final,17,'GA-01','Gerencia / administración general',1,'persona/mes',get('Mano de obra','H18'),'Costo empleador del piloto. No incluye desarrollo, ya clasificado en software.',('Mano de obra','H18'))
item(final,18,'GA-02','Contador externo',1,'mes',get('Indirectos y gastos','J22'),'Supuesto mensual de contabilidad.',('Indirectos y gastos','J22'))
refs['admin']=total(final,19,'TOTAL GASTOS ADMINISTRATIVOS','=SUM(F17:F18)')
section(final,21,'5. GASTOS DE VENTAS','sales')
item(final,22,'GV-01','Ejecutivo comercial',0,'persona/mes',get('Mano de obra','C22')*1.005+15,'No contratado en el piloto. Tarifa cargada bajo los supuestos laborales heredados.',('Mano de obra','H22'))
item(final,23,'GV-02','Marketing y publicidad',1,'mes',get('Indirectos y gastos','J25'),'Supuesto comercial mensual.',('Indirectos y gastos','J25'))
item(final,24,'GV-03','Comisión comercial sobre convenios',1,'mes',get('Indirectos y gastos','J26'),'S/ 75 sobre ingresos hipotéticos del piloto; no implica contrato firmado.',('Indirectos y gastos','J26'))
refs['sales']=total(final,25,'TOTAL GASTOS DE VENTAS','=SUM(F22:F24)')
section(final,27,'6. GASTOS FINANCIEROS Y BANCARIOS','finance')
item(final,28,'GF-01','Gastos bancarios y otros asociados',1,'mes',get('Indirectos y gastos','J23'),'Se reclasifica aquí la partida mixta del modelo anterior. Separar gastos no bancarios al tener detalle.',('Indirectos y gastos','J23'))
item(final,29,'GF-02','Comisiones por transferencias / pagos',1,'mes',get('Costos variables','J18'),'Cero en el piloto; no es capital de préstamo.',('Costos variables','J18'))
item(final,30,'GF-03','Intereses de préstamos',1,'mes',0,'Sin deuda presupuestada. Cero como supuesto; no inventar financiamiento.')
refs['finance']=total(final,31,'TOTAL GASTOS FINANCIEROS Y BANCARIOS','=SUM(F28:F30)')
refs['total']=total(final,33,'COSTO TOTAL = PRODUCCIÓN + ADMIN. + VENTAS + FINANCIEROS','=F14+F19+F25+F31')
section(final,35,'7. UTILIDAD PROPUESTA','profit')
refs['profit']=total(final,36,'UTILIDAD DESEADA = COSTO TOTAL × RECARGO','=F33*E5')
refs['net_sales']=total(final,37,'VALOR DE VENTA (SIN IGV) = COSTO TOTAL + UTILIDAD','=F33+F36')
section(final,39,'8. IGV / IVA','tax')
refs['tax']=total(final,40,'IGV DE VENTA = VALOR DE VENTA × TASA','=F37*E6')
refs['price']=total(final,42,'PRECIO DE VENTA (CON IGV) = VALOR DE VENTA + IGV','=F37+F40')
text(final,44,'Lectura del diagrama: MD + MOD = costo primo; + CIF = costo de producción; + gastos administrativos, ventas y financieros = costo total; + utilidad = valor de venta; + IGV = precio de venta.',48)
text(final,45,'La utilidad de la fila 36 es una META de formación de precio, antes del impuesto a la renta. No reemplaza las pérdidas calculadas en el modelo de inversionistas. No es dinero disponible para repartir.',45)
text(final,46,'Criterio fiscal: el IGV de ventas no es ingreso ni utilidad. El IGV de compras recuperable no es costo; el modelo previo trató algunos servicios digitales como costo con IGV. Revisar esa condición y ajustar hoja 2.',48)
text(final,47,'Fuente IGV: SUNAT, tasa total de 18 % sobre operaciones gravadas. Para el ejercicio se supone que la operación presupuestada está gravada; la aplicación real depende de cada operación.',42)
text(final,48,'https://orientacion.sunat.gob.pe/3053-concepto-tasa-y-operaciones-gravadas-igv-empresas',32)
final['A48'].hyperlink='https://orientacion.sunat.gob.pe/3053-concepto-tasa-y-operaciones-gravadas-igv-empresas'
text(final,50,'CONTROL DE TRANSCRIPCIÓN — La igualdad con el piloto original corresponde a los valores iniciales. Si editas costos, la diferencia puede cambiar legítimamente.',40)
refs['source_total']=total(final,51,'Costo total del piloto original (referencia fija)',get('Estado de costos','D29'))
refs['reconcile']=total(final,52,'DIFERENCIA DE TRANSCRIPCIÓN INICIAL (debe dar 0)','=ROUND(F33-F51,8)')
refs['sumcheck']=total(final,53,'CONTROL PRECIO − COSTO − UTILIDAD − IGV (debe dar 0)','=ROUND(F42-F33-F36-F40,8)')
for cell in ['E5','E6']:
    dv=DataValidation(type='decimal',operator='between',formula1=0,formula2=1,showErrorMessage=True)
    dv.error='Ingrese una tasa entre 0 % y 100 %.'; final.add_data_validation(dv);dv.add(cell)
for ws in wb:
    ws.print_area=f'A1:G{ws.max_row}'
    ws.sheet_properties.outlinePr.summaryRight=False
wb.calculation.calcMode='auto';wb.calculation.fullCalcOnLoad=True;wb.calculation.forceFullCalc=True
wb.save(OUT)
(ROOT/'plantilla/referencias_diagrama.json').write_text(json.dumps({'salida':OUT.name,'referencias':refs,'origen':provenance},ensure_ascii=False,indent=2),encoding='utf-8')
print(OUT)
