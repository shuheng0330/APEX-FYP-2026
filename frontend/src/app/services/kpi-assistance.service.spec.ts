import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { KpiAssistanceService } from './kpi-assistance.service';

describe('KPI assistance API', () => {
  let api: KpiAssistanceService; let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/individual-kpi-assistance`;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(KpiAssistanceService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('loads scoped eligibility, cases and details', () => {
    api.employees().subscribe(); http.expectOne(`${base}/employees`).flush([]);
    api.list().subscribe(); http.expectOne(base).flush([]);
    api.get(8).subscribe(); const get = http.expectOne(`${base}/8`); expect(get.request.withCredentials).toBeTrue(); get.flush({});
  });
  it('requests and records HR decisions through their actual endpoints', () => {
    api.request(7, 'Needs help preparing KPIs').subscribe(); const request = http.expectOne(base); expect(request.request.method).toBe('POST'); expect(request.request.body).toEqual({ ownerParticipantId: 7, requestReason: 'Needs help preparing KPIs' }); request.flush({});
    api.approve(8).subscribe(); const approve = http.expectOne(`${base}/8/authorize`); expect(approve.request.method).toBe('POST'); approve.flush({});
    api.reject(8, 'Clarify the need').subscribe(); const reject = http.expectOne(`${base}/8/reject`); expect(reject.request.body).toEqual({ reason: 'Clarify the need' }); expect(reject.request.method).toBe('POST'); reject.flush({});
  });
  it('derives assisted plan ownership from the case, without sending arbitrary employee IDs', () => {
    api.plan(8).subscribe(); http.expectOne(`${base}/8/plan`).flush({});
    api.createPlan(8, []).subscribe(); const create = http.expectOne(`${base}/8/plan`); expect(create.request.method).toBe('POST'); expect(create.request.body).toEqual({ items: [] }); create.flush({});
    api.updatePlan(8, []).subscribe(); const update = http.expectOne(`${base}/8/plan`); expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual({ items: [] }); update.flush({});
    api.confirmPlan(8).subscribe(); const confirm = http.expectOne(`${base}/8/confirm`); expect(confirm.request.method).toBe('POST'); expect(confirm.request.headers.get('X-Skip-Error-Handler')).toBe('true'); confirm.flush({});
  });
});
