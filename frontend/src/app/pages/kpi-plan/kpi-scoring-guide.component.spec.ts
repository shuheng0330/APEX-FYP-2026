import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline } from '@ant-design/icons-angular/icons';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';

describe('Shared KPI Scoring Guide', () => {
  let fixture: ComponentFixture<KpiScoringGuideComponent>;
  const labels = ['Exceeds expectation', 'Meets expectation', 'Partially meets expectation', 'Below expectation', 'Unsatisfactory'];
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiScoringGuideComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), { provide: NZ_ICONS, useValue: [CloseOutline] }]
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      KPI_SCORING_GUIDE: { VIEW: 'View Scoring Guide', TITLE: 'KPI Scoring Guide',
        RATING: Object.fromEntries(labels.map((LABEL, index) => [5 - index, { LABEL, DESCRIPTION: 'Meaning for point ' + (5 - index) }])),
        NOTE: 'Use the KPI-specific scoring definitions, not fixed percentage thresholds.' },
      KPI_PLAN: { CLOSE: 'Close' }
    });
    translate.use('en');
    fixture = TestBed.createComponent(KpiScoringGuideComponent); fixture.detectChanges();
  });
  async function open() {
    fixture.nativeElement.querySelector('button').click(); fixture.detectChanges();
    await fixture.whenStable(); fixture.detectChanges();
  }
  it('opens a readable 5-to-1 guide with the shared ratings and KPI-specific threshold note', async () => {
    expect(document.querySelector('.rating-list')).toBeNull();
    await open();
    const rows = Array.from(document.querySelectorAll('.rating-row'));
    expect(rows.map(row => row.querySelector('.rating-point')?.textContent?.trim())).toEqual(['5', '4', '3', '2', '1']);
    expect(rows.map(row => row.querySelector('strong')?.textContent?.trim())).toEqual(labels);
    expect(rows.every(row => !!row.querySelector('p')?.textContent?.trim())).toBeTrue();
    expect(document.querySelector('.rating-note')?.textContent).toContain('not fixed percentage thresholds');
  });
  it('closes and reopens through the visible controls', async () => {
    await open();
    const close: HTMLButtonElement = document.querySelector('.ant-modal-footer button')!;
    close.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(fixture.componentInstance.visible).toBeFalse();
    await open(); expect(fixture.componentInstance.visible).toBeTrue();
    const cancel: HTMLButtonElement = document.querySelector('.ant-modal-close')!;
    cancel.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(fixture.componentInstance.visible).toBeFalse();
  });
});
