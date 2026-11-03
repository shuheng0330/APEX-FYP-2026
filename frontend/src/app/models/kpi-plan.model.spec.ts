import { KpiItem, emptyKpiItem, kpiPlanComplete } from './kpi-plan.model';
describe('KPI plan completeness', () => {
  function item(weight: number): KpiItem {
    return { ...emptyKpiItem(), name: 'Sales', perspective: 'Financial', kra: 'Revenue Growth', target: '8%', weightage: weight,
      scoringDefinitions: { 1: 'One', 2: 'Two', 3: 'Three', 4: 'Four', 5: 'Five' } };
  }
  it('allows an empty Draft but not submission', () => expect(kpiPlanComplete([])).toBeFalse());
  it('requires exactly 100%', () => { expect(kpiPlanComplete([item(100)])).toBeTrue(); expect(kpiPlanComplete([item(99.99)])).toBeFalse(); });
  it('requires all five criteria', () => { const i = item(100); i.scoringDefinitions[5] = ''; expect(kpiPlanComplete([i])).toBeFalse(); });
  it('does not require a removed Measurement Unit field', () => expect(kpiPlanComplete([item(100)])).toBeTrue());
  it('requires Perspective and manually entered KRA for UI publication', () => {
    const i = item(100); i.perspective = null; expect(kpiPlanComplete([i])).toBeFalse();
    i.perspective = 'Financial'; i.kra = null; expect(kpiPlanComplete([i])).toBeFalse();
  });
  it('uses exact hundredths without rounding away invalid decimals', () => expect(kpiPlanComplete([item(100.001)])).toBeFalse());
  it('rejects duplicate KPI names', () => expect(kpiPlanComplete([item(50), item(50)])).toBeFalse());
});
