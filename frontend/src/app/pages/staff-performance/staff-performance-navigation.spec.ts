import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { routes } from '../../app.routes';
import { SideMenuComponent } from '../../components/side-menu/side-menu.component';
import { AppTopHeaderComponent } from '../../components/app-top-header/app-top-header.component';
import { AuthService } from '../../services/auth.service';
import { performanceViewPermissions } from './staff-performance-navigation';

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
});
