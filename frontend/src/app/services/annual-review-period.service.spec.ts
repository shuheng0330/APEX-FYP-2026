import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AnnualReviewPeriodService } from './annual-review-period.service';
import { savedReview, validReviewRequest } from '../pages/annual-review-period/review-period.fixtures.spec';

describe('AnnualReviewPeriodService', () => {
  let api: AnnualReviewPeriodService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/annual-kpi-review-periods`;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); api = TestBed.inject(AnnualReviewPeriodService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('retrieves both creation defaults for the chosen start date', () => {
    api.creationDefaults('2028-01-01').subscribe();
    const req = http.expectOne(`${base}/creation-defaults?startDate=2028-01-01`);
    expect(req.request.withCredentials).toBeTrue(); expect(req.request.headers.get('X-Skip-Error-Handler')).toBe('true');
    req.flush({ sourceReviewPeriodId: 1, roleConfigurations: [], employeeLevelConfigurations: [] });
  });
  it('creates using only the current Employee Level model', () => {
    api.create(validReviewRequest(), true).subscribe();
    const req = http.expectOne(`${base}?publish=true`);
    expect(req.request.method).toBe('POST'); expect(req.request.body.companyKpiWeight).toBeUndefined();
    expect(req.request.body.employeeLevelConfigurations.length).toBe(6); req.flush(savedReview());
  });
  it('excludes the existing period when previewing an edit', () => {
    api.preview(validReviewRequest(), 29).subscribe();
    const req = http.expectOne(`${base}/preview?excludedPeriodId=29`);
    expect(req.request.method).toBe('POST'); req.flush(savedReview());
  });
  it('does not include an excluded ID in a creation preview', () => {
    api.preview(validReviewRequest(), null).subscribe();
    const preview = http.expectOne(`${base}/preview`);
    expect(preview.request.params.has('excludedPeriodId')).toBeFalse();
    preview.flush(savedReview());
  });
  it('updates and publishes via the established routes', () => {
    api.update(29, validReviewRequest()).subscribe();
    const update = http.expectOne(`${base}/29`); expect(update.request.method).toBe('PUT'); update.flush(savedReview());
    api.publish(29).subscribe(); const publish = http.expectOne(`${base}/29/publish`); expect(publish.request.method).toBe('POST'); publish.flush(savedReview());
  });
  it('lists, retrieves and deletes using the established routes', () => {
    api.list().subscribe(); http.expectOne(base).flush([]);
    api.get(29).subscribe(); http.expectOne(`${base}/29`).flush(savedReview());
    api.roles().subscribe(); http.expectOne(`${base}/roles`).flush([]);
    api.delete(29).subscribe(); const deletion = http.expectOne(`${base}/29`); expect(deletion.request.method).toBe('DELETE'); deletion.flush(null);
  });
  it('loads read-only published configuration coverage using annual management access', () => {
    api.attitudeConfigurations().subscribe(); const request = http.expectOne(`${base}/attitude-configurations`);
    expect(request.request.method).toBe('GET'); expect(request.request.withCredentials).toBeTrue();
    expect(request.request.headers.get('X-Skip-Error-Handler')).toBe('true');
    request.flush([{ id: 8, name: 'Published Attitude', publishedAt: null, coveredRoleIds: [7] }]);
  });
  it('checks closure read-only and closes explicitly with credentials and local error handling', () => {
    api.closureReadiness(29).subscribe(); const check = http.expectOne(`${base}/29/closure-readiness`);
    expect(check.request.method).toBe('GET'); expect(check.request.withCredentials).toBeTrue();
    expect(check.request.headers.get('X-Skip-Error-Handler')).toBe('true'); check.flush({ canClose: false });
    api.close(29).subscribe(); const close = http.expectOne(`${base}/29/close`);
    expect(close.request.method).toBe('POST'); expect(close.request.body).toEqual({}); expect(close.request.withCredentials).toBeTrue();
    expect(close.request.headers.get('X-Skip-Error-Handler')).toBe('true'); close.flush({ status: 'CLOSED' });
  });
  it('preserves the structured blocked-closure response for contextual display', () => {
    const fresh = { reviewPeriodId: 29, status: 'OPEN', canClose: false, blockers: [] };
    const failure = jasmine.createSpy('failure'); api.close(29).subscribe({ error: failure });
    http.expectOne(`${base}/29/close`).flush(fresh, { status: 409, statusText: 'Conflict' });
    expect(failure).toHaveBeenCalledWith(jasmine.objectContaining({ status: 409, error: fresh }));
  });
});
