import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { routes } from '../app.routes';
import { AuthService } from '../services/auth.service';
import { AttitudeConfigurationService } from '../services/attitude-configuration.service';
import { AccessDeniedComponent } from './access-denied.component';

describe('Permission denial destination', () => {
  it('shows the denial view for an authenticated user without Setup permission and does not load configuration data', async () => {
    const api = jasmine.createSpyObj<AttitudeConfigurationService>('api', ['list', 'optionsList']);
    await TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()], providers: [
      provideRouter(routes), provideNoopAnimations(),
      { provide: AuthService, useValue: { loggedIn$: new BehaviorSubject(true), hasRole: (role: string) => role === 'ROLE_USER' } },
      { provide: AttitudeConfigurationService, useValue: api }
    ] }).compileComponents();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/kpi-administration/attitude-evaluation-setup');
    await harness.fixture.whenStable(); harness.detectChanges();
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(AccessDeniedComponent);
    expect(harness.routeNativeElement?.textContent).toContain('ACCESS_DENIED.TITLE');
    expect(harness.routeNativeElement?.querySelector('a')?.getAttribute('href')).toBe('/org-chart');
    expect(api.list).not.toHaveBeenCalled(); expect(api.optionsList).not.toHaveBeenCalled();
  });
});
