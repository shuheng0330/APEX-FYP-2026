import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { IndividualKpiPlanService } from './individual-kpi-plan.service';

describe('Individual KPI API', () => {
  let api: IndividualKpiPlanService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/individual-kpi-plans`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(IndividualKpiPlanService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads only own periods, plans and assignments using the selected period', () => {
    api.periods().subscribe(); http.expectOne(`${base}/periods`).flush([]);
    api.mine().subscribe(); http.expectOne(`${base}/mine`).flush([]);
    api.assigned(2028).subscribe(); const assigned = http.expectOne(`${base}/my-assigned?reviewPeriodId=2028`);
    expect(assigned.request.withCredentials).toBeTrue(); expect(assigned.request.method).toBe('GET'); assigned.flush([]);
  });
  it('persists whole item collections without choosing another employee', () => {
    const body = { reviewPeriodId: 1, items: [] };
    api.create(body).subscribe(); const create = http.expectOne(base);
    expect(create.request.body).toEqual(body); expect(create.request.method).toBe('POST'); create.flush({});
    api.update(10, body).subscribe(); const update = http.expectOne(`${base}/10`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual(body); update.flush({});
    api.submit(10).subscribe(); const submit = http.expectOne(`${base}/10/submit`);
    expect(submit.request.method).toBe('POST'); expect(submit.request.body).toEqual({}); submit.flush({});
  });
  it('uses the scoped history listing and separate plan-level review actions', () => {
    api.reviews().subscribe(); http.expectOne(`${base}/reviews`).flush([]);
    api.get(10).subscribe(); http.expectOne(`${base}/10`).flush({});
    api.approve(10).subscribe(); const approved = http.expectOne(`${base}/10/approve`);
    expect(approved.request.method).toBe('POST'); approved.flush({});
    api.returnForRevision(10, 'Clarify target').subscribe(); const returned = http.expectOne(`${base}/10/return`);
    expect(returned.request.body).toEqual({ reason: 'Clarify target' });
    expect(returned.request.headers.get('X-Skip-Error-Handler')).toBe('true'); returned.flush({});
  });
});
