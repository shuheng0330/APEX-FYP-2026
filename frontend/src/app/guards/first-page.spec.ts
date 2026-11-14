import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, firstValueFrom, of } from 'rxjs';
import { routes } from '../app.routes';
import { AuthService } from '../services/auth.service';
import { LoadingService } from '../services/loading.service';
import { LoginComponent } from '../pages/login-page/login/login.component';
import { LoginGuard } from './login.guard';

describe('First page after signing in', () => {
  const children = routes.find(r => r.path === '')!.children!;
  let auth: { loggedIn$: BehaviorSubject<boolean>; redirectUrl?: string; login: jasmine.Spy };

  beforeEach(() => {
    auth = { loggedIn$: new BehaviorSubject(false), redirectUrl: undefined, login: jasmine.createSpy('login').and.returnValue(of({})) };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: AuthService, useValue: auth },
      { provide: LoadingService, useValue: { show: () => {}, hide: () => {} } },
      { provide: TranslateService, useValue: { instant: (key: string) => key } }
    ] });
  });

  function signIn(): jasmine.Spy {
    const router = TestBed.inject(Router);
    const go = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const page = TestBed.runInInjectionContext(() => new LoginComponent(
      TestBed.inject(AuthService), TestBed.inject(LoadingService), router, TestBed.inject(TranslateService)));
    page.validateForm.setValue({ email: 'staff@example.com', password: 'secret' });
    page.submitForm();
    return go;
  }

  it('opens My Learning after signing in', () => {
    expect(signIn()).toHaveBeenCalledOnceWith('/my-learning');
  });

  it('still returns to the page someone was on before signing in again', () => {
    auth.redirectUrl = '/sop/scoreboard';
    expect(signIn()).toHaveBeenCalledOnceWith('/sop/scoreboard');
    expect(auth.redirectUrl).toBeUndefined();
  });

  it('sends someone already signed in from the login page to My Learning', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    auth.loggedIn$.next(true);
    expect(await firstValueFrom(TestBed.inject(LoginGuard).canActivate())).toBeFalse();
    expect(navigate).toHaveBeenCalledOnceWith(['/my-learning']);
  });

  it('lets someone signed out open the login page', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    expect(await firstValueFrom(TestBed.inject(LoginGuard).canActivate())).toBeTrue();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('opens My Learning from the site address, and still sends unknown addresses to the org chart', async () => {
    expect(children.some(r => r.path === 'my-learning')).toBeTrue();
    const router = TestBed.inject(Router);
    router.resetConfig([
      children.find(r => r.path === '')!,
      { path: 'my-learning', children: [] },
      { path: 'org-chart', children: [] },
      children.find(r => r.path === '**')!
    ]);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/my-learning');
    await router.navigateByUrl('/no-such-page');
    expect(router.url).toBe('/org-chart');
  });
});
