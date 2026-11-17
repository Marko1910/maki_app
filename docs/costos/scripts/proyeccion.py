"""Extensión del libro de Claude: flujos auditables de empresa y accionista.

Todas las proyecciones, incluidos los nueve casos de sensibilidad, usan las
mismas fórmulas de Excel. No se escriben resultados precalculados como datos.
"""
from openpyxl.chart import LineChart, Reference
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.utils import get_column_letter as col
import json
from pathlib import Path


def ampliar(e):
    wb, ws_sup = e['wb'], e['ws_sup']
    S, sup, add = e['S'], e['sup'], e['add']
    put, note, header = e['put'], e['note'], e['header']
    SOL, INT, PCT = e['SOL'], e['INT'], e['PCT']

    def param(key, label, unit, value, fmt=SOL, state='SUPUESTO', source=''):
        add(key, label, unit, value, state, source, fmt, True)

    e['ssec']('15. Caja, reparto y condiciones de la proyección')
    param('pr_cost_g', 'Aumento anual de costos y salarios', '% anual', .03, PCT)
    param('pr_price_g', 'Aumento anual de precios de venta', '% anual', 0, PCT)
    param('pr_reg', 'Régimen laboral de la proyección (1 general, 2 pequeña, 3 micro, 4 honorarios)',
          'código', 1, INT, source='General por defecto; REMYPE no se da por acreditado. No cambia los escenarios estáticos.')
    param('vac_cover', 'Cobertura adicional de vacaciones: fracción que exige reemplazo pagado', '%', 0, PCT,
          source='Los 12 sueldos ya pagan el descanso. Solo se suma un reemplazo adicional si se contrata; evita duplicar vacaciones.')
    param('cash_months', 'Caja mínima retenida antes de repartir dividendos', 'meses de costos fijos', 1, e['DEC1'])
    param('ar_days', 'Plazo medio de cobro de las ventas', 'días', 30, INT)
    param('stock_days', 'Inventario financiado antes de vender el material', 'días', 7, INT)
    param('capital', 'Capital social pagado inicial para calcular reserva legal', 'S/', 100000,
          source='Supuesto; no es la valorización. Se asume que aportes adicionales también van a capital, sin prima.')
    param('reserve_rate', 'Reserva legal sobre utilidad anual después de pérdidas', '%', .10, PCT, 'VERIFICADO', '[F41]')
    param('reserve_cap', 'Límite de reserva legal respecto al capital pagado', '%', .20, PCT, 'VERIFICADO', '[F41]')
    param('exit_on', '¿Suponer venta de la participación en el mes 60? (1/0)', 'indicador', 0, INT,
          source='Apagada. Activarla supone comprador y venta real; una valorización no es dinero cobrado.')
    param('exit_cost', 'Gastos de una venta hipotética de acciones', '% del precio', .03, PCT)
    param('exit_tax', 'Impuesto supuesto sobre ganancia de venta de acciones', '% de ganancia', .05, PCT,
          source='Escenario de persona natural domiciliada y venta local; confirmar estructura y costo tributario [F42].')
    param('prepay_low', 'Pago a cuenta IR: tasa mínima RMT hasta 300 UIT', '% ingresos', .01, PCT, 'VERIFICADO', '[F37]')
    param('prepay_high', 'Pago a cuenta IR: tasa mínima general o RMT >300 UIT', '% ingresos', .015, PCT, 'VERIFICADO', '[F37]')
    param('prepay_coef', 'Coeficiente de pagos a cuenta si supera la tasa mínima', 'coeficiente', 0, PCT,
          source='Se compara también con IR/ingresos del año anterior. Aproximación anual, no cronograma tributario.')
    param('plan_group', 'Plan: ¿renegociar tarifa base por viaje agrupado? (1/0)', 'indicador', 1, INT,
          source='HIPÓTESIS. Hoy se paga base por recojo; agrupar rutas no reduce automáticamente la tarifa. Exige acuerdo y cambio operativo.')

    # Completar los parámetros que Claude dejó pendientes, sin inventar fuentes.
    for k in ('ir_rmt', 'ir_tramo', 'ir_gen', 'ir_bpct', 'div_ret'):
        ws_sup[f'F{S[k][0]}'] = 'VERIFICADO'
    ws_sup[f'G{S["ir_rmt"][0]}'] = '[F37] SUNAT: 10 % hasta 15 UIT y 29,5 % por el exceso. Consultado 2026-11-06.'
    ws_sup[f'F{S["ir_sis"][0]}'] = 'SUPUESTO (elección)'
    ws_sup[f'G{S["ir_sis"][0]}'] = '[F39] A: cuatro ejercicios siguientes; B: 50 % sin plazo. En años 1–5 no vence aún ninguna pérdida del año 1.'
    ws_sup[f'C{S["pk0"][0]}'] = 6
    ws_sup[f'C{S["pkd"][0]}'] = 2
    ws_sup[f'G{S["mult"][0]}'] = 'Solo se convierte en cobro si §15 exit_on=1. Valor empresarial = EBITDA año 5 × múltiplo; se agrega caja, sin deuda.'
    ws_sup[f'G{S["payout"][0]}'] = 'Reparto anual al cierre, tras pérdidas, reserva legal y caja mínima. Supone aprobación societaria; no garantiza dividendos.'
    ws_sup[f'G{S["pr_m2"][0]}'] = 'Se escala también antes si los activos alcanzan la etapa Ciudad; evita usar equipo Piloto con demanda de Ciudad.'

    # Corregir doble conteo: salario anual incluye el descanso remunerado.
    for c, days in [('C', 'vac_gen'), ('D', 'vac_mype'), ('E', 'vac_mype')]:
        e['ws_mo'][f'{c}{e["M"]["vac"]}'] = f'={sup(days)}/360*{sup("vac_cover")}'
    e['ws_mo'][f'A{e["M"]["vac"]}'] = 'Reemplazo adicional durante vacaciones (opcional)'
    e['ws_mo'][f'G{e["M"]["vac"]}'] = 'Los 12 sueldos ya incluyen vacaciones; §15 controla reemplazo adicional.'
    ws_sup[f'G{S["vac_gen"][0]}'] = '[F28] Descanso pagado incluido en los 12 sueldos. Reemplazo adicional: §15.'

    # Histórico de la evidencia: no fingir consulta propia al backend.
    for row in e['ws_src'].iter_rows():
        if row[0].value == 'P2':
            e['ws_src'].cell(row[0].row, 7, 'Claude consultó staging el 2026-11-06: base S/2,50 + S/0,30/km, máximo 15 km. Evidencia recuperada del historial; sin nueva consulta en esta sesión.')
        for cell in row:
            if isinstance(cell.value, str) and cell.value.startswith('• Tarifa del EcoRider tomada'):
                cell.value = '• Tarifa del EcoRider confirmada por Claude en staging el 2026-11-06; continuidad contrastada con su historial, sin nueva consulta.'

    # Los escenarios originales se conservan; Inversionistas pasa a contestar la recuperación.
    e['ws_inv'].title = 'Escenarios inversionistas'
    inv = wb.create_sheet('Inversionistas', 0)
    base = wb.create_sheet('Proyección base', 1)
    plan = wb.create_sheet('Proyección plan', 2)
    sens = wb.create_sheet('Sensibilidad retorno', 3)
    detail = wb.create_sheet('Sens detalle')
    specs = [
        ('month','Mes'), ('year','Año'), ('stage','Etapa: 2 piloto / 3 ciudad'), ('active','Recolectores activos'),
        ('picks','Recojos'), ('kg','Kg por recojo'), ('stops','Recojos por viaje'), ('riders','Riders requeridos'),
        ('fee','Pago por recojo'), ('contracts','Convenios municipales'), ('studies','Clientes estudios'),
        ('material','Ingreso material'), ('municipal','Ingreso municipal'), ('research','Ingreso estudios'),
        ('rep','Ingreso REP'), ('revenue','Ingresos totales'), ('points','Compra del material'),
        ('riderpay','Pago riders'), ('variable','Otros costos variables'), ('team','Equipo fijo con cargas'),
        ('fixed','Otros costos fijos'), ('ebitda','EBITDA'), ('dep','Depreciación'), ('startup','Gasto inicial contable'),
        ('ebit','Resultado antes de IR'), ('tax','IR devengado'), ('net','Utilidad neta'),
        ('prepay','Adelantos IR'), ('taxcash','IR pagado en caja'), ('capex','Compra/reemplazo equipos'),
        ('wc','Capital de trabajo'), ('dwc','Cambio capital de trabajo'), ('fcf','Flujo libre empresa'),
        ('cumfcf','Flujo libre acumulado'), ('dcf','Flujo libre descontado'), ('cumdcf','Acumulado descontado'),
        ('cashraw','Caja sin aportes extra ni reparto'), ('cashpre','Caja antes de aporte y reparto'),
        ('extra','Capital adicional requerido'), ('reserve','Reserva legal del año'), ('rescum','Reserva acumulada'),
        ('distributable','Utilidad acumulada disponible'), ('dividend','Dividendo total bruto'), ('cash','Caja final financiada'),
        ('retained','Utilidades retenidas después de reparto'), ('invextra','Aporte adicional inversionista'),
        ('invdiv','Dividendo neto inversionista'), ('sale','Venta neta hipotética participación'),
        ('invflow','Flujo neto inversionista'), ('invcum','Flujo acumulado inversionista'),
        ('invdisc','Flujo descontado inversionista'), ('invdcum','Acumulado descontado inversionista'),
        ('positive','Mes rentable'), ('divmonth','Primer dividendo candidato'), ('payback','Recuperación sostenida inversionista'),
        ('dpayback','Recuperación descontada sostenida'), ('projectpay','Recuperación sostenida proyecto'),
        ('cashaudit','Control caja = 0'), ('divaudit','Control límite dividendos = 0'), ('invaudit','Control flujo inversor = 0'),
        ('sign','Signo flujo inversor'), ('changes','Cambio de signo'), ('discprojectpay','Recuperación descontada proyecto'),
        ('cashout','Mes insuficiencia inversión inicial'), ('margin','Contribución por recojo'),
    ]
    C = {k: col(i + 1) for i, (k, _) in enumerate(specs)}
    integer = {'month','year','stage','active','picks','riders','contracts','studies','positive','divmonth','payback','dpayback','projectpay','discprojectpay','cashout'}
    metrics = {}
    manifest = {'inputs': {k: f'C{r}' for k,(r,_) in S.items()}, 'columns': C, 'cases': {}}

    def engine(ws, start, is_plan, label, kg_final=None, fee_final=None):
        """Un solo motor para caso base, plan y sensibilidad."""
        case_label = label
        put(ws, f'A{start}', label, e['f_title'])
        note(ws, start + 1, 'Meses 1–60 = cinco ejercicios enero–diciembre. Soles nominales. Aportes adicionales son hipotéticos, sin financiamiento contratado. Ver metodología.', 'P', 30)
        header(ws, start + 2, [v for _,v in specs])
        zero, first, last = start + 3, start + 4, start + 63
        ay = start + 68
        summary = start + 79
        def ref(k,r): return f'{C[k]}{r}'
        def span(k,a=first,b=last): return f'{C[k]}{a}:{C[k]}{b}'
        def prev(k,r): return ref(k,r-1)
        def st(k,r): return f'INDEX({e["sup_row"](k)},1,{ref("stage",r)})' if not S[k][1] else sup(k)
        def es(key,r): return f'INDEX(\'Escenarios\'!$C${e["E"][key]}:$E${e["E"][key]},1,{ref("stage",r)})'
        def irange(sheet,keymap,key,r): return f'INDEX(\'{sheet}\'!$C${keymap[key]}:$E${keymap[key]},1,{ref("stage",r)})'
        pc0=f'({sup("laptop")}*{sup("laptop_n",1)}+{sup("phone")}*{sup("phone_n",1)})'
        bal0=f'({sup("balanza")}*{sup("balanza_n",1)})'
        pcadd=f'({sup("laptop")}*MAX(0,{sup("laptop_n",2)}-{sup("laptop_n",1)})+{sup("phone")}*MAX(0,{sup("phone_n",2)}-{sup("phone_n",1)}))'
        baladd=f'({sup("balanza")}*MAX(0,{sup("balanza_n",2)}-{sup("balanza_n",1)}))'
        initial=f"'Única vez'!{e['US_'][1]}{e['UNI_TOT']}"
        # Año fiscal: se usa IR anual y pérdidas de años anteriores, sin compensar dos veces.
        ah=['Año','Ingresos','Resultado fiscal aproximado','Pérdidas iniciales','Pérdida aplicada','Base imponible',
            'IR anual','Pérdidas finales','Adelantos','Crédito IR anterior','Regularización IR','Crédito IR final','EBIT positivo acumulado']
        header(ws, ay-1, ah)
        for y in range(1,6):
            a=ay+y-1; lo=first+(y-1)*12; hi=lo+11
            vals=[y, f'=SUM({span("revenue",lo,hi)})', f'=SUM({span("ebit",lo,hi)})',
                  '=0' if y==1 else f'=H{a-1}',
                  f'=MIN(D{a},MAX(0,C{a})*IF({sup("ir_sis")}=2,{sup("ir_bpct")},1))',
                  f'=MAX(0,C{a}-E{a})',
                  f'=IF(OR({sup("ir_reg")}=2,B{a}>1700*{sup("uit")}),F{a}*{sup("ir_gen")},MIN(F{a},{sup("ir_tramo")}*{sup("uit")})*{sup("ir_rmt")}+MAX(0,F{a}-{sup("ir_tramo")}*{sup("uit")})*{sup("ir_gen")})',
                  f'=D{a}-E{a}+MAX(0,-C{a})', f'=SUM({span("prepay",lo,hi)})', '=0' if y==1 else f'=L{a-1}',
                  f'=MAX(0,G{a}-I{a}-J{a})', f'=MAX(0,J{a}+I{a}-G{a})',
                  f'=SUMIF({span("ebit",lo,hi)},">0",{span("ebit",lo,hi)})']
            for i,v in enumerate(vals): put(ws, f'{col(i+1)}{a}', v, fmt=INT if i==0 else SOL)

        for k,_ in specs: put(ws, ref(k,zero), 0, fmt=SOL)
        initvals={'fcf':f'-{initial}', 'cumfcf':ref('fcf',zero), 'dcf':ref('fcf',zero), 'cumdcf':ref('fcf',zero),
                  'cashraw':f'{sup("inversion")}-{initial}', 'cashpre':f'{sup("inversion")}-{initial}',
                  'extra':f'MAX(0,-{ref("cashpre",zero)})', 'cash':f'{ref("cashpre",zero)}+{ref("extra",zero)}',
                  'invextra':f'{ref("extra",zero)}*{sup("inv_pct")}',
                  'invflow':f'-{sup("inversion")}-{ref("invextra",zero)}', 'invcum':ref('invflow',zero),
                  'invdisc':ref('invflow',zero), 'invdcum':ref('invflow',zero), 'sign':'-1'}
        for k,v in initvals.items(): put(ws,ref(k,zero),'='+v,fmt=SOL)
        for m in range(1,61):
            r=zero+m; year=(m-1)//12+1; a=ay+year-1
            x=lambda k:ref(k,r)
            infl=f'(1+{sup("pr_cost_g")})^({x("year")}-1)'
            price=f'(1+{sup("pr_price_g")})^({x("year")}-1)'
            stagechange=f'AND({x("stage")}=3,{prev("stage",r)}<>3)'
            # Fecha de ampliación según calendario o capacidad; independiente de costos.
            transition=f'IFERROR(MATCH(3,{span("stage")},0),61)'
            cityprice=f'(1+{sup("pr_cost_g")})^INT(({transition}-1)/12)'
            repl=f'ROUNDUP(12/{sup("dep_pc")},0)+1'
            ramp=lambda k0,kf,kn:f'{sup(k0)}+(({kf})-{sup(k0)})*MIN(1,({x("month")}-1)/MAX(1,{sup(kn)}-1))'
            kgexpr=ramp('pl_kg0',kg_final or sup('pl_kgF'),'pl_kgN') if is_plan else sup('b_kg')
            stopsexpr=ramp('pl_par0',sup('pl_parF'),'pl_parN') if is_plan else sup('b_par')
            normalfee=f'{sup("fee_base")}/IF({sup("plan_group")}=1,{x("stops")},1)+{sup("fee_km")}*MIN(15,{sup("km")})' if is_plan else f'{sup("fee_base")}+{sup("fee_km")}*MIN(15,{sup("km")})'
            feeexpr=(f'({sup("fee_base")}+{sup("fee_km")}*MIN(15,{sup("km")}))+(({fee_final})-({sup("fee_base")}+{sup("fee_km")}*MIN(15,{sup("km")})))*MIN(1,({x("month")}-1)/MAX(1,{sup("pl_parN")}-1))') if fee_final else normalfee
            # Salarios por régimen escogido, adicionales al crecimiento por volumen.
            load=f'INDEX(\'Mano de obra\'!$C${e["M"]["tot"]}:$F${e["M"]["tot"]},1,{sup("pr_reg")})'
            fixload=f'INDEX(\'Mano de obra\'!$C${e["M"]["fix"]}:$F${e["M"]["fix"]},1,{sup("pr_reg")})'
            team='+'.join(f'({sup("sal_"+k)}*(1+{load})+{fixload})*{st("n_"+k,r)}' for k,*_ in e['ROLES'])
            ridersunit=f'({sup("chaleco")}/{sup("chaleco_vida")}+{sup("uniforme")}/{sup("uniforme_vida")}+{sup("epp")}+IF({st("rider_mode",r)}=1,{sup("seg_ind")}*{st("seg_ind_on",r)},0))'
            # Partir del costo fijo existente y sustituir personal, depreciación, seguros y comisión comercial.
            cf=irange('Punto de equilibrio',e['P'],'cf',r)
            tm=f'INDEX(\'Mano de obra\'!$G${e["M"]["team_tot"]}:$I${e["M"]["team_tot"]},1,{x("stage")})'
            oldr=f'INDEX(\'Mano de obra\'!$G${e["M"]["r_pla_used"]}:$I${e["M"]["r_pla_used"]},1,{x("stage")})'
            olddep=irange('Estado de costos',e['T'],'dep',r)
            oldcommission=f'{irange("Ingresos",e["G"],"fix",r)}*{sup("com_ventas")}'
            riderfixed='+'.join(f'INDEX(\'Indirectos y gastos\'!${e["IC"][0]}${e["N"][k]}:${e["IC"][2]}${e["N"][k]},1,{x("stage")})' for k in ['chaleco','uniforme','epp','seguro'])
            tech=f'INDEX(\'Costos variables\'!${e["VS"][0]}${e["V"]["tech"]}:${e["VS"][2]}${e["V"]["tech"]},1,{x("stage")})'
            formulas={
                'month':str(m), 'year':str(year),
                'active':f'MIN({sup("pr_cap")},ROUND({sup("pr_act0")}*(1+{sup("pr_g")})^({x("month")}-1),0))',
                'stage':f'IF(OR({x("month")}>={sup("pr_m2")},{x("active")}>={sup("pr_city")}),3,2)',
                'picks':f'{x("active")}*{st("pick",r)}', 'kg':kgexpr, 'stops':stopsexpr,
                'riders':f'IF({x("picks")}=0,0,MAX({sup("pr_rmin")},ROUNDUP({x("picks")}/({sup("dias")}*({sup("pr_capd")}+({x("stops")}-1)*{sup("pr_capx")})),0)))',
                'fee':f'({feeexpr})*{infl}',
                'contracts':(f'MIN({sup("pl_conv_max")},{sup("pl_conv0")}+INT(({x("month")}-1)/MAX(1,{sup("pl_conv_cada")})))' if is_plan else st('muni_n',r)),
                'studies':(f'IF(AND({x("month")}>={sup("pl_est_mes")},{x("active")}>={sup("pl_est_umb")}),MIN({sup("pl_est_max")},1+INT(({x("month")}-{sup("pl_est_mes")})/MAX(1,{sup("pl_est_cada")}))),0)' if is_plan else st('est_n',r)),
                'material':f'{x("picks")}*{x("kg")}*{sup("p_avg")}*{st("on_mat",r)}*{price}',
                'municipal':f'({x("contracts")}*{sup("muni_fee")}+{x("picks")}*{x("kg")}/1000*{sup("muni_t")})*{st("on_muni",r)}*{price}',
                'research':f'{x("studies")}*{sup("est_price")}*{st("on_est",r)}*{price}',
                'rep':f'{x("picks")}*{x("kg")}/1000*{sup("rep_t")}*{st("on_rep",r)}*{price}',
                'revenue':f'SUM({x("material")}:{x("rep")})',
                'points':f'{x("picks")}*{x("kg")}*{sup("p_avg")}*{sup("share")}*{sup("canje")}*{price}',
                'riderpay':'0',
                'variable':f'({x("picks")}*({sup("bolsa")}+{sup("precinto")})+{x("active")}*{sup("impresos")}+IFERROR({tech}/{es("act",r)}*{x("active")},0))*{infl}+({x("riderpay")}+{x("points")})*{sup("commission")}',
                'team':f'({team})*{infl}',
                'fixed':f'({cf}-{tm}-{oldr}-{olddep}-{oldcommission}-({riderfixed})+{x("riders")}*{ridersunit}+MAX(0,{x("active")}-{sup("pr_city")})/1000*{sup("pr_extra")}+MAX(0,{x("active")}/{sup("pr_city")}-1)*{irange("Estado de costos",e["T"],"infra",r)})*{infl}+({x("municipal")}+{x("research")})*{sup("com_ventas")}',
                'ebitda':f'{x("revenue")}-SUM({x("points")}:{x("fixed")})',
                'dep':f'IF({x("month")}<{repl},{pc0}*{sup("dep_pc")}/12,{pc0}*(1+{sup("pr_cost_g")})^INT(({repl}-1)/12)*{sup("dep_pc")}/12)+{bal0}*{sup("dep_maq")}/12+IF({x("month")}>={transition},({pcadd}*{sup("dep_pc")}+{baladd}*{sup("dep_maq")})*{cityprice}/12,0)',
                'startup':f'IF({x("month")}=1,MAX(0,{initial}-{pc0}-{bal0}),0)',
                'ebit':f'{x("ebitda")}-{x("dep")}-{x("startup")}',
                'tax':f'IFERROR($G${a}*MAX(0,{x("ebit")})/$M${a},0)',
                'net':f'{x("ebit")}-{x("tax")}',
                'prepay':f'{x("revenue")}*IF(AND({sup("ir_reg")}=1,$B${a}<=300*{sup("uit")}),{sup("prepay_low")},MAX({sup("prepay_high")},{sup("prepay_coef")},{"0" if year==1 else f"IFERROR($G${a-1}/$B${a-1},0)"}))',
                'taxcash':f'{x("prepay")}+IF(MOD({x("month")},12)=0,$K${a},0)',
                'capex':f'IF({stagechange},({pcadd}+{baladd})*{infl},0)+IF({x("month")}={repl},{pc0}*{infl},0)',
                'wc':f'{x("revenue")}*{sup("ar_days")}/30+{x("points")}*{sup("stock_days")}/30',
                'dwc':f'{x("wc")}-{prev("wc",r)}',
                'fcf':f'{x("ebitda")}-{x("taxcash")}-{x("capex")}-{x("dwc")}',
                'cumfcf':f'{prev("cumfcf",r)}+{x("fcf")}', 'dcf':f'{x("fcf")}/(1+{sup("tasa_m")})^{x("month")}',
                'cumdcf':f'{prev("cumdcf",r)}+{x("dcf")}', 'cashraw':f'{sup("inversion")}+{x("cumfcf")}',
                'cashpre':f'{prev("cash",r)}+{x("fcf")}', 'extra':f'MAX(0,-{x("cashpre")})',
                'reserve':f'IF(MOD({x("month")},12)=0,MIN(MAX(0,({sup("capital")}+SUM({span("extra",zero,r)}))*{sup("reserve_cap")}-{prev("rescum",r)}),MAX(0,MIN(SUM({span("net",r-11,r)}),{prev("retained",r)}+{x("net")}))*{sup("reserve_rate")}),0)',
                'rescum':f'{prev("rescum",r)}+{x("reserve")}',
                'distributable':f'MAX(0,{prev("retained",r)}+{x("net")}-{x("reserve")})',
                'dividend':f'IF(MOD({x("month")},12)=0,MAX(0,MIN({x("distributable")}*{sup("payout")},{x("cashpre")}-{sup("cash_months")}*({x("team")}+{x("fixed")}))),0)',
                'cash':f'{x("cashpre")}+{x("extra")}-{x("dividend")}',
                'retained':f'{prev("retained",r)}+{x("net")}-{x("reserve")}-{x("dividend")}',
                'invextra':f'{x("extra")}*{sup("inv_pct")}',
                'invdiv':f'{x("dividend")}*{sup("inv_pct")}*(1-{sup("div_ret")})',
                'sale':'0', 'invflow':f'{x("invdiv")}+{x("sale")}-{x("invextra")}',
                'invcum':f'{prev("invcum",r)}+{x("invflow")}', 'invdisc':f'{x("invflow")}/(1+{sup("tasa_m")})^{x("month")}',
                'invdcum':f'{prev("invdcum",r)}+{x("invdisc")}',
                'positive':f'IF({x("ebit")}>0,{x("month")},999)', 'divmonth':f'IF({x("invdiv")}>0,{x("month")},999)',
                'payback':f'IF(MIN({span("invcum",r,last)})>=0,{x("month")},999)',
                'dpayback':f'IF(MIN({span("invdcum",r,last)})>=0,{x("month")},999)',
                'projectpay':f'IF(MIN({span("cumfcf",r,last)})>=0,{x("month")},999)',
                'discprojectpay':f'IF(MIN({span("cumdcf",r,last)})>=0,{x("month")},999)',
                'cashout':f'IF({x("cashraw")}<0,{x("month")},999)',
                'cashaudit':f'ROUND({x("cash")}-{prev("cash",r)}-{x("fcf")}-{x("extra")}+{x("dividend")},6)',
                'divaudit':f'ROUND(MAX(0,{x("dividend")}-{x("distributable")},{x("dividend")}-MAX(0,{x("cashpre")})),6)',
                'invaudit':f'ROUND({x("invflow")}-{x("invdiv")}-{x("sale")}+{x("invextra")},6)',
                'sign':f'IF({x("invflow")}=0,{prev("sign",r)},SIGN({x("invflow")}))',
                'changes':f'IF({x("sign")}={prev("sign",r)},0,1)',
                'margin':f'IFERROR(({x("material")}+{x("rep")}+{x("picks")}*{x("kg")}/1000*{sup("muni_t")}*{st("on_muni",r)}*{price}-{x("points")}-{x("riderpay")}-{x("variable")})/{x("picks")},0)',
            }
            # Planilla usa inflación una vez; independientes ya la incluyen en la tarifa.
            formulas['riderpay']=f'IF({st("rider_mode",r)}=1,{x("picks")}*{x("fee")},{x("riders")}*({sup("rider_sal")}*(1+{load}+{sup("sctr")})+{fixload}+{sup("rider_mov")})*{infl})'
            if m % 12:
                formulas['reserve']='0'
            if m==60:
                gross=f'(MAX(0,SUM({span("ebitda",last-11,last)}))*{sup("mult")}+{x("cash")})*{sup("inv_pct")}*(1-{sup("exit_cost")})'
                basis=f'({sup("inversion")}+SUM({span("invextra",zero,last)}))'
                formulas['sale']=f'IF({sup("exit_on")}=1,MAX(0,{gross}-MAX(0,{gross}-{basis})*{sup("exit_tax")}),0)'
            for k,v in formulas.items():
                put(ws,ref(k,r),'='+v,fmt=INT if k in integer else SOL)
            for k in ('kg','stops'): ws[ref(k,r)].number_format=e['DEC1']
        # Las fórmulas de filas anuales usan rangos completos: sin dependencias circulares.
        header(ws,summary-1,['Indicador','Resultado'])
        names={}
        for key,label,formula,fmt in [
            ('profit','Primer mes con resultado operativo positivo',f'MIN({span("positive")})',INT),
            ('divfirst','Primer mes de dividendos netos',f'MIN({span("divmonth")})',INT),
            ('cashout','Mes en que la inversión inicial resulta insuficiente',f'MIN({span("cashout")})',INT),
            ('needed','Capital total mínimo sin reparto',f'MAX(0,-MIN({span("cumfcf",zero,last)}))',SOL),
            ('extra','Aportes adicionales de todos los socios',f'SUM({span("extra",zero,last)})',SOL),
            ('invtotal','Total aportado por inversionista',f'{sup("inversion")}+SUM({span("invextra",zero,last)})',SOL),
            ('divs','Dividendos netos cobrados por inversionista',f'SUM({span("invdiv")})',SOL),
            ('exit','Venta neta hipotética cobrada',ref('sale',last),SOL),
            ('gain','Cobros menos TODOS los aportes del inversionista',ref('invcum',last),SOL),
            ('moic','Múltiplo de efectivo cobrado / aportes (MOIC)',f'IFERROR((SUM({span("invdiv")})+{ref("sale",last)})/({sup("inversion")}+SUM({span("invextra",zero,last)})),0)',e['DEC2']),
            ('payback','Mes de recuperación sostenida del inversionista',f'MIN({span("payback")})',INT),
            ('dpayback','Mes de recuperación descontada del inversionista',f'MIN({span("dpayback")})',INT),
            ('npv','VAN del inversionista, incluidos aportes extra',f'SUM({span("invdisc",zero,last)})',SOL),
            ('irr','TIR anual efectiva del inversionista',f'IF(SUM({span("changes")})>1,"Múltiples cambios de signo: ver VAN",IFERROR((1+IRR({span("invflow",zero,last)}))^12-1,"No calculable: sin cambio de signo"))',PCT),
            ('projectpay','Recuperación del desembolso del proyecto',f'MIN({span("projectpay")})',INT),
            ('projectdisc','Recuperación descontada del proyecto',f'MIN({span("discprojectpay")})',INT),
            ('projectnpv','VAN de flujos libres del proyecto, sin venta final',f'SUM({span("dcf",zero,last)})',SOL),
            ('cash','Caja empresa al mes 60',ref('cash',last),SOL),
            ('ebitda5','EBITDA año 5',f'SUM({span("ebitda",last-11,last)})',SOL),
            ('rev5','Ingresos año 5',f'SUM({span("revenue",last-11,last)})',SOL),
            ('margin60','Margen por recojo al mes 60',ref('margin',last),SOL),
        ]:
            rr=summary+len(names); names[key]=f"'{ws.title}'!B{rr}"
            put(ws,f'A{rr}',label,e['f_bold']); put(ws,f'B{rr}','='+formula,fmt=fmt)
        ws.freeze_panes='D8'; ws.sheet_view.showGridLines=False
        ws.column_dimensions['A'].width=12
        for i in range(2,len(specs)+1): ws.column_dimensions[col(i)].width=20
        ws.row_dimensions[start+2].height=60
        ws.sheet_properties.pageSetUpPr.fitToPage=True
        ws.page_setup.orientation='landscape'; ws.page_setup.paperSize=ws.PAPERSIZE_A3
        ws.page_setup.fitToWidth=2; ws.page_setup.fitToHeight=0
        manifest['cases'][case_label]={'sheet':ws.title,'zero':zero,'first':first,'last':last,'annual':ay,'summary':names}
        return names

    metrics['base']=engine(base,4,False,'Caso base: economía por recojo actual con crecimiento supuesto')
    metrics['plan']=engine(plan,4,True,'Plan condicionado: más kg, rutas renegociadas y contratos supuestos')
    # Sensibilidad auditada con el mismo motor, incluyendo impuestos y nuevos aportes.
    e['title'](sens,'Sensibilidad de recuperación — plan a 60 meses','Cada celda recalcula los 60 meses; incluye capital adicional, IR, dividendos y venta solo si se activa.')
    note(sens,3,'Filas: kg finales por recojo. Columnas: tarifa final por recojo en soles del año 1, antes de inflación. Se supone tarifa renegociada, independiente de la agrupación.', 'E',40)
    for j in range(3): put(sens,f'{col(j+2)}5',f'={sup("pp0")}+{j}*{sup("ppd")}',fmt=SOL)
    for i in range(3):
        put(sens,f'A{i+6}',f'={sup("pk0")}+{i}*{sup("pkd")}',fmt=e['DEC1'])
        for j in range(3):
            dest=f'{col(j+2)}{i+6}'
            mm=engine(detail,4+140*(i*3+j),True,f'Sensibilidad {i+1},{j+1}',f"'Sensibilidad retorno'!$A${i+6}",f"'Sensibilidad retorno'!{col(j+2)}$5")
            put(sens,dest,f'=IF({mm["payback"]}>60,"No recupera en 60 meses",{mm["payback"]})',fmt=INT,align=e['wrap'],border=e['b_all'])
            put(sens,f'{col(j+2)}{i+13}',f'={mm["gain"]}',fmt=SOL,border=e['b_all'])
    put(sens,'A5','Kg / pago final',e['f_bold']); put(sens,'A11','Ganancia neta del inversionista a 60 meses (S/)',e['f_sec'])
    for j in range(3): put(sens,f'{col(j+2)}12',f'={col(j+2)}5',fmt=SOL)
    for i in range(3): put(sens,f'A{i+13}',f'=A{i+6}',fmt=e['DEC1'])
    e['widths'](sens,{'A':28,'B':30,'C':30,'D':30,'E':12})
    for r in range(6,9): sens.row_dimensions[r].height=35

    e['title'](inv,'Ruma | Recuperación de la inversión','Modelo de decisión a cinco años · corte 06/11/2026 · escenarios hipotéticos, sin contratos ni retorno garantizado')
    note(inv,3,'La rentabilidad de la empresa y la recuperación del accionista son distintas. El retorno cuenta solo dividendos netos y una venta si se activa; también descuenta cada aporte adicional.', 'E',38)
    header(inv,5,['Indicador','Unidad','Caso base','Plan con palancas','Cómo interpretarlo'])
    row=6
    def summary_line(label,unit,key=None,formula=None,fmt=SOL,explain='',month=False):
        nonlocal row
        put(inv,f'A{row}',label,e['f_bold'],align=e['wrap']); put(inv,f'B{row}',unit)
        for c,case in [('C','base'),('D','plan')]:
            expr=metrics[case][key] if key else formula
            if month: expr=f'IF({expr}>60,"No ocurre en 60 meses",{expr})'
            put(inv,f'{c}{row}','='+expr,fmt=fmt,align=e['wrap'],border=e['b_all'])
        put(inv,f'E{row}',explain,align=e['wrap']); inv.row_dimensions[row].height=32
        row+=1
    summary_line('Inversión inicial del inversionista','S/',formula=sup('inversion'),explain='Capital inicial de caja. No se confunde con la valorización de la empresa.')
    summary_line('Participación del inversionista','%',formula=sup('inv_pct'),fmt=PCT,explain='Se mantiene si participa a prorrata en todos los aportes posteriores.')
    summary_line('Reparto anual de utilidades disponibles','%',formula=sup('payout'),fmt=PCT,explain='Después de pérdidas, IR, reserva legal y caja mínima.')
    for label,key,explain in [
        ('Primer mes en que la empresa obtiene utilidad operativa','profit','Primer EBIT positivo; no implica recuperación de pérdidas acumuladas.'),
        ('Primer mes en que el inversionista recibe dividendos','divfirst','Reparto anual supuesto y sujeto a caja y utilidades disponibles.'),
        ('¿Cuándo recupera TODO lo que aportó?','payback','Recuperación por cobros netos. Incluye aportes posteriores; no vuelve a perder lo recuperado hasta mes 60.'),
        ('Recuperación descontando el valor del tiempo','dpayback','Con tasa anual editable de 25 % por defecto.'),
        ('Mes en que se agota la inversión inicial','cashout','Contrafactual sin aportes adicionales ni dividendos; anticipa necesidad de nueva financiación.')]:
        summary_line(label,'mes',key=key,fmt=INT,month=True,explain=explain)
    for label,key,unit,fmt,explain in [
        ('Capital total mínimo requerido por el proyecto','needed','S/',SOL,'Mayor déficit acumulado antes de repartir dividendos.'),
        ('Financiación adicional requerida, todos los socios','extra','S/',SOL,'Hipotética y no comprometida. El modelo aporta cuando la caja cae bajo cero.'),
        ('Aporte TOTAL del inversionista en cinco años','invtotal','S/',SOL,'Inversión inicial + participación proporcional en financiación adicional.'),
        ('Dividendos netos cobrados en cinco años','divs','S/',SOL,'Incluye retención de 5 % para la persona natural supuesta.'),
        ('Cobro por venta hipotética de la participación','exit','S/',SOL,'Cero por defecto. Valorización y comprador son supuestos si se activa la venta.'),
        ('Ganancia o pérdida neta del inversionista','gain','S/',SOL,'Cobros menos TODOS los aportes; el valor de acciones no vendidas queda excluido.'),
        ('MOIC: efectivo cobrado por cada sol aportado','moic','veces',e['DEC2'],'Menos de 1: aún no recupera sus aportes. No incluye valorización sin venta.'),
        ('VAN del inversionista','npv','S/',SOL,'Incluye capital inicial, aportes extra y cobros descontados.'),
        ('TIR anual efectiva del inversionista','irr','%',PCT,'Si el flujo no cambia de signo, no existe una TIR calculable.'),
        ('EBITDA de la empresa en el año 5','ebitda5','S/',SOL,'Resultado antes de depreciación e impuestos; no equivale a caja distribuible.'),
        ('Margen de contribución por recojo en mes 60','margin60','S/',SOL,'Mide si cada recojo aporta a cubrir equipo y otros costos fijos.')]:
        summary_line(label,unit,key=key,fmt=fmt,explain=explain)
    summary_line('Recuperación del desembolso del proyecto','mes',key='projectpay',fmt=INT,month=True,explain='Flujos de la empresa contra sus desembolsos, no contra el aporte del accionista.')
    summary_line('Recuperación descontada del proyecto','mes',key='projectdisc',fmt=INT,month=True)
    summary_line('VAN del proyecto sin venta final','S/',key='projectnpv')
    row+=1
    note(inv,row,'Supuesto de financiación: el inversionista pone el capital inicial. Cada déficit posterior se cubre con nuevos aportes: su porcentaje por el inversionista y el resto por otros socios. Si nadie financia esos déficits, la operación no puede seguir como está proyectada.', 'E',48)
    row+=2
    note(inv,row,'El plan requiere validar 8 kg por recojo, renegociar la base del pago por viaje y firmar convenios y contratos de estudios. Ninguno de esos ingresos comerciales está contratado. Edita Supuestos §§14–15 y consulta Sensibilidad retorno.', 'E',45)
    # Gráfico sin valor terminal: permite ver el déficit real y el flujo de cada inversor.
    chrow=row+3
    header(inv,chrow,['Mes','Empresa base: flujo acumulado','Empresa plan: flujo acumulado','Inversionista base: flujo acumulado','Inversionista plan: flujo acumulado'])
    for m in range(61):
        rr=chrow+1+m; put(inv,f'A{rr}',m,fmt=INT)
        for c,ws,k in [('B',base,'cumfcf'),('C',plan,'cumfcf'),('D',base,'invcum'),('E',plan,'invcum')]:
            put(inv,f'{c}{rr}',f"='{ws.title}'!{C[k]}{7+m}",fmt=SOL)
    chart=LineChart(); chart.title='Flujos acumulados a cinco años'; chart.y_axis.title='Soles'; chart.x_axis.title='Mes'
    chart.add_data(Reference(inv,min_col=2,max_col=5,min_row=chrow,max_row=chrow+61),titles_from_data=True)
    chart.set_categories(Reference(inv,min_col=1,min_row=chrow+1,max_row=chrow+61))
    chart.width=28; chart.height=13; chart.legend.position='b'
    inv.add_chart(chart,f'A{chrow+64}')
    e['widths'](inv,{'A':53,'B':12,'C':28,'D':28,'E':65}); inv.freeze_panes='C6'
    inv.print_area=f'A1:E{row}'; inv.page_setup.orientation='landscape'; inv.page_setup.paperSize=inv.PAPERSIZE_A3
    inv.page_setup.fitToWidth=1; inv.page_setup.fitToHeight=1

    methodology=[
        'Proyección creada el 2026-11-06 al retomar la ampliación interrumpida. El archivo de Claude anterior se conserva en respaldo/.',
        'Años 1–5 representan ejercicios completos enero–diciembre desde un inicio hipotético. Tarifas tributarias y UIT 2026 se mantienen constantes: no son predicciones legales futuras.',
        'Base y plan crecen con la misma rampa editable. Caso base conserva kg y pago actual por recojo; también contiene ingresos municipales y estudios supuestos, por lo que no es un escenario de contratos confirmados.',
        'Plan: llega a 8 kg y 3 paradas por viaje. El ahorro de tarifa exige renegociación y cambio operativo; hoy el backend cobra una base por recojo. El costo por km conserva el tope histórico de 15 km.',
        'Costos de personal: se corrigió el doble conteo de vacaciones. Los 12 sueldos incluyen descanso remunerado. Solo se provisiona un reemplazo si vac_cover > 0. No implica retirar el derecho a vacaciones.',
        'Proyección usa régimen laboral general por defecto, sin asumir acceso a REMYPE. Equipo por etapas y crecimiento adicional de soporte e infraestructura. Cargas, reemplazos y precios siguen siendo estimaciones presupuestarias.',
        'Capital de trabajo = cuentas por cobrar (ventas × días/30) + inventario (compra material × días/30). No se presume crédito de proveedores. No se libera al mes 60: la empresa continúa operando.',
        'Compra inicial de equipos ya incluida en Única vez. Se compra el incremento al pasar a Ciudad y se reemplaza el primer lote informático al acabar su vida útil. Depreciación no es un pago adicional.',
        'Gastos iniciales distintos de equipos se reconocen contablemente en mes 1 y se pagan en mes 0, una sola vez. No se capitaliza desarrollo como intangible ni se cuantifican diferencias tributarias de deducibilidad.',
        'IR anual sobre resultado aproximado: RMT 10 % hasta 15 UIT, 29,5 % exceso; general 29,5 %. Si supera 1700 UIT de ingresos, se aplica general. El régimen laboral y el tributario son independientes.',
        'Pérdidas A: hasta cuatro ejercicios posteriores; B: hasta 50 % de renta positiva. No hay pérdidas anteriores al mes 1. Dentro de cinco ejercicios aún no caducan las del primer año. Extender a seis años exige vencimiento por cohortes.',
        'IR caja: adelantos de 1 % o máximo de 1,5 %, coeficiente editable y ratio IR/ingresos del año anterior. Regularización simplificada al cierre del año; saldo a favor se arrastra y no se devuelve en efectivo. No reproduce fechas de declaración ni cambios mensuales de coeficiente.',
        'Dividendos solo al cierre de cada año, limitados por utilidades acumuladas después de pérdidas, reserva legal y caja mínima. Reserva 10 % hasta 20 % del capital social. No modela trámites ni aprobación de junta.',
        'Inversionista: persona natural domiciliada, 20 % por defecto, dividendos netos de 5 %. Cada déficit requiere capital nuevo: inversionista aporta 20 % y demás socios 80 %, sin dilución. Se supone que todos aceptan; no hay financiación asegurada.',
        'Payback sostenido: primer mes donde cobros netos acumulados cubren todos los aportes y no vuelven a ser negativos hasta el mes 60. No recuperar en 60 meses no demuestra imposibilidad para siempre.',
        'VAN usa flujos de mes 0 sin descuento y meses 1–60 a tasa mensual equivalente. TIR mensual se convierte con (1+TIR)^12−1. Se oculta TIR si hay más de un cambio de signo o falta cambio de signo.',
        'Venta final apagada por defecto. Al activarla: EBITDA año 5 positivo × múltiplo + caja, sin deuda; participación del inversor menos costos de venta y tributo supuesto sobre ganancia. Sin comprador no existe ese cobro. No se suma la caja dos veces.',
        'Límites: sin deuda, sin dilución, sin valorización de acciones no vendidas; IGV de ventas/compras no se modela como flujo neto, ni diferencias contables/fiscales, participación laboral en utilidades o remuneraciones futuras mínimas. Cotizar y validar antes de convertir el modelo en compromiso de inversión.',
        'Sensibilidad usa el mismo motor de 60 meses; tarifa por recojo final fija sustituye el efecto de agrupación. Los nueve cálculos están visibles en Sens detalle. No son números pegados.',
    ]
    method=wb.create_sheet('Metodología retorno')
    e['title'](method,'Metodología y fuentes de recuperación','Alcance, supuestos y límites del modelo financiero')
    for i,t in enumerate(methodology,4): note(method,i,t,'D',52)
    sources=[
        ('F37','IR RMT, límites y anticipos','https://www.gob.pe/institucion/sunat/pages/6990-regimen-mype-tributario-rmt'),
        ('F38','IR anual RMT y general','https://renta.sunat.gob.pe/empresas/tasas-de-impuesto'),
        ('F39','Compensación de pérdidas A/B, art. 50','https://www.sunat.gob.pe/legislacion/renta/ley/capvi.pdf'),
        ('F40','Dividendos, retención de 5 %','https://orientacion.sunat.gob.pe/otras-rentas-de-segunda-categoria'),
        ('F41','Reserva legal y distribución, arts. 229–230','https://www.leyes.congreso.gob.pe/documentos/Leyes/26887.pdf'),
        ('F42','Tasas de segunda categoría','https://orientacion.sunat.gob.pe/03-tasas-aplicables'),
        ('F43','RMV 2026 contrastada con diario oficial','https://www.elperuano.pe/noticia/305879-desde-hoy-rige-la-nueva-rmv-sueldo-minimo-sube-a-s-1230-y-cambia-otros-conceptos-laborales'),
    ]
    srcrow=method.max_row+3; header(method,srcrow,['ID','Concepto','Fuente oficial','Consulta'])
    for i,(key,label,url) in enumerate(sources,srcrow+1):
        for j,v in enumerate([key,label,url,'2026-11-06'],1): put(method,f'{col(j)}{i}',v,align=e['wrap'])
        method[f'C{i}'].hyperlink=url; method.row_dimensions[i].height=35
    e['widths'](method,{'A':14,'B':40,'C':90,'D':18})
    # Fuentes al final de la hoja original, sin alterar las anteriores.
    rr=e['ws_src'].max_row+3
    for key,label,url in sources:
        for j,v in enumerate([key,label,'Ver Metodología retorno','VERIFICADO',url,'2026-11-06','Fuente contrastada al retomar el trabajo.'],1):
            put(e['ws_src'],f'{col(j)}{rr}',v,align=e['wrap'])
        e['ws_src'][f'E{rr}'].hyperlink=url; rr+=1

    # Validaciones para evitar ediciones accidentales que produzcan flujos imposibles.
    for key,(r,glob) in S.items():
        if not glob: continue
        value=ws_sup[f'C{r}'].value
        if not isinstance(value,(int,float)): continue
        if key in ('inv_pct','payout','reserve_rate','reserve_cap','exit_cost','exit_tax','div_ret','vac_cover'):
            dv=DataValidation(type='decimal',operator='between',formula1=0,formula2=1)
        elif key in ('plan_group','exit_on'):
            dv=DataValidation(type='whole',operator='between',formula1=0,formula2=1)
        elif key in ('ir_reg','ir_sis'):
            dv=DataValidation(type='whole',operator='between',formula1=1,formula2=2)
        elif key=='pr_reg':
            dv=DataValidation(type='whole',operator='between',formula1=1,formula2=4)
        elif key in ('inversion','dias','dep_pc','dep_maq','chaleco_vida','uniforme_vida','pr_city','pr_capd'):
            dv=DataValidation(type='decimal',operator='greaterThan',formula1=0)
        else:
            continue
        dv.errorTitle='Valor fuera de rango'; dv.error='Usa un valor permitido para este supuesto.'; dv.showErrorMessage=True
        ws_sup.add_data_validation(dv); dv.add(f'C{r}')
    for ws in (inv,base,plan,sens,detail,method):
        ws.sheet_view.showGridLines=False
        ws.sheet_properties.pageSetUpPr.fitToPage=True
    wb.active=0
    wb.calculation.fullCalcOnLoad=True; wb.calculation.forceFullCalc=True; wb.calculation.calcMode='auto'
    manifest['metrics']=metrics
    (Path(e['OUT']).parent/'scripts'/'modelo_referencias.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
