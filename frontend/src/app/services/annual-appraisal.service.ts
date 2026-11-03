import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiPeriodContext } from '../models/kpi-plan.model';
import { AnnualAppraisal, AnnualAppraisalActionRequest, AnnualAppraisalCandidate,
  AnnualAppraisalReadiness, AnnualAppraisalRequest, AnnualPerformanceHistory } from '../models/annual-appraisal.model';

@Injectable({ providedIn: 'root' })
export class AnnualAppraisalService {
  private readonly base = `${environment.apiBaseUrl}/annual-appraisals`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods(hrReview: boolean) {
    return this.http.get<KpiPeriodContext[]>(`${this.base}/periods`, { ...this.options, params: { hrReview } });
  }
  team(reviewPeriodId: number) {
    return this.http.get<AnnualAppraisalCandidate[]>(`${this.base}/team`, { ...this.options, params: { reviewPeriodId } });
  }
  reviews(reviewPeriodId: number) {
    return this.http.get<AnnualAppraisal[]>(`${this.base}/reviews`, { ...this.options, params: { reviewPeriodId } });
  }
  readiness(participantId: number) {
    return this.http.get<AnnualAppraisalReadiness>(`${this.base}/participants/${participantId}/readiness`, this.options);
  }
  history(participantId: number) {
    return this.http.get<AnnualAppraisal[]>(`${this.base}/participants/${participantId}/history`, this.options);
  }
  performanceHistory(participantId: number) {
    return this.http.get<AnnualPerformanceHistory[]>(`${this.base}/participants/${participantId}/performance-history`, this.options);
  }
  get(id: number) { return this.http.get<AnnualAppraisal>(`${this.base}/${id}`, this.options); }
  create(request: AnnualAppraisalRequest) { return this.http.post<AnnualAppraisal>(this.base, request, this.options); }
  update(id: number, request: AnnualAppraisalRequest) { return this.http.put<AnnualAppraisal>(`${this.base}/${id}`, request, this.options); }
  submit(id: number, request: AnnualAppraisalActionRequest) { return this.action(id, 'submit', request); }
  approve(id: number, request: AnnualAppraisalActionRequest) { return this.action(id, 'approve', request); }
  overrideApprove(id: number, request: AnnualAppraisalActionRequest) { return this.action(id, 'override-approve', request); }
  returnForRevision(id: number, request: AnnualAppraisalActionRequest) { return this.action(id, 'return', request); }
  private action(id: number, action: string, request: AnnualAppraisalActionRequest) {
    return this.http.post<AnnualAppraisal>(`${this.base}/${id}/${action}`, request, this.options);
  }
}
