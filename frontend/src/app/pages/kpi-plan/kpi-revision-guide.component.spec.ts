import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { KpiRevisionGuideComponent } from './kpi-revision-guide.component';

describe('KPI revision workflow guide', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [KpiRevisionGuideComponent, TranslateModule.forRoot()],
    providers: [provideNoopAnimations()] }));
  it('opens a guide for all levels and both Individual preparation paths without loading business data', () => {
    const fixture = TestBed.createComponent(KpiRevisionGuideComponent); fixture.detectChanges();
    fixture.nativeElement.querySelector('button').click(); fixture.detectChanges();
    expect(fixture.componentInstance.visible).toBeTrue();
    const body = document.querySelector('.ant-modal-body')!;
    expect(body.querySelectorAll('section').length).toBe(4);
    expect(body.querySelectorAll('aside li').length).toBe(3);
    expect(body.textContent).toContain('KPI_REVISIONS.GUIDE.INTRO');
    expect(body.textContent).toContain('KPI_REVISIONS.GUIDE.CLOSED');
    expect(body.textContent).toContain('KPI_REVISIONS.GUIDE.RETURNED');
    for (const key of ['COMPANY_TITLE', 'DEPARTMENT_TITLE', 'INDIVIDUAL_SUPERIOR_TITLE', 'INDIVIDUAL_HR_TITLE', 'STATUSES']) {
      expect(body.textContent).toContain('KPI_REVISIONS.GUIDE.' + key);
    }
    document.querySelector<HTMLButtonElement>('.ant-modal-footer button')!.click(); fixture.detectChanges();
    expect(fixture.componentInstance.visible).toBeFalse();
  });
});
