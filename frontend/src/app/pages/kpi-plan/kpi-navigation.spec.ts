import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { AppTopHeaderComponent } from '../../components/app-top-header/app-top-header.component';
import { AuthService } from '../../services/auth.service';
import { EvaluationCycleService } from '../../services/evaluation-cycle.service';
import { routes } from '../../app.routes';

describe('Company KPI navigation', () => {
  it('separates Company management authority from annual administrative authority', () => {
    const auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']);
    auth.hasRole.and.callFake(permission => permission === 'CAN_MANAGE_COMPANY_KPI');
    const menu = new SideMenuComponent(jasmine.createSpyObj<Router>('router', ['navigate']), auth);
    const management = menu.navItems.find(item => item.key === 'NAV.KPI_MANAGEMENT')!;
    const administration = menu.navItems.find(item => item.key === 'NAV.KPI_ADMINISTRATION')!;
    expect(menu.hasAccess(management)).toBeTrue(); expect(menu.hasAccess(administration)).toBeFalse();
    expect(management.children?.[0].route).toBe('/kpi-management/company-kpis');
    expect(administration.children?.some(item => item.route?.includes('company-kpis'))).toBeFalse();
  });
  it('uses KPI Management in the Company page header without loading legacy evaluation cycles', () => {
    const router = jasmine.createSpyObj<Router>('router', ['navigate'], { url: '/kpi-management/company-kpis', events: new Subject() });
    const auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']); auth.hasRole.and.returnValue(false);
    const cycles = jasmine.createSpyObj<EvaluationCycleService>('cycles', ['getCurrentCycle']);
    const header = new AppTopHeaderComponent(router, auth, cycles); header.ngOnInit();
    expect(header.page).toEqual({ title: 'KPI_PLAN.COMPANY_TITLE', group: 'NAV.KPI_MANAGEMENT' });
    expect(cycles.getCurrentCycle).not.toHaveBeenCalled(); header.ngOnDestroy();
  });
});

describe('Department KPI navigation', () => {
  it('shows only the permitted management and review pages', () => {
    const auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']);
    auth.hasRole.and.callFake(permission => permission === 'CAN_MANAGE_DEPARTMENT_KPI');
    const menu = new SideMenuComponent(jasmine.createSpyObj<Router>('router', ['navigate']), auth);
    const management = menu.navItems.find(item => item.key === 'NAV.KPI_MANAGEMENT')!;
    expect(menu.hasAccess(management)).toBeTrue();
    expect(management.children?.filter(item => menu.hasAccess(item)).map(item => item.route)).toEqual(['/kpi-management/department-kpis']);
    auth.hasRole.and.callFake(permission => permission === 'CAN_APPROVE_DEPARTMENT_KPI');
    expect(management.children?.filter(item => menu.hasAccess(item)).map(item => item.route)).toEqual(['/kpi-management/kpi-review']);
    const children = routes.find(route => route.path === '')?.children ?? [];
    expect(children.find(route => route.path === 'kpi-management/department-kpis')?.data?.['requiredRoles']).toEqual(['CAN_MANAGE_DEPARTMENT_KPI']);
    expect(children.find(route => route.path === 'kpi-management/kpi-review')?.data?.['requiredRoles']).toEqual(['CAN_APPROVE_DEPARTMENT_KPI']);
    expect(children.some(route => route.path === 'kpi-management/team-reviews')).toBeFalse();
  });
  it('labels the separate Department pages in the top header', () => {
    const router = jasmine.createSpyObj<Router>('router', ['navigate'], { url: '/kpi-management/kpi-review', events: new Subject() });
    const auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']); auth.hasRole.and.returnValue(false);
    const cycles = jasmine.createSpyObj<EvaluationCycleService>('cycles', ['getCurrentCycle']);
    const header = new AppTopHeaderComponent(router, auth, cycles); header.ngOnInit();
    expect(header.page).toEqual({ title: 'DEPARTMENT_REVIEW.TITLE', group: 'NAV.KPI_MANAGEMENT' });
    expect(cycles.getCurrentCycle).not.toHaveBeenCalled(); header.ngOnDestroy();
  });
});
