import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { StaffPerformanceService } from './staff-performance.service';

describe('Staff performance read API', () => {
  let api: StaffPerformanceService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(StaffPerformanceService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('uses only authorised GET endpoints and asks for five years for local range selection', () => {
    const base = `${environment.apiBaseUrl}/annual-performance`;
    api.periods('bob').subscribe(); api.dashboard('bob', 7).subscribe(); api.checkpoint(11, 42).subscribe(); api.appraisal(11, 9).subscribe();
    const requests = http.match(() => true);
    expect(requests.map(request => request.request.urlWithParams)).toEqual([
      `${base}/staff/bob/periods`, `${base}/staff/bob?reviewPeriodId=7&historyYears=5`,
      `${base}/participants/11/checkpoints/42`, `${base}/participants/11/appraisals/9`
    ]);
    for (const request of requests) {
      expect(request.request.method).toBe('GET'); expect(request.request.withCredentials).toBeTrue();
      expect(request.request.headers.get('X-Skip-Error-Handler')).toBe('true'); request.flush({});
    }
  });
});
