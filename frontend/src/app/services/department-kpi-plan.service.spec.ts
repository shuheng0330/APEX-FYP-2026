import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { DepartmentKpiPlanService } from './department-kpi-plan.service';

describe('Department KPI plan API', () => {
  let api: DepartmentKpiPlanService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/department-kpi-plans`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(DepartmentKpiPlanService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads scoped periods and Department options', () => {
    api.periods().subscribe(); const periods = http.expectOne(`${base}/periods`);
    expect(periods.request.method).toBe('GET'); expect(periods.request.withCredentials).toBeTrue(); periods.flush([]);
    api.departments().subscribe(); const departments = http.expectOne(`${base}/departments`);
    expect(departments.request.method).toBe('GET'); departments.flush([]);
  });
  it('saves complete plan collections and submits the plan once', () => {
    const body = { reviewPeriodId: 1, departmentId: 2, items: [] };
    api.create(body).subscribe(); const create = http.expectOne(base);
    expect(create.request.method).toBe('POST'); expect(create.request.body).toEqual(body); create.flush({});
    api.update(10, body).subscribe(); const update = http.expectOne(`${base}/10`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual(body); update.flush({});
    api.submit(10).subscribe(); const submit = http.expectOne(`${base}/10/submit`);
    expect(submit.request.method).toBe('POST'); expect(submit.request.body).toEqual({}); submit.flush({});
  });
  it('uses distinct review actions with a reason on return', () => {
    api.list().subscribe(); http.expectOne(base).flush([]);
    api.get(10).subscribe(); http.expectOne(`${base}/10`).flush({});
    api.approve(10).subscribe(); const approval = http.expectOne(`${base}/10/approve`);
    expect(approval.request.method).toBe('POST'); approval.flush({});
    api.returnForRevision(10, 'Clarify target').subscribe(); const returned = http.expectOne(`${base}/10/return`);
    expect(returned.request.method).toBe('POST'); expect(returned.request.body).toEqual({ reason: 'Clarify target' });
    expect(returned.request.headers.get('X-Skip-Error-Handler')).toBe('true'); returned.flush({});
  });
});
