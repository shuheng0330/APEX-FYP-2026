import { emptyKpiItem, kpiPlanComplete } from './kpi-plan.model';
describe('KPI plan completeness', () => {
  function item(weight: number) {
    return { ...emptyKpiItem(), name: 'Sales', target: '8%', measurementUnit: '%', weightage: weight,
      scoringDefinitions: { 1: 'One', 2: 'Two', 3: 'Three', 4: 'Four', 5: 'Five' } };
  }
  it('allows an empty Draft but not submission', () => expect(kpiPlanComplete([])).toBeFalse());
  it('requires exactly 100%', () => { expect(kpiPlanComplete([item(100)])).toBeTrue(); expect(kpiPlanComplete([item(99.99)])).toBeFalse(); });
  it('requires all five criteria', () => { const i = item(100); i.scoringDefinitions[5] = ''; expect(kpiPlanComplete([i])).toBeFalse(); });
  it('uses exact hundredths without rounding away invalid decimals', () => expect(kpiPlanComplete([item(100.001)])).toBeFalse());
  it('rejects duplicate KPI names', () => expect(kpiPlanComplete([item(50), item(50)])).toBeFalse());
});
