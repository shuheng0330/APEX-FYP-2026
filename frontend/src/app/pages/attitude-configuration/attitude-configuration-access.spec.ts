import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, provideRouter } from '@angular/router';
import { PermissionGuard } from '../../guards/permission.guard';
import { AuthService } from '../../services/auth.service';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { routes } from '../../app.routes';
import { ATTITUDE_CONFIGURATION_PERMISSION } from '../../models/attitude-configuration.model';

describe('Attitude configuration access', () => {
  let auth: jasmine.SpyObj<AuthService>;
  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']);
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });
  it('protects the actual route with the configuration permission and unsaved-change guard', () => {
    const route = routes.find(r => r.path === '')!.children!.find(r => r.path === 'kpi-administration/attitude-evaluation-setup')!;
    expect(route.data?.['requiredRoles']).toEqual([ATTITUDE_CONFIGURATION_PERMISSION]); expect(route.canDeactivate?.length).toBe(1);
    const snapshot = new ActivatedRouteSnapshot(); snapshot.data = route.data!;
    auth.hasRole.and.callFake(p => p === 'ROLE_USER' || p === 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD');
    expect(TestBed.inject(PermissionGuard).canActivate(snapshot)).toBeFalse();
    auth.hasRole.and.callFake(p => p === ATTITUDE_CONFIGURATION_PERMISSION);
    expect(TestBed.inject(PermissionGuard).canActivate(snapshot)).toBeTrue();
  });
  it('shows KPI Administration for configuration-only administrators without granting period management', () => {
    const menu = new SideMenuComponent(TestBed.inject(Router), auth);
    const parent = menu.navItems.find(item => item.key === 'NAV.KPI_ADMINISTRATION')!;
    const attitude = parent.children!.find(item => item.key === 'ATTITUDE_SETUP.TITLE')!;
    const period = parent.children!.find(item => item.key === 'NAV.ANNUAL_REVIEW_PERIOD')!;
    auth.hasRole.and.callFake(p => p === ATTITUDE_CONFIGURATION_PERMISSION);
    expect(menu.hasAccess(parent)).toBeTrue(); expect(menu.hasAccess(attitude)).toBeTrue(); expect(menu.hasAccess(period)).toBeFalse();
    auth.hasRole.and.callFake(p => p === 'ROLE_SUPER_ADMIN'); expect(menu.hasAccess(attitude)).toBeFalse();
  });
});
