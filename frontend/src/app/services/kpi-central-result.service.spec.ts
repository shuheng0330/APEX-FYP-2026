import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { KpiCentralResultService } from './kpi-central-result.service';
import { environment } from '../../environments/environment';

describe('KpiCentralResultService', () => {
  let api: KpiCentralResultService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/kpi-central-results`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(KpiCentralResultService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads the scoped period, plan-version and checkpoint choices without writes', () => {
    api.periods().subscribe(); api.optionsForPeriod(3).subscribe(); api.checkpoints(7).subscribe(); api.list(3).subscribe();
    const periods = http.expectOne(`${base}/periods`);
    expect(periods.request.method).toBe('GET'); periods.flush([]);
    const options = http.expectOne(`${base}/options?reviewPeriodId=3`);
    expect(options.request.method).toBe('GET'); options.flush([]);
    const checkpoints = http.expectOne(`${base}/checkpoints?planVersionId=7`);
    expect(checkpoints.request.method).toBe('GET'); checkpoints.flush([]);
    const list = http.expectOne(`${base}?reviewPeriodId=3`);
    expect(list.request.method).toBe('GET'); list.flush([]);
  });

  it('sends optimistic-lock versions for Draft, publication and correction', () => {
    const draft = { planVersionId: 7, checkpointId: 11, expectedLockVersion: 2,
      items: [{ kpiId: 17, actualAchievement: '101 units', point: 4, comment: null }] };
    api.update(5, draft).subscribe();
    const update = http.expectOne(`${base}/5`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual(draft); update.flush({});
    api.publish(5, 3).subscribe();
    const publish = http.expectOne(`${base}/5/publish`);
    expect(publish.request.body).toEqual({ expectedLockVersion: 3 }); publish.flush({});
    api.correct(5, 4, 'Corrected actuals').subscribe();
    const correction = http.expectOne(`${base}/5/corrections`);
    expect(correction.request.body).toEqual({ expectedLockVersion: 4, reason: 'Corrected actuals' }); correction.flush({});
  });

  it('uses item-scoped evidence and period-scoped delegated entrants', () => {
    api.upload(19, 4, new File(['proof'], 'proof.pdf', { type: 'application/pdf' })).subscribe();
    const upload = http.expectOne(`${base}/items/19/evidence?expectedLockVersion=4`);
    expect(upload.request.body instanceof FormData).toBeTrue(); upload.flush({});
    api.removeEvidence(23, 5).subscribe();
    const removal = http.expectOne(`${base}/evidence/23?expectedLockVersion=5`);
    expect(removal.request.method).toBe('DELETE'); removal.flush({});
    api.assignDelegate(3, '00000000-0000-0000-0000-000000000001').subscribe();
    const delegate = http.expectOne(`${base}/periods/3/delegates`);
    expect(delegate.request.body).toEqual({ staffId: '00000000-0000-0000-0000-000000000001' }); delegate.flush({});
  });
});
