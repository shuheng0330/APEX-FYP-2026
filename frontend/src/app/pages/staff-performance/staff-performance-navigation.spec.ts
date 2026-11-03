import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { routes } from '../../app.routes';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { AppTopHeaderComponent } from '../../components/app-top-header/app-top-header.component';
import { AuthService } from '../../services/auth.service';
import { performanceViewPermissions } from './staff-performance-navigation';
import { performanceReturnUrl } from './staff-performance-navigation';
import { PermissionGuard } from '../../guards/permission.guard';

describe('Performance dashboard navigation', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()], providers: [
    provideRouter([]), { provide: AuthService, useValue: { hasRole: (permission: string) => permission === 'ROLE_USER' } }
  ] }));
  it('places the self dashboard first under My Performance and uses dedicated scope permissions for Staff', () => {
    const children = routes.find(route => route.path === '')!.children!;
    expect(children.find(route => route.path === 'my-performance/dashboard')?.data?.['requiredRoles']).toEqual(['ROLE_USER']);
    expect(children.find(route => route.path === 'my-performance/staff/:employeeId')?.data?.['requiredRoles']).toEqual(performanceViewPermissions);
    const menu = new SideMenuComponent(TestBed.inject(Router), TestBed.inject(AuthService));
    expect(menu.navItems.find(item => item.key === 'NAV.MY_PERFORMANCE')?.children?.[0].route).toBe('/my-performance/dashboard');
  });
  it('uses clear shell titles for both routes without bringing back the competency dashboard', () => {
    const header = new AppTopHeaderComponent(TestBed.inject(Router), TestBed.inject(AuthService));
    const router = TestBed.inject(Router);
    spyOnProperty(router, 'url').and.returnValue('/my-performance/dashboard'); header.ngOnInit();
    expect(header.page.title).toBe('STAFF_PERFORMANCE.MY_TITLE'); expect(header.page.group).toBe('NAV.MY_PERFORMANCE'); header.ngOnDestroy();
    const children = routes.find(route => route.path === '')!.children!;
    expect(children.some(route => route.path === 'performance/:staffId')).toBeFalse();
  });
  it('places Team Performance first in its group and guards it with viewing permissions', () => {
    const route = routes.find(r => r.path === '')!.children!.find(r => r.path === 'team-performance/dashboard')!;
    expect(route.data?.['requiredRoles']).toEqual(performanceViewPermissions); expect(route.canActivate).toContain(PermissionGuard);
    const auth = TestBed.inject(AuthService); const role = spyOn(auth, 'hasRole');
    const menu = new SideMenuComponent(TestBed.inject(Router), auth); const group = menu.navItems.find(i => i.key === 'NAV.TEAM_PERFORMANCE')!;
    expect(group.children?.[0].route).toBe('/team-performance/dashboard');
    for (const permission of performanceViewPermissions) {
      role.and.callFake(p => p === permission); expect(menu.hasAccess(group)).toBeTrue(); expect(menu.hasAccess(group.children![0])).toBeTrue();
      expect(menu.hasAccess(group.children![1])).toBeFalse();
    }
    role.and.callFake(p => p === 'ROLE_USER'); expect(menu.hasAccess(group.children![0])).toBeFalse();
  });
  it('labels the Team shell and accepts only its exact local dashboard return path', () => {
    const router = TestBed.inject(Router); spyOnProperty(router, 'url').and.returnValue('/team-performance/dashboard?scope=DEPARTMENT&departmentId=8');
    const header = new AppTopHeaderComponent(router, TestBed.inject(AuthService)); header.ngOnInit();
    expect(header.page.title).toBe('TEAM_PERFORMANCE.TITLE'); expect(header.page.group).toBe('NAV.TEAM_PERFORMANCE'); header.ngOnDestroy();
    const safe = '/team-performance/dashboard?scope=TEAM&reviewPeriodId=1&search=Bob'; expect(performanceReturnUrl(safe)).toBe(safe);
    for (const path of ['//evil.test/team-performance/dashboard', '/team-performance/dashboard/extra', '/team-performance/dashboard-evil']) expect(performanceReturnUrl(path)).toBe('/my-performance/dashboard');
  });
});
