"""PDF legible desde los valores calculados de Excel; no duplica el modelo."""
from pathlib import Path
import json
import fitz
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter
from openpyxl import load_workbook

ROOT=Path(__file__).resolve().parent.parent
meta=json.loads((ROOT/'scripts/modelo_referencias.json').read_text(encoding='utf-8'))
w=load_workbook(ROOT/'Ruma_costos_2026-11.xlsx',data_only=True)

def get(case,key):
    sheet,cell=meta['metrics'][case][key].split('!')
    return w[sheet.strip("'")][cell].value

def money(v): return f'S/ {v:,.0f}'.replace(',',' ')
def month(v): return str(int(v)) if v<=60 else 'No ocurre en 60 meses'

pdf=fitz.open()
green=(.12,.31,.24); ink=(.12,.16,.14); grey=(.35,.39,.36)
def page(title,sub,num):
    p=pdf.new_page(width=595,height=842)
    p.draw_rect(fitz.Rect(38,32,557,38),color=None,fill=green)
    p.insert_text((38,66),title,fontsize=21,fontname='hebo',color=green)
    p.insert_text((38,87),sub,fontsize=9,color=grey)
    p.insert_text((38,814),'Ruma | Modelo editable en Excel | Corte: 06/11/2026',fontsize=8,color=grey)
    p.insert_text((540,814),str(num),fontsize=8,color=grey)
    return p

def paragraph(p,y,text,height=55,size=10):
    result=p.insert_textbox(fitz.Rect(38,y,557,y+height),text,fontsize=size,color=ink,lineheight=1.25)
    assert result>=0,('Texto no cabe',text)
    return y+height

p=page('Recuperación de la inversión','Proyección de cinco años | Caso base y plan con palancas',1)
y=paragraph(p,110,'Con los supuestos actuales, el inversionista no recupera sus aportes en 60 meses. Los S/ 100 000 iniciales resultan insuficientes en el mes 5 en ambos casos.',62,13)
y=paragraph(p,y+5,'La proyección diferencia utilidad de la empresa, caja disponible y dinero cobrado por el accionista. El plan mejora el margen por recojo, pero aún no cubre el equipo y los demás costos fijos.',55)
rows=[
    ('Indicador','Caso base','Plan con palancas'),
    ('Primera utilidad operativa',month(get('base','profit')),month(get('plan','profit'))),
    ('Primer dividendo',month(get('base','divfirst')),month(get('plan','divfirst'))),
    ('Recuperación del inversionista',month(get('base','payback')),month(get('plan','payback'))),
    ('Capital total mínimo del proyecto',money(get('base','needed')),money(get('plan','needed'))),
    ('Aportes adicionales de todos los socios',money(get('base','extra')),money(get('plan','extra'))),
    ('Total aportado por el inversionista',money(get('base','invtotal')),money(get('plan','invtotal'))),
    ('Dividendos netos cobrados',money(get('base','divs')),money(get('plan','divs'))),
    ('Resultado neto del inversionista',money(get('base','gain')),money(get('plan','gain'))),
    ('VAN del inversionista (25 % anual)',money(get('base','npv')),money(get('plan','npv'))),
]
x=[38,282,420,557]; y+=8
for i,row in enumerate(rows):
    h=38
    p.draw_rect(fitz.Rect(38,y,557,y+h),color=(.83,.87,.84),fill=green if i==0 else ((.95,.97,.95) if i%2 else (1,1,1)),width=.4)
    for j,t in enumerate(row):
        result=p.insert_textbox(fitz.Rect(x[j]+6,y+7,x[j+1]-6,y+h-3),t,fontsize=9,fontname='hebo' if i==0 else 'helv',color=(1,1,1) if i==0 else ink)
        assert result>=0,(i,j,t)
    y+=h
y=paragraph(p,y+18,'Qué significan los aportes adicionales',25,12)
y=paragraph(p,y,'Se supone que el inversionista aporta el capital inicial y mantiene una participación del 20 %. Si la caja se agota, aporta el 20 % del déficit y otros socios el 80 %. Es financiación hipotética, todavía no comprometida. Sin esos aportes, la operación no puede continuar como se proyecta.',70)
paragraph(p,y+6,'No se cuenta una venta de acciones: está desactivada por defecto. Una valorización sin comprador no constituye recuperación. Las cifras negativas indican aportes aún no recuperados, sin afirmar que las acciones carezcan de valor.',62)

p=page('Qué necesita validar el piloto','Supuestos editables, evolución de caja y fuentes',2)
paragraph(p,110,'El plan supone llegar a 8 kg por recojo y 3 paradas por viaje, renegociar el pago base del rider, y conseguir convenios municipales y clientes de estudios. Agrupar rutas por sí solo no cambia la tarifa actual por recojo.',66,11)
fig,ax=plt.subplots(figsize=(8,3.5),layout='constrained')
for sheet,label,color in [('Proyección base','Base','#788e82'),('Proyección plan','Plan','#205b43')]:
    values=[w[sheet][f'{meta["columns"]["cumfcf"]}{7+m}'].value for m in range(61)]
    ax.plot(range(61),values,label=label,color=color,linewidth=2)
ax.axhline(0,color='#333333',linewidth=.7)
ax.set(xlabel='Mes',ylabel='Flujo libre acumulado (S/)',title='Necesidad de caja de la empresa, antes de financiación')
ax.yaxis.set_major_formatter(FuncFormatter(lambda x,pos:f'{x/1000000:.1f} M'))
ax.spines[['top','right']].set_visible(False); ax.grid(axis='y',alpha=.2); ax.legend(frameon=False)
chart=ROOT/'verificacion/flujo_acumulado.png'; fig.savefig(chart,dpi=180); plt.close(fig)
p.insert_image(fitz.Rect(30,185,565,425),filename=chart)
y=paragraph(p,445,'Condiciones de lectura',25,12)
for text in [
    'Financiación: el plan requiere aproximadamente S/ 1,35 millones de capital total en cinco años. Los S/ 100 000 iniciales cubren solo una parte de esa necesidad.',
    'Retorno: se reparte 50 % de la utilidad disponible al cierre anual, después de pérdidas, impuestos, reserva legal y caja mínima. El modelo usa retención de dividendos del 5 %.',
    'Costos: régimen laboral general en la proyección, aumento anual supuesto de 3 %, 30 días de cobro y 7 días de inventario. Se eliminó la duplicación del costo de vacaciones.',
    'Horizonte: no recuperar en 60 meses no demuestra imposibilidad para siempre. Los convenios, precios, volúmenes y participación son supuestos; no representan contratos ni rendimientos prometidos.',
]: y=paragraph(p,y,text,57,9.5)
y=paragraph(p,y+7,'Fuentes y trazabilidad',22,11)
for label,url in [
    ('SUNAT: tasas del impuesto a la renta','https://renta.sunat.gob.pe/empresas/tasas-de-impuesto'),
    ('SUNAT: dividendos y retención','https://orientacion.sunat.gob.pe/otras-rentas-de-segunda-categoria'),
    ('Ley General de Sociedades: reserva y dividendos','https://www.leyes.congreso.gob.pe/documentos/Leyes/26887.pdf'),
]:
    p.insert_text((38,y+10),label,fontsize=8.5,color=green)
    p.insert_link({'kind':fitz.LINK_URI,'from':fitz.Rect(38,y,540,y+14),'uri':url}); y+=17
pdf.save(ROOT/'Ruma_inversionistas_2026-11.pdf')
print('PDF de 2 páginas exportado desde los resultados de Excel.')
