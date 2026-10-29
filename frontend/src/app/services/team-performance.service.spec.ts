import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { TeamPerformanceService } from './team-performance.service';
import { teamScope, departmentScope } from '../pages/team-performance/team-performance-test-fixtures';

describe('Team performance read API', () => {
  let api: TeamPerformanceService; let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); api = TestBed.inject(TeamPerformanceService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('requests scoped GET data with the selected period, range and metric', () => {
    api.scopes().subscribe(); api.periods(teamScope).subscribe(); api.periods(departmentScope).subscribe();
    api.dashboard(departmentScope, 7, 3, 'KPI').subscribe();
    const requests = http.match(() => true); const base = `${environment.apiBaseUrl}/annual-performance/team`;
    expect(requests.map(r => r.request.urlWithParams)).toEqual([`${base}/scopes`, `${base}/periods?scope=TEAM`,
      `${base}/periods?scope=DEPARTMENT&departmentId=8`, `${base}?scope=DEPARTMENT&departmentId=8&reviewPeriodId=7&historyYears=3&metric=KPI`]);
    for (const r of requests) { expect(r.request.method).toBe('GET'); expect(r.request.withCredentials).toBeTrue(); expect(r.request.headers.get('X-Skip-Error-Handler')).toBe('true'); r.flush({}); }
  });
  it('preserves an authorised unassigned Department scope without inventing an ID', () => {
    api.dashboard({ type: 'DEPARTMENT', departmentId: null, name: 'Unassigned' }, 1, 5, 'FINAL').subscribe();
    const request = http.expectOne(r => r.params.get('scope') === 'DEPARTMENT'); expect(request.request.params.has('departmentId')).toBeFalse(); request.flush({});
  });
});
