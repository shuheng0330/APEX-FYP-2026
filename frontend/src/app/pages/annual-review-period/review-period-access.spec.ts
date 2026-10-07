import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, provideRouter } from '@angular/router';
import { PermissionGuard } from '../../guards/permission.guard';
import { AuthService } from '../../services/auth.service';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { ANNUAL_REVIEW_PERMISSION } from '../../models/annual-review-period.model';

describe('Annual review period permission', () => {
  let auth: jasmine.SpyObj<AuthService>;
  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']);
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });
  it('requires the annual permission, not the legacy Evaluation Cycle permission', () => {
    const route = new ActivatedRouteSnapshot(); route.data = { requiredRoles: [ANNUAL_REVIEW_PERMISSION] };
    auth.hasRole.and.callFake(permission => permission === 'CAN_MANAGE_EVALUATION_CYCLE');
    expect(TestBed.inject(PermissionGuard).canActivate(route)).toBeFalse();
    auth.hasRole.and.callFake(permission => permission === ANNUAL_REVIEW_PERMISSION);
    expect(TestBed.inject(PermissionGuard).canActivate(route)).toBeTrue();
  });
  it('shows the new menu only with the matching authority', () => {
    const menu = new SideMenuComponent(TestBed.inject(Router), auth);
    const item = menu.navItems.find(item => item.key === 'NAV.KPI_ADMINISTRATION')!;
    auth.hasRole.and.returnValue(false); expect(menu.hasVisibleChildren(item)).toBeFalse();
    auth.hasRole.and.callFake(permission => permission === ANNUAL_REVIEW_PERMISSION); expect(menu.hasVisibleChildren(item)).toBeTrue();
  });
  it('retains access to legacy child navigation without adding new parent restrictions', () => {
    const menu = new SideMenuComponent(TestBed.inject(Router), auth);
    const item = menu.navItems.find(item => item.key === 'NAV.ORGANISATION_MANAGEMENT')!;
    auth.hasRole.and.callFake(permission => permission === 'CAN_MANAGE_ROLE');
    expect(menu.hasVisibleChildren(item)).toBeTrue();
  });
});
