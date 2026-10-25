import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { BellOutline, DownOutline, LogoutOutline, MenuFoldOutline, MenuOutline, MenuUnfoldOutline, RightOutline, UserOutline } from '@ant-design/icons-angular/icons';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';
import { LoadingService } from './services/loading.service';
import { EvaluationCycleService } from './services/evaluation-cycle.service';
import { NotificationService } from './services/notification.service';

describe('App shell', () => {
  const cycles = { getCurrentCycle: jasmine.createSpy('getCurrentCycle') };

  beforeEach(async () => {
    cycles.getCurrentCycle.calls.reset();
    await TestBed.configureTestingModule({
      imports: [AppComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideNoopAnimations(),
        { provide: AuthService, useValue: { loggedIn$: of(true), hasRole: (p: string) => p === 'CAN_MANAGE_EVALUATION_CYCLE' } },
        { provide: LoadingService, useValue: { loading$: of(false) } },
        { provide: EvaluationCycleService, useValue: cycles },
        { provide: NotificationService, useValue: { unreadCount: () => of(0) } },
        { provide: NZ_ICONS, useValue: [BellOutline, DownOutline, LogoutOutline, MenuFoldOutline, MenuOutline, MenuUnfoldOutline, RightOutline, UserOutline] }
      ]
    }).compileComponents();
  });

  it('retains the signed-in navigation, header, notifications and routed workspace', () => {
    const fixture = TestBed.createComponent(AppComponent); fixture.detectChanges();
    for (const selector of ['app-side-menu', 'app-top-header', 'app-notification-bell', 'router-outlet']) {
      expect(fixture.nativeElement.querySelector(selector)).not.toBeNull();
    }
    fixture.destroy();
  });

  it('does not mount or fetch the retired cycle setup, including on mobile', () => {
    const fixture = TestBed.createComponent(AppComponent); fixture.detectChanges();
    fixture.componentInstance.openDrawer(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-evaluation-cycle-drawer')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cycle-button')).toBeNull();
    expect(cycles.getCurrentCycle).not.toHaveBeenCalled();
    fixture.componentInstance.closeDrawer(); fixture.destroy();
  });
});
