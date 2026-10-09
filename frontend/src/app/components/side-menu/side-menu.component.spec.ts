import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { LogoutOutline, MenuUnfoldOutline, UserOutline } from '@ant-design/icons-angular/icons';

import { SideMenuComponent } from './side-menu.component';

describe('SideMenuComponent', () => {
  let component: SideMenuComponent;
  let fixture: ComponentFixture<SideMenuComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SideMenuComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: AuthService, useValue: { hasRole: () => false } },
        { provide: NZ_ICONS, useValue: [LogoutOutline, MenuUnfoldOutline, UserOutline] }]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SideMenuComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
  it('exposes Team Reviews with either review authority, but not ROLE_USER alone', () => {
    const item = component.navItems.find(item => item.key === 'NAV.TEAM_PERFORMANCE')!;
    const auth = TestBed.inject(AuthService);
    const hasRole = spyOn(auth, 'hasRole');
    for (const permission of ['CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_INDIVIDUAL_KPI']) {
      hasRole.and.callFake(role => role === permission);
      expect(component.hasAccess(item)).toBeTrue(); expect(component.hasVisibleChildren(item)).toBeTrue();
    }
    hasRole.and.callFake(role => role === 'ROLE_USER');
    expect(component.hasAccess(item)).toBeFalse(); expect(component.hasVisibleChildren(item)).toBeFalse();
  });
});
