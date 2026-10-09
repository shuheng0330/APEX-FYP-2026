import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiPeriodContext } from '../models/kpi-plan.model';
import { KpiAssessment, KpiAssessmentCheckpoint, KpiAssessmentEvidence, KpiAssessmentRequest, KpiAssessmentReview, KpiAssessmentReviewStatus, KpiSuperiorAssessmentRequest } from '../models/kpi-assessment.model';

@Injectable({ providedIn: 'root' })
export class KpiAssessmentService {
  private readonly base = `${environment.apiBaseUrl}/kpi-assessments`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods() { return this.http.get<KpiPeriodContext[]>(`${this.base}/periods`, this.options); }
  checkpoints(reviewPeriodId: number) {
    return this.http.get<KpiAssessmentCheckpoint[]>(`${this.base}/checkpoints`, { ...this.options, params: { reviewPeriodId } });
  }
  mine(checkpointId: number) {
    return this.http.get<KpiAssessment>(`${this.base}/mine`, { ...this.options, params: { checkpointId } });
  }
  create(request: KpiAssessmentRequest) { return this.http.post<KpiAssessment>(this.base, request, this.options); }
  update(id: number, request: KpiAssessmentRequest) { return this.http.put<KpiAssessment>(`${this.base}/${id}`, request, this.options); }
  submit(id: number) { return this.http.post<KpiAssessment>(`${this.base}/${id}/submit`, {}, this.options); }
  get(id: number) { return this.http.get<KpiAssessment>(`${this.base}/${id}`, this.options); }
  reviews(reviewPeriodId?: number, status?: KpiAssessmentReviewStatus) {
    const params: Record<string, string | number> = {};
    if (reviewPeriodId !== undefined) params['reviewPeriodId'] = reviewPeriodId;
    if (status !== undefined) params['status'] = status;
    return this.http.get<KpiAssessmentReview[]>(`${this.base}/reviews`, { ...this.options, params });
  }
  saveSuperiorDraft(id: number, request: KpiSuperiorAssessmentRequest) {
    return this.http.put<KpiAssessment>(`${this.base}/${id}/superior-draft`, request, this.options);
  }
  completeReview(id: number) { return this.http.post<KpiAssessment>(`${this.base}/${id}/complete-review`, {}, this.options); }
  upload(itemId: number, file: File) {
    const body = new FormData(); body.append('file', file);
    return this.http.post<KpiAssessmentEvidence>(`${this.base}/items/${itemId}/evidence`, body, this.options);
  }
  download(evidenceId: number) {
    return this.http.get(`${this.base}/evidence/${evidenceId}`, { ...this.options, responseType: 'blob' });
  }
  removeEvidence(evidenceId: number) { return this.http.delete<void>(`${this.base}/evidence/${evidenceId}`, this.options); }
}
