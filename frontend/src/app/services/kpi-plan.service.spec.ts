import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { KpiPlanService } from './kpi-plan.service';

describe('Company KPI plan API', () => {
  let api: KpiPlanService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/company-kpi-plans`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(KpiPlanService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('uses scoped period lookup without annual administration endpoints', () => {
    api.periods().subscribe(); const req = http.expectOne(`${base}/periods`);
    expect(req.request.method).toBe('GET'); expect(req.request.withCredentials).toBeTrue(); req.flush([]);
  });
  it('saves and updates complete plan collections', () => {
    const body = { reviewPeriodId: 1, items: [] };
    api.create(body).subscribe(); const create = http.expectOne(base);
    expect(create.request.method).toBe('POST'); expect(create.request.body).toEqual(body); create.flush({});
    api.update(10, body).subscribe(); const update = http.expectOne(`${base}/10`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual(body); update.flush({});
  });
  it('publishes the whole plan, not an item', () => {
    api.publish(10).subscribe(); const req = http.expectOne(`${base}/10/publish`);
    expect(req.request.method).toBe('POST'); expect(req.request.body).toEqual({});
    expect(req.request.headers.get('X-Skip-Error-Handler')).toBe('true'); req.flush({});
  });
});
