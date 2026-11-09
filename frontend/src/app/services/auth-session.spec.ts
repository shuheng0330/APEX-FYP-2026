import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { ActivatedRouteSnapshot, provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { JwtInterceptor } from '../interceptors/jwtInterceptor.interceptor';
import { PermissionGuard } from '../guards/permission.guard';
import { initAppFactory } from '../app.config';

describe('Cookie-restored authentication in a fresh application tab', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let client: HttpClient;
  const base = environment.authBaseUrl;
  const restored = { userId: 'fictional-session-user', roles: ['ROLE_USER', 'CAN_MANAGE_ATTITUDE_CONFIGURATION'], accessToken: 'mock-restored-access', message: 'Session restored' };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(withInterceptors([JwtInterceptor])), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService); http = TestBed.inject(HttpTestingController); client = TestBed.inject(HttpClient);
    const grantLock = (name: string, optionsOrCallback: LockOptions | LockGrantedCallback, callback?: LockGrantedCallback) => {
      const action = typeof optionsOrCallback === 'function' ? optionsOrCallback : callback!;
      return Promise.resolve(action({ name, mode: 'exclusive' }));
    };
    if (navigator.locks) spyOn(navigator.locks, 'request').and.callFake(grantLock);
  });
  afterEach(() => http.verify());

  it('retains the refreshed access token when /me returns identity and permissions without a token', async () => {
    const ready = initAppFactory(auth)();
    http.expectOne(`${base}/me`).flush({}, { status: 401, statusText: 'Unauthorised' });
    const refresh = http.expectOne(`${base}/refresh`);
    expect(refresh.request.withCredentials).toBeTrue(); refresh.flush(restored);
    const me = http.expectOne(`${base}/me`);
    expect(me.request.headers.get('Authorization')).toBe(`Bearer ${restored.accessToken}`);
    me.flush({ userId: restored.userId, roles: restored.roles });
    await ready;
    expect(auth.getAccessToken()).toBe(restored.accessToken);
    if (navigator.locks) expect((navigator.locks.request as jasmine.Spy).calls.mostRecent().args[0]).toBe(`apex-auth-refresh:${base}`);
    expect(auth.hasRole('CAN_MANAGE_ATTITUDE_CONFIGURATION')).toBeTrue();
    const route = new ActivatedRouteSnapshot(); route.data = { requiredRoles: ['CAN_MANAGE_ATTITUDE_CONFIGURATION'] };
    expect(TestBed.inject(PermissionGuard).canActivate(route)).toBeTrue();
    client.get('/api/attitude-configurations/options').subscribe();
    const options = http.expectOne('/api/attitude-configurations/options');
    expect(options.request.headers.get('Authorization')).toBe(`Bearer ${restored.accessToken}`); options.flush({});
  });

  it('uses one refresh for parallel 401 replies instead of rotating the same cookie twice and clearing permissions', () => {
    auth.login('fictional@example.test', 'not-a-real-password').subscribe(); http.expectOne(`${base}/login`).flush({ ...restored, accessToken: 'mock-expired-access' });
    const errors: unknown[] = [];
    client.get('/api/attitude-configurations').subscribe({ error: e => errors.push(e) });
    client.get('/api/attitude-configurations/options').subscribe({ error: e => errors.push(e) });
    http.expectOne('/api/attitude-configurations').flush({}, { status: 401, statusText: 'Unauthorised' });
    http.expectOne('/api/attitude-configurations/options').flush({}, { status: 401, statusText: 'Unauthorised' });
    const refreshes = http.match(`${base}/refresh`);
    expect(refreshes.length).toBe(1);
    refreshes.forEach((request, index) => index === 0 ? request.flush(restored)
      : request.flush({}, { status: 403, statusText: 'Refresh cookie already rotated' }));
    for (const path of ['/api/attitude-configurations', '/api/attitude-configurations/options']) {
      const retried = http.match(path);
      retried.forEach(request => request.flush({}));
    }
    expect(errors).toEqual([]); expect(auth.hasRole('CAN_MANAGE_ATTITUDE_CONFIGURATION')).toBeTrue();
  });

  for (const status of [401, 403]) {
    it(`does not recursively refresh an unsuccessful refresh (${status}) and permits a later fresh session`, async () => {
      const ready = initAppFactory(auth)();
      http.expectOne(`${base}/me`).flush({}, { status: 401, statusText: 'Unauthorised' });
      http.expectOne(`${base}/refresh`).flush({}, { status, statusText: 'Session expired' });
      await ready;
      http.expectNone(`${base}/refresh`);
      expect(auth.loggedIn$.value).toBeFalse(); expect(auth.roles).toEqual([]);
      const retry = initAppFactory(auth)();
      http.expectOne(`${base}/me`).flush({}, { status: 401, statusText: 'Unauthorised' });
      http.expectOne(`${base}/refresh`).flush(restored);
      http.expectOne(`${base}/me`).flush({ userId: restored.userId, roles: restored.roles });
      await retry;
      expect(auth.hasRole('CAN_MANAGE_ATTITUDE_CONFIGURATION')).toBeTrue();
    });
  }

  it('keeps unauthorised users denied rather than refreshing a permission rejection', () => {
    auth.login('fictional@example.test', 'not-a-real-password').subscribe();
    http.expectOne(`${base}/login`).flush({ ...restored, roles: ['ROLE_USER'] });
    const route = new ActivatedRouteSnapshot(); route.data = { requiredRoles: ['CAN_MANAGE_ATTITUDE_CONFIGURATION'] };
    expect(TestBed.inject(PermissionGuard).canActivate(route)).toBeFalse();
    let rejected = false;
    client.get('/api/attitude-configurations').subscribe({ error: () => rejected = true });
    http.expectOne('/api/attitude-configurations').flush({}, { status: 403, statusText: 'Permission required' });
    expect(rejected).toBeTrue(); http.expectNone(`${base}/refresh`);
  });

  it('does not restore an old session when a pending refresh returns after logout', () => {
    auth.refresh(false).subscribe({ error: () => undefined });
    const refresh = http.expectOne(`${base}/refresh`);
    auth.logout().subscribe(); http.expectOne(`${base}/logout`).flush(null);
    refresh.flush(restored);
    expect(auth.loggedIn$.value).toBeFalse(); expect(auth.getAccessToken()).toBeNull();
  });

  it('does not let an older failed refresh clear a newly logged-in account', () => {
    auth.refresh(false).subscribe({ error: () => undefined });
    const refresh = http.expectOne(`${base}/refresh`);
    auth.login('another@example.test', 'not-a-real-password').subscribe();
    http.expectOne(`${base}/login`).flush({ ...restored, userId: 'another-fictional-user', accessToken: 'another-mock-token' });
    refresh.flush({}, { status: 403, statusText: 'Old session expired' });
    expect(auth.userId).toBe('another-fictional-user'); expect(auth.getAccessToken()).toBe('another-mock-token');
  });
});
