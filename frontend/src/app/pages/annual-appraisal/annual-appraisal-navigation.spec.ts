import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { MenuOutline, BellOutline, UserOutline, LogoutOutline, RightOutline, DownOutline } from '@ant-design/icons-angular/icons';
import { routes } from '../../app.routes';
import { AuthService } from '../../services/auth.service';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { AppTopHeaderComponent } from '../../components/app-top-header/app-top-header.component';
import { EvaluationCycleService } from '../../services/evaluation-cycle.service';
import { reviewTasks } from '../kpi-plan/review-task-directory';

describe('Annual appraisal navigation', () => {
  const grants: string[] = [];
  const auth = { hasRole: (p: string) => grants.includes(p) };
  beforeEach(() => {
    grants.length = 0;
    TestBed.configureTestingModule({ imports: [SideMenuComponent, AppTopHeaderComponent, TranslateModule.forRoot()], providers: [
      provideRouter([]), provideNoopAnimations(), { provide: AuthService, useValue: auth }, { provide: EvaluationCycleService, useValue: {} },
      { provide: NZ_ICONS, useValue: [MenuOutline, BellOutline, UserOutline, LogoutOutline, RightOutline, DownOutline] }
    ] });
  });
  it('protects the two routes independently, including unsaved-change guards', () => {
    const children = routes.find(r => r.path === '')!.children!;
    for (const [path, permission, hrReview] of [
      ['team-performance/team-appraisals', 'CAN_SUBMIT_ANNUAL_APPRAISAL', false],
      ['appraisals/review', 'CAN_REVIEW_ANNUAL_APPRAISAL', true],
      ['team-performance/team-appraisals/:reviewPeriodId/:participantId', 'CAN_SUBMIT_ANNUAL_APPRAISAL', false],
      ['appraisals/review/:appraisalId', 'CAN_REVIEW_ANNUAL_APPRAISAL', true]
    ] as const) {
      const route = children.find(r => r.path === path)!;
      expect(route.data!['requiredRoles']).toEqual([permission]); expect(route.data!['hrReview']).toBe(hrReview);
      expect(route.canDeactivate?.length).toBe(1); expect(route.canActivate?.length).toBe(1);
    }
    for (const path of ['evaluation/overview', 'evaluation/org-overview', 'evaluation/my-evaluation', 'performance/:staffId']) {
      expect(children.some(r => r.path === path)).toBeTrue();
    }
  });
  it('shows each granted workspace and both when combined, without role-name inference', () => {
    const component = TestBed.createComponent(SideMenuComponent).componentInstance;
    const team = component.navItems.find(i => i.key === 'NAV.TEAM_PERFORMANCE')!;
    const hr = component.navItems.find(i => i.key === 'NAV.PERFORMANCE_APPRAISAL')!;
    const teamItem = team.children!.find(i => i.key === 'ANNUAL_APPRAISAL.TEAM_TITLE')!;
    const hrItem = hr.children!.find(i => i.key === 'ANNUAL_APPRAISAL.HR_TITLE')!;
    grants.push('Super Admin', 'HR', 'MD', 'ROLE_USER'); expect(component.hasAccess(teamItem)).toBeFalse(); expect(component.hasAccess(hrItem)).toBeFalse();
    grants.push('CAN_SUBMIT_ANNUAL_APPRAISAL'); expect(component.hasAccess(team)).toBeTrue(); expect(component.hasAccess(teamItem)).toBeTrue();
    expect(component.hasAccess(hrItem)).toBeFalse(); grants.push('CAN_REVIEW_ANNUAL_APPRAISAL');
    expect(component.hasAccess(hr)).toBeTrue(); expect(component.hasAccess(hrItem)).toBeTrue();
    expect(reviewTasks(auth.hasRole).filter(t => t.id.startsWith('ANNUAL_')).map(t => t.path))
      .toEqual(['/team-performance/team-appraisals', '/appraisals/review']);
  });
  it('uses the same page names in top-header context after direct navigation', async () => {
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(AppTopHeaderComponent);
    router.resetConfig([{ path: 'appraisals/review/:appraisalId', children: [] }, { path: 'team-performance/team-appraisals/:reviewPeriodId/:participantId', children: [] },
      { path: 'appraisals/review', children: [] }, { path: 'team-performance/team-appraisals', children: [] }]);
    await router.navigateByUrl('/appraisals/review'); fixture.detectChanges();
    expect(fixture.componentInstance.page.title).toBe('ANNUAL_APPRAISAL.HR_TITLE');
    await router.navigateByUrl('/team-performance/team-appraisals');
    expect(fixture.componentInstance.page.title).toBe('ANNUAL_APPRAISAL.TEAM_TITLE');
    await router.navigateByUrl('/team-performance/team-appraisals/2/7');
    expect(fixture.componentInstance.page.title).toBe('ANNUAL_APPRAISAL.DETAIL_TITLE');
    await router.navigateByUrl('/appraisals/review/10');
    expect(fixture.componentInstance.page.title).toBe('ANNUAL_APPRAISAL.REVIEW_DETAIL_TITLE'); fixture.destroy();
  });
});
