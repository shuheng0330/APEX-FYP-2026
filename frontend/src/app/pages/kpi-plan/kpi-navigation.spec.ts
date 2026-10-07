import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { AppTopHeaderComponent } from '../../components/app-top-header/app-top-header.component';
import { AuthService } from '../../services/auth.service';
import { EvaluationCycleService } from '../../services/evaluation-cycle.service';

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
