import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AttitudeConfigurationService } from './attitude-configuration.service';
import { emptyAttitudeConfiguration } from '../models/attitude-configuration.model';

describe('Attitude configuration API', () => {
  let api: AttitudeConfigurationService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/attitude-configurations`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AttitudeConfigurationService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads complete history and authorised options using the existing HTTP conventions', () => {
    api.list().subscribe(); const list = http.expectOne(base);
    expect(list.request.method).toBe('GET'); expect(list.request.withCredentials).toBeTrue();
    expect(list.request.headers.get('X-Skip-Error-Handler')).toBe('true'); list.flush([]);
    api.optionsList().subscribe(); http.expectOne(`${base}/options`).flush({ formats: [], roles: [] });
  });
  it('saves incomplete Drafts as full collections without changing a review-period binding', () => {
    const request = emptyAttitudeConfiguration();
    api.create(request).subscribe(); const create = http.expectOne(base);
    expect(create.request.method).toBe('POST'); expect(create.request.body).toEqual(request); create.flush({});
    api.update(7, request).subscribe(); const update = http.expectOne(`${base}/7`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body).toEqual(request); update.flush({});
  });
  it('uses independent whole-configuration copy and publish actions', () => {
    api.copy(7).subscribe(); const copy = http.expectOne(`${base}/7/copy`);
    expect(copy.request.method).toBe('POST'); expect(copy.request.body).toEqual({}); copy.flush({});
    api.publish(8).subscribe(); const publish = http.expectOne(`${base}/8/publish`);
    expect(publish.request.method).toBe('POST'); expect(publish.request.withCredentials).toBeTrue(); publish.flush({});
  });
});
