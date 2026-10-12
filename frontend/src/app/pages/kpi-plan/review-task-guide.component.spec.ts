import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline } from '@ant-design/icons-angular/icons';
import { AuthService } from '../../services/auth.service';
import { ReviewTaskGuideComponent } from './review-task-guide.component';
import { reviewLayout } from './review-navigation.test-layout';

@Component({ standalone: true, template: '' })
class Destination {}

describe('Where Should I Go guide', () => {
  let fixture: ComponentFixture<ReviewTaskGuideComponent>;
  let permissions: string[];
  beforeEach(async () => {
    permissions = ['ROLE_USER', 'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE', 'CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS', 'CAN_REVIEW_ATTITUDE_EVALUATION'];
    TestBed.configureTestingModule({ imports: [ReviewTaskGuideComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), provideRouter([{ path: '**', component: Destination }]),
      { provide: AuthService, useValue: { hasRole: (p: string) => permissions.includes(p) } },
      { provide: NZ_ICONS, useValue: [CloseOutline] }
    ] });
    await TestBed.compileComponents(); fixture = TestBed.createComponent(ReviewTaskGuideComponent); fixture.detectChanges();
  });
  function open() {
    fixture.nativeElement.querySelector('button').click(); fixture.detectChanges();
    return document.querySelector<HTMLElement>('.ant-modal-body .task-guide')!;
  }
  it('lists short authorised tasks with the correct workspace and tab links', () => {
    const guide = open();
    expect(guide.querySelectorAll('li').length).toBe(5);
    expect(guide.textContent).toContain('REVIEW_NAV.TASK.ASSISTANCE_DECISION');
    expect(guide.textContent).not.toContain('REVIEW_NAV.TASK.INDIVIDUAL_APPROVAL');
    expect(guide.textContent).not.toContain('REVIEW_NAV.SAME_REQUEST');
    expect([...guide.querySelectorAll('a')].map(a => a.getAttribute('href'))).toContain('/kpi-management/kpi-review?tab=assistance');
    expect([...guide.querySelectorAll('a')].map(a => a.getAttribute('href'))).toContain('/team-performance/team-reviews?tab=attitude');
  });
  it('explains shared revisions only for users with responsibilities in both workspaces', () => {
    permissions.push('CAN_REVIEW_INDIVIDUAL_KPI');
    expect(open().textContent).toContain('REVIEW_NAV.SAME_REQUEST');
  });
  it('uses focusable native links and closes after navigation or the Close action', async () => {
    const guide = open(); const link = guide.querySelector<HTMLAnchorElement>('a')!;
    link.focus(); expect(document.activeElement).toBe(link); expect(link.tabIndex).toBe(0);
    link.click(); await fixture.whenStable(); fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/kpi-management/kpi-review?tab=assistance');
    expect(fixture.componentInstance.visible).toBeFalse();
    open(); document.querySelector<HTMLButtonElement>('.ant-modal-footer button')!.click(); fixture.detectChanges();
    expect(fixture.componentInstance.visible).toBeFalse();
  });
  it('handles no tasks and a disabled trigger without business API calls', () => {
    permissions = []; expect(open().textContent).toContain('REVIEW_NAV.NO_TASKS');
    fixture.componentInstance.visible = false; fixture.componentInstance.disabled = true; fixture.detectChanges();
    fixture.nativeElement.querySelector('button').click(); expect(fixture.componentInstance.visible).toBeFalse();
  });
  it('closes the guide when the user chooses the tab they are already viewing', async () => {
    await TestBed.inject(Router).navigateByUrl('/kpi-management/kpi-review?tab=assistance');
    const guide = open(); guide.querySelector<HTMLAnchorElement>('a')!.click(); await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.componentInstance.visible).toBeFalse();
    expect(TestBed.inject(Router).url).toBe('/kpi-management/kpi-review?tab=assistance');
  });
  it('wraps long text and scrolls at desktop, tablet and mobile widths', () => {
    permissions = ['ROLE_USER', 'CAN_REVIEW_INDIVIDUAL_KPI', 'CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_ATTITUDE_EVALUATION',
      'CAN_MANAGE_COMPANY_KPI', 'CAN_REVISE_COMPANY_KPI', 'CAN_MANAGE_DEPARTMENT_KPI', 'CAN_APPROVE_DEPARTMENT_KPI',
      'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE', 'CAN_MANAGE_KPI_AMENDMENTS', 'CAN_PREPARE_DEPARTMENT_KPI_REVISION', 'CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS'];
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('test', { REVIEW_NAV: { TASK: { COMPANY: 'Long translated task description '.repeat(10) } } }); translate.use('test');
    const guide = open();
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(guide, width);
      try {
        expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        expect(layout.window.getComputedStyle(layout.root).overflowY).toBe('auto');
        expect(layout.root.scrollHeight).toBeGreaterThan(layout.root.clientHeight);
        const row = layout.root.querySelector('li')!;
        const columns = layout.window.getComputedStyle(row).gridTemplateColumns.split(' ');
        expect(columns.length).toBe(width <= 600 ? 1 : 2);
      } finally { layout.remove(); }
    }
  });
});
