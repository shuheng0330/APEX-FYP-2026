import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AttitudeAssessmentService } from './attitude-assessment.service';

describe('Attitude Self-Assessment API', () => {
  let api: AttitudeAssessmentService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/attitude-assessments`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AttitudeAssessmentService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads owner-scoped periods and annual details without configuration administration calls', () => {
    api.periods().subscribe(); const periods = http.expectOne(`${base}/periods`);
    expect(periods.request.method).toBe('GET'); expect(periods.request.withCredentials).toBeTrue(); periods.flush([]);
    api.mine(2).subscribe(); const mine = http.expectOne(`${base}/mine?reviewPeriodId=2`);
    expect(mine.request.method).toBe('GET'); expect(mine.request.headers.get('X-Skip-Error-Handler')).toBe('true'); mine.flush({});
    api.get(3).subscribe(); const detail = http.expectOne(`${base}/3`);
    expect(detail.request.method).toBe('GET'); detail.flush({});
  });
  it('persists nullable criterion answers, then submits saved answers without sending ownership or configuration', () => {
    const body = { reviewPeriodId: 2, items: [{ criterionId: 7, selfPoint: null, selfComment: null }] };
    api.create(body).subscribe(); const create = http.expectOne(base);
    expect(create.request.method).toBe('POST'); expect(create.request.body).toEqual(body); expect(create.request.withCredentials).toBeTrue(); create.flush({});
    api.update(10, body).subscribe(); const update = http.expectOne(`${base}/10`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual(body); update.flush({});
    api.submit(10).subscribe(); const submit = http.expectOne(`${base}/10/submit`);
    expect(submit.request.method).toBe('POST'); expect(submit.request.body).toEqual({});
    expect(submit.request.withCredentials).toBeTrue(); submit.flush({});
  });
});
