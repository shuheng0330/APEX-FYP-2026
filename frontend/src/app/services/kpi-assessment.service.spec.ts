import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { KpiAssessmentService } from './kpi-assessment.service';

describe('KPI Self-Assessment API', () => {
  let api: KpiAssessmentService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/kpi-assessments`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(KpiAssessmentService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('uses employee-scoped context and readonly lookup endpoints', () => {
    api.periods().subscribe(); const periods = http.expectOne(`${base}/periods`);
    expect(periods.request.method).toBe('GET'); expect(periods.request.withCredentials).toBeTrue(); periods.flush([]);
    api.checkpoints(2).subscribe(); const checkpoints = http.expectOne(`${base}/checkpoints?reviewPeriodId=2`);
    expect(checkpoints.request.method).toBe('GET'); checkpoints.flush([]);
    api.mine(3).subscribe(); const mine = http.expectOne(`${base}/mine?checkpointId=3`);
    expect(mine.request.method).toBe('GET'); mine.flush({});
  });
  it('saves incomplete answers with assignment IDs, then submits the saved assessment', () => {
    const body = { checkpointId: 3, items: [{ assignmentId: 7, selfPoint: null, selfComment: null }] };
    api.create(body).subscribe(); const create = http.expectOne(base);
    expect(create.request.method).toBe('POST'); expect(create.request.body).toEqual(body); create.flush({});
    api.update(10, body).subscribe(); const update = http.expectOne(`${base}/10`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual(body); update.flush({});
    api.submit(10).subscribe(); const submit = http.expectOne(`${base}/10/submit`);
    expect(submit.request.body).toEqual({}); expect(submit.request.method).toBe('POST'); submit.flush({});
  });
  it('uploads evidence to a saved item without setting a multipart boundary manually', () => {
    const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' });
    api.upload(8, file).subscribe(); const upload = http.expectOne(`${base}/items/8/evidence`);
    expect(upload.request.method).toBe('POST'); expect(upload.request.body.get('file')).toEqual(file);
    expect(upload.request.headers.has('Content-Type')).toBeFalse(); upload.flush({});
  });
  it('uses private authenticated downloads/deletion, never the generic file API', () => {
    api.download(9).subscribe(); const download = http.expectOne(`${base}/evidence/9`);
    expect(download.request.responseType).toBe('blob'); expect(download.request.withCredentials).toBeTrue();
    expect(download.request.headers.get('X-Skip-Error-Handler')).toBe('true'); download.flush(new Blob());
    api.removeEvidence(9).subscribe(); const remove = http.expectOne(`${base}/evidence/9`);
    expect(remove.request.method).toBe('DELETE'); remove.flush(null);
  });
});
