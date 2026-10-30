import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { routes } from '../../app.routes';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { AppTopHeaderComponent } from '../../components/app-top-header/app-top-header.component';
import { AuthService } from '../../services/auth.service';
import { PermissionGuard } from '../../guards/permission.guard';
import { organisationPerformancePath, organisationReturnUrl } from './organisation-performance-navigation';

describe('Organisation Performance navigation', () => {
  const grants: string[] = [];
  beforeEach(() => {
    grants.length = 0;
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()], providers: [provideRouter([]), { provide: AuthService, useValue: { hasRole: (p: string) => grants.includes(p) } }] });
  });
  it('uses the dedicated permission for the lazy route and menu under Performance & Appraisal', async () => {
    const route = routes.find(r => r.path === '')!.children!.find(r => r.path === organisationPerformancePath.slice(1))!;
    expect(route.canActivate).toContain(PermissionGuard); expect(route.data?.['requiredRoles']).toEqual(['CAN_VIEW_ORGANISATION_PERFORMANCE']);
    expect(route.loadComponent).toBeDefined();
    const router = TestBed.inject(Router), auth = TestBed.inject(AuthService), menu = new SideMenuComponent(router, auth);
    const group = menu.navItems.find(i => i.key === 'NAV.PERFORMANCE_APPRAISAL')!, item = group.children!.find(i => i.route === organisationPerformancePath)!;
    const snapshot = new ActivatedRouteSnapshot(); snapshot.data = route.data!; const guard = new PermissionGuard(auth, router); spyOn(router, 'navigate').and.resolveTo(true);
    for (const permission of ['ROLE_USER', 'HR', 'Super Admin', 'CAN_REVIEW_ANNUAL_APPRAISAL', 'CAN_VIEW_TEAM_PERFORMANCE', 'CAN_VIEW_DEPARTMENT_PERFORMANCE']) {
      grants.splice(0, grants.length, permission); expect(menu.hasAccess(item)).toBeFalse(); expect(guard.canActivate(snapshot)).toBeFalse();
    }
    grants.splice(0, grants.length, 'CAN_VIEW_ORGANISATION_PERFORMANCE'); expect(menu.hasAccess(group)).toBeTrue(); expect(menu.hasAccess(item)).toBeTrue(); expect(guard.canActivate(snapshot)).toBeTrue();
    expect(menu.hasAccess(group.children!.find(i => i.route === '/appraisals/review')!)).toBeFalse();
  });
  it('uses a matching shell title and rejects external or non-dashboard return paths', () => {
    const router = TestBed.inject(Router); spyOnProperty(router, 'url').and.returnValue(organisationPerformancePath + '?reviewPeriodId=8');
    const header = new AppTopHeaderComponent(router, TestBed.inject(AuthService)); header.ngOnInit();
    expect(header.page.title).toBe('ORG_PERFORMANCE.TITLE'); expect(header.page.group).toBe('NAV.PERFORMANCE_APPRAISAL'); header.ngOnDestroy();
    const safe = organisationPerformancePath + '?reviewPeriodId=8&metric=KPI&search=IT'; expect(organisationReturnUrl(safe)).toBe(safe);
    for (const value of ['//evil.test', 'https://evil.test', organisationPerformancePath + '/extra', organisationPerformancePath + '#hash', '/appraisals/review', '/appraisals/organisation-performance\\evil', '/appraisals/organisation-performance extra']) expect(organisationReturnUrl(value)).toBeNull();
  });
});
