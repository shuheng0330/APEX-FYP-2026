import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { FundOutline, LogoutOutline, MenuFoldOutline, MenuUnfoldOutline, UserOutline } from '@ant-design/icons-angular/icons';

import { SideMenuComponent } from './side-menu.component';

describe('SideMenuComponent', () => {
  let component: SideMenuComponent;
  let fixture: ComponentFixture<SideMenuComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SideMenuComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), provideRouter([]), { provide: AuthService, useValue: { hasRole: () => false } },
        { provide: NZ_ICONS, useValue: [FundOutline, LogoutOutline, MenuFoldOutline, MenuUnfoldOutline, UserOutline] }]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SideMenuComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
  it('exposes Team Reviews with any review authority, but not ROLE_USER alone', () => {
    const item = component.navItems.find(item => item.key === 'NAV.TEAM_PERFORMANCE')!;
    const auth = TestBed.inject(AuthService);
    const hasRole = spyOn(auth, 'hasRole');
    for (const permission of ['CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_INDIVIDUAL_KPI', 'CAN_REVIEW_ATTITUDE_EVALUATION']) {
      hasRole.and.callFake(role => role === permission);
      expect(component.hasAccess(item)).toBeTrue(); expect(component.hasVisibleChildren(item)).toBeTrue();
    }
    hasRole.and.callFake(role => role === 'ROLE_USER');
    expect(component.hasAccess(item)).toBeFalse(); expect(component.hasVisibleChildren(item)).toBeFalse();
  });
  it('keeps the existing review route and lets its longer label wrap in the expanded menu', () => {
    spyOn(TestBed.inject(AuthService), 'hasRole').and.callFake(p => p === 'CAN_APPROVE_DEPARTMENT_KPI');
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { DEPARTMENT_REVIEW: { NAV_TITLE: 'KPI Approvals & Revisions' } }); translate.use('en');
    component.isCollapsed = false; fixture.detectChanges();
    const item = fixture.nativeElement.querySelector('.review-workflow-link') as HTMLElement;
    expect(item).not.toBeNull(); expect(item.textContent).toContain('KPI Approvals & Revisions');
    expect(getComputedStyle(item).whiteSpace).toBe('normal');
    expect(component.navItems.find(i => i.key === 'NAV.KPI_MANAGEMENT')?.children?.find(i => i.key === 'DEPARTMENT_REVIEW.NAV_TITLE')?.route)
      .toBe('/kpi-management/kpi-review');
  });
});
