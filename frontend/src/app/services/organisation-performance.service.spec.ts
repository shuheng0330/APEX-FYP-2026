import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { OrganisationPerformanceService } from './organisation-performance.service';

describe('Organisation performance read API', () => {
  let api: OrganisationPerformanceService; let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); api = TestBed.inject(OrganisationPerformanceService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('uses authorised GET endpoints and explicit period, range and metric without adding scope permissions', () => {
    api.periods().subscribe(); api.dashboard(7, 3, 'ATTITUDE').subscribe(); api.export(7, 3, 'ATTITUDE').subscribe();
    const requests = http.match(() => true), base = `${environment.apiBaseUrl}/annual-performance/organisation`;
    expect(requests.map(r => r.request.urlWithParams)).toEqual([`${base}/periods`, `${base}?reviewPeriodId=7&historyYears=3&metric=ATTITUDE`, `${base}/export?reviewPeriodId=7&historyYears=3&metric=ATTITUDE`]);
    requests.forEach((r, index) => {
      expect(r.request.method).toBe('GET'); expect(r.request.withCredentials).toBeTrue(); expect(r.request.headers.get('X-Skip-Error-Handler')).toBe('true');
      expect(r.request.responseType).toBe(index === 2 ? 'blob' : 'json'); r.flush(index === 2 ? new Blob(['Excel']) : []);
    });
  });
});
