import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { KpiChangesComponent } from './kpi-changes.component';

describe('KPI revision change summary', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [KpiChangesComponent, TranslateModule.forRoot()] }));
  it('counts edited fields separately from added/removed KPIs and expands only the chosen KPI', () => {
    const fixture = TestBed.createComponent(KpiChangesComponent); const page = fixture.componentInstance;
    fixture.componentRef.setInput('summaryTable', true);
    fixture.componentRef.setInput('changes', [
      { type: 'EDITED', itemName: 'Uptime', itemId: 1, fields: [{ field: 'target', previousValue: '95%', currentValue: '99%' }] },
      { type: 'ADDED', itemName: 'New KPI', itemId: 2, fields: [{ field: 'name', previousValue: null, currentValue: 'New KPI' }] },
      { type: 'REMOVED', itemName: 'Old KPI', itemId: 3, fields: [] }
    ]); fixture.detectChanges();
    expect(page.editedFields).toBe(1); expect(page.addedCount).toBe(1); expect(page.removedCount).toBe(1);
    expect(fixture.nativeElement.querySelector('.comparison')).toBeNull();
    fixture.nativeElement.querySelector('button').click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.comparison').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('95%'); expect(fixture.nativeElement.textContent).toContain('99%');
    fixture.nativeElement.querySelector('button').click(); fixture.detectChanges(); expect(page.expanded.size).toBe(0);
  });
  it('preserves the original expanded feedback presentation by default', () => {
    const fixture = TestBed.createComponent(KpiChangesComponent);
    fixture.componentRef.setInput('changes', [{ type: 'EDITED', itemName: 'KPI', itemId: 1, fields: [] }]); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('details[open]')).not.toBeNull();
  });
});
