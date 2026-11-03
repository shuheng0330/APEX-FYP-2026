import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiPeriodContext } from '../models/kpi-plan.model';
import {
  CentralEvidenceMutation, CentralResult, CentralResultCheckpoint, CentralResultOption,
  CentralResultRequest, CentralResultEvidence, CompanyResultDelegate
} from '../models/kpi-central-result.model';

@Injectable({ providedIn: 'root' })
export class KpiCentralResultService {
  private readonly base = `${environment.apiBaseUrl}/kpi-central-results`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };

  constructor(private http: HttpClient) {}

  periods() { return this.http.get<KpiPeriodContext[]>(`${this.base}/periods`, this.options); }
  optionsForPeriod(reviewPeriodId: number) {
    return this.http.get<CentralResultOption[]>(`${this.base}/options`, { ...this.options, params: { reviewPeriodId } });
  }
  checkpoints(planVersionId: number) {
    return this.http.get<CentralResultCheckpoint[]>(`${this.base}/checkpoints`, { ...this.options, params: { planVersionId } });
  }
  list(reviewPeriodId: number) {
    return this.http.get<CentralResult[]>(this.base, { ...this.options, params: { reviewPeriodId } });
  }
  get(id: number) { return this.http.get<CentralResult>(`${this.base}/${id}`, this.options); }
  create(request: CentralResultRequest) { return this.http.post<CentralResult>(this.base, request, this.options); }
  update(id: number, request: CentralResultRequest) { return this.http.put<CentralResult>(`${this.base}/${id}`, request, this.options); }
  publish(id: number, expectedLockVersion: number) {
    return this.http.post<CentralResult>(`${this.base}/${id}/publish`, { expectedLockVersion }, this.options);
  }
  correct(id: number, expectedLockVersion: number, reason: string) {
    return this.http.post<CentralResult>(`${this.base}/${id}/corrections`, { expectedLockVersion, reason }, this.options);
  }
  history(id: number) { return this.http.get<CentralResult[]>(`${this.base}/${id}/history`, this.options); }
  upload(itemId: number, expectedLockVersion: number, file: File) {
    const body = new FormData(); body.append('file', file);
    return this.http.post<CentralEvidenceMutation>(`${this.base}/items/${itemId}/evidence`, body,
      { ...this.options, params: { expectedLockVersion } });
  }
  evidence(itemId: number) {
    return this.http.get<CentralResultEvidence[]>(`${this.base}/items/${itemId}/evidence`, this.options);
  }
  download(evidenceId: number) {
    return this.http.get(`${this.base}/evidence/${evidenceId}`, { ...this.options, responseType: 'blob' });
  }
  removeEvidence(evidenceId: number, expectedLockVersion: number) {
    return this.http.delete<CentralEvidenceMutation>(`${this.base}/evidence/${evidenceId}`,
      { ...this.options, params: { expectedLockVersion } });
  }
  delegates(reviewPeriodId: number) {
    return this.http.get<CompanyResultDelegate[]>(`${this.base}/periods/${reviewPeriodId}/delegates`, this.options);
  }
  assignDelegate(reviewPeriodId: number, staffId: string) {
    return this.http.post<CompanyResultDelegate>(`${this.base}/periods/${reviewPeriodId}/delegates`, { staffId }, this.options);
  }
  revokeDelegate(reviewPeriodId: number, delegateId: number) {
    return this.http.post<CompanyResultDelegate>(`${this.base}/periods/${reviewPeriodId}/delegates/${delegateId}/revoke`, {}, this.options);
  }
}
