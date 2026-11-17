"""Verifica resultados recalculados por Excel con identidades independientes.

Uso: py verificar_modelo.py [archivo.xlsx ...]
No evalúa fórmulas con openpyxl: exige resultados almacenados por Excel.
"""
from pathlib import Path
import json
import math
import sys
from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parent.parent
META = json.loads((ROOT/'scripts/modelo_referencias.json').read_text(encoding='utf-8'))


def check(path):
    w = load_workbook(path, data_only=True)
    formulas = load_workbook(path, data_only=False)
    n = 0
    for sheet in formulas:
        for row in sheet:
            for cell in row:
                cached = w[sheet.title][cell.coordinate]
                assert cached.data_type != 'e', (sheet.title, cell.coordinate, cached.value)
                if cell.data_type == 'f':
                    n += 1
                    assert cached.value is not None, ('Sin recalcular',sheet.title,cell.coordinate)
    checks = 0
    def same(a,b,where):
        nonlocal checks
        assert isinstance(a,(float,int)) and math.isfinite(a), (where,a)
        assert math.isclose(a,b,abs_tol=1e-5,rel_tol=1e-8), (where,a,b)
        checks+=1
    def inp(k): return w['Supuestos'][META['inputs'][k]].value
    def result(reference):
        sh, c = reference.split('!')
        return w[sh.strip("'")][c].value
    for name,meta in META['cases'].items():
        s=w[meta['sheet']]; z=meta['zero']; first=meta['first']; last=meta['last']
        def val(k,r): return s[f'{META["columns"][k]}{r}'].value
        cash=val('cash',z); cum=val('fcf',z); invcum=val('invflow',z)
        same(val('cashraw',z),inp('inversion')+cum,(name,'mes0'))
        for r in range(first,last+1):
            m=val('month',r)
            fcf=val('ebitda',r)-val('taxcash',r)-val('capex',r)-(val('wc',r)-val('wc',r-1))
            same(val('fcf',r),fcf,(name,m,'fcf'))
            cum+=fcf
            same(val('cumfcf',r),cum,(name,m,'cumulative company'))
            same(val('cashraw',r),inp('inversion')+cum,(name,m,'unfunded cash'))
            extra=max(0,-cash-fcf)
            same(val('extra',r),extra,(name,m,'additional funds'))
            expected_div=0
            available=max(0,val('retained',r-1)+val('net',r)-val('reserve',r))
            if m%12==0:
                expected_div=max(0,min(available*inp('payout'),cash+fcf-inp('cash_months')*(val('team',r)+val('fixed',r))))
            same(val('dividend',r),expected_div,(name,m,'dividends'))
            same(val('retained',r),val('retained',r-1)+val('net',r)-val('reserve',r)-expected_div,(name,m,'retained'))
            cash+=fcf+extra-expected_div
            same(val('cash',r),cash,(name,m,'funded cash'))
            assert cash>=-1e-5,(name,m,'negative funded cash')
            same(val('invextra',r),extra*inp('inv_pct'),(name,m,'investor extra'))
            same(val('invdiv',r),expected_div*inp('inv_pct')*(1-inp('div_ret')),(name,m,'net dividend'))
            flow=val('invdiv',r)+val('sale',r)-extra*inp('inv_pct')
            invcum+=flow
            same(val('invflow',r),flow,(name,m,'net investor flow'))
            same(val('invcum',r),invcum,(name,m,'cumulative investor'))
            same(val('invdisc',r),flow/(1+inp('tasa'))**(m/12),(name,m,'discount timing'))
            if inp('exit_on')==0: same(val('sale',r),0,(name,m,'no hypothetical sale'))
            for k in ('cashaudit','divaudit','invaudit'): same(val(k,r),0,(name,m,k))
            for k in ('fixed','team','points','riderpay','variable','tax','prepay','dep','capex'):
                assert val(k,r)>=-1e-5,(name,m,'negative cost',k,val(k,r))
        loss=0; credit=0
        for y in range(5):
            r=meta['annual']+y; lo=first+y*12; hi=lo+12
            profit=sum(val('ebit',m) for m in range(lo,hi))
            income=sum(val('revenue',m) for m in range(lo,hi))
            offset=min(loss,max(0,profit)*(inp('ir_bpct') if inp('ir_sis')==2 else 1))
            taxable=max(0,profit-offset)
            tax=taxable*inp('ir_gen') if inp('ir_reg')==2 or income>1700*inp('uit') else min(taxable,inp('ir_tramo')*inp('uit'))*inp('ir_rmt')+max(0,taxable-inp('ir_tramo')*inp('uit'))*inp('ir_gen')
            same(s[f'G{r}'].value,tax,(name,y+1,'annual income tax'))
            same(sum(val('tax',m) for m in range(lo,hi)),tax,(name,y+1,'monthly tax accrual'))
            advances=sum(val('prepay',m) for m in range(lo,hi))
            same(sum(val('taxcash',m) for m in range(lo,hi)),advances+max(0,tax-advances-credit),(name,y+1,'cash tax'))
            credit=max(0,credit+advances-tax)
            loss=loss-offset+max(0,-profit)
            same(s[f'H{r}'].value,loss,(name,y+1,'tax loss balance'))
            same(s[f'L{r}'].value,credit,(name,y+1,'tax credit'))
        mm=meta['summary']
        recovered=next((i-first+1 for i in range(first,last+1) if min(val('invcum',j) for j in range(i,last+1))>=0),999)
        same(result(mm['payback']),recovered,(name,'sustained payback'))
        contributed=inp('inversion')+sum(val('invextra',r) for r in range(z,last+1))
        received=sum(val('invdiv',r)+val('sale',r) for r in range(first,last+1))
        same(result(mm['invtotal']),contributed,(name,'total invested'))
        same(result(mm['gain']),received-contributed,(name,'investor net gain'))
        same(result(mm['moic']),received/contributed,(name,'cash MOIC'))
        same(result(mm['npv']),sum(val('invflow',r)/(1+inp('tasa'))**((r-z)/12) for r in range(z,last+1)),(name,'NPV'))
        irr=result(mm['irr'])
        if isinstance(irr,(float,int)):
            residual=sum(val('invflow',r)/(1+irr)**((r-z)/12) for r in range(z,last+1))
            assert abs(residual)<max(1,contributed*1e-5),(name,'IRR does not zero NPV',irr,residual)
        if inp('exit_on'):
            gross=(max(0,sum(val('ebitda',r) for r in range(last-11,last+1)))*inp('mult')+val('cash',last))*inp('inv_pct')*(1-inp('exit_cost'))
            sale=max(0,gross-max(0,gross-contributed)*inp('exit_tax'))
            same(val('sale',last),sale,(name,'terminal equity cash once'))
    # Controles originales de Claude continúan cuadrando después de la extensión.
    for sh in ('Estado de costos','Punto de equilibrio','Resumen','Escenarios inversionistas'):
        s=w[sh]
        for row in s:
            label=row[0].value
            if isinstance(label,str) and ('debe dar 0' in label.lower() or label.startswith('Control: resultado')):
                for c in row[2:5]:
                    if isinstance(c.value,(float,int)): same(c.value,0,(sh,c.coordinate,'legacy control'))
    if 'sin_dividendos' in path.name:
        for k in ('base','plan'):
            same(result(META['metrics'][k]['divs']),0,(k,'dividends disabled'))
            same(result(META['metrics'][k]['payback']),999,(k,'no payout or sale: no recovery'))
    if 'rentable' in path.name:
        assert result(META['metrics']['plan']['divs'])>0,'La prueba rentable debe ejercitar dividendos'
        assert result(META['metrics']['plan']['payback'])<=60,'La prueba rentable debe ejercitar recuperación'
    return {'archivo':path.name,'formulas':n,'comprobaciones':checks,
            'plan':{k:result(v) for k,v in META['metrics']['plan'].items()}}


if __name__=='__main__':
    paths=[Path(p) for p in sys.argv[1:]] or [ROOT/'Ruma_costos_2026-11.xlsx']
    results=[check(p) for p in paths]
    print(json.dumps(results,ensure_ascii=False,indent=2))
