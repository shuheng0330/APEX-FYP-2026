import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { KpiRevisionService } from './kpi-revision.service';
import { revisionFixture, revisionPlan } from '../pages/kpi-plan/kpi-revision.test-data';

describe('KPI revision API', () => {
  let api: KpiRevisionService; let http: HttpTestingController;
  const base = environment.apiBaseUrl;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(KpiRevisionService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('uses scoped period, confirmed-plan and history endpoints', () => {
    api.periods().subscribe(); http.expectOne(`${base}/kpi-plan-revisions/periods`).flush([]);
    api.plans(4).subscribe(); http.expectOne(`${base}/kpi-plan-revisions/plans?reviewPeriodId=4`).flush([]);
    api.list(4).subscribe(); http.expectOne(`${base}/kpi-plan-revisions?reviewPeriodId=4`).flush([]);
    api.history(1).subscribe(); const history = http.expectOne(`${base}/kpi-plan-revisions/plans/1/versions`);
    expect(history.request.withCredentials).toBeTrue(); history.flush([]);
    api.get(2).subscribe(); http.expectOne(`${base}/kpi-plan-revisions/2`).flush(revisionFixture());
  });
  it('uses different Company start and Department request routes and trims reasons', () => {
    api.start(revisionPlan(), ' Market change ').subscribe();
    const company = http.expectOne(`${base}/company-kpi-plans/1/revisions`); expect(company.request.body).toEqual({ reason: 'Market change' }); company.flush({});
    api.start(revisionPlan({ level: 'DEPARTMENT' }), ' change ').subscribe(); http.expectOne(`${base}/department-kpi-plans/1/revision-requests`).flush({});
  });
  it('requests Individual revisions using the chosen preparation path', () => {
    for (const preparationMode of ['SUPERIOR', 'HR_MD'] as const) {
      api.start(revisionPlan({ level: 'INDIVIDUAL' }), ' Change ', preparationMode).subscribe();
      const request = http.expectOne(`${base}/individual-kpi-plans/1/revision-requests`);
      expect(request.request.body).toEqual({ reason: 'Change', preparationMode }); request.flush({});
    }
    api.start(revisionPlan({ level: 'INDIVIDUAL' }), 'Change').subscribe();
    const request = http.expectOne(`${base}/individual-kpi-plans/1/revision-requests`);
    expect(request.request.body.preparationMode).toBe('SUPERIOR'); request.flush({});
  });
  it('scopes the Superior revision queue to Individual plans and the selected period', () => {
    api.list(4, 'INDIVIDUAL').subscribe();
    const request = http.expectOne(`${base}/kpi-plan-revisions?reviewPeriodId=4&level=INDIVIDUAL`);
    expect(request.request.method).toBe('GET'); request.flush([]);
  });
  it('echoes the latest concurrency token for saves and all decisions', () => {
    const revision = revisionFixture(); api.save(revision, []).subscribe();
    const save = http.expectOne(`${base}/kpi-plan-revisions/2`); expect(save.request.method).toBe('PUT');
    expect(save.request.body).toEqual({ expectedLockVersion: 3, items: [] }); save.flush({});
    for (const action of ['PREPARE', 'SUBMIT', 'APPROVE', 'RETURN', 'CONFIRM', 'CANCEL'] as const) {
      api.decide(revision, action, ' reason ').subscribe(); const decision = http.expectOne(`${base}/kpi-plan-revisions/2/${action.toLowerCase()}`);
      expect(decision.request.body).toEqual({ expectedLockVersion: 3, reason: 'reason' }); decision.flush({});
    }
  });
  it('persists amendment settings without changing period dates', () => {
    api.access(4).subscribe(); http.expectOne(`${base}/kpi-amendment-access/4`).flush({});
    api.saveAccess(4, false, null).subscribe(); const request = http.expectOne(`${base}/kpi-amendment-access/4`);
    expect(request.request.body).toEqual({ enabled: false, closeDate: null }); request.flush({});
  });
});
