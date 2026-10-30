import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiPeriodContext } from '../models/kpi-plan.model';
import { AttitudeAssessment, AttitudeAssessmentRequest, AttitudeAssessmentReview,
  SuperiorAttitudeAssessmentRequest } from '../models/attitude-assessment.model';

@Injectable({ providedIn: 'root' })
export class AttitudeAssessmentService {
  private readonly base = `${environment.apiBaseUrl}/attitude-assessments`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods() { return this.http.get<KpiPeriodContext[]>(`${this.base}/periods`, this.options); }
  mine(reviewPeriodId: number) {
    return this.http.get<AttitudeAssessment>(`${this.base}/mine`, { ...this.options, params: { reviewPeriodId } });
  }
  get(id: number) { return this.http.get<AttitudeAssessment>(`${this.base}/${id}`, this.options); }
  create(request: AttitudeAssessmentRequest) { return this.http.post<AttitudeAssessment>(this.base, request, this.options); }
  update(id: number, request: AttitudeAssessmentRequest) { return this.http.put<AttitudeAssessment>(`${this.base}/${id}`, request, this.options); }
  submit(id: number) { return this.http.post<AttitudeAssessment>(`${this.base}/${id}/submit`, {}, this.options); }
  reviews(reviewPeriodId?: number, status?: AttitudeAssessmentReview['status']) {
    const params: Record<string, string | number> = {};
    if (reviewPeriodId !== undefined) params['reviewPeriodId'] = reviewPeriodId;
    if (status !== undefined) params['status'] = status;
    return this.http.get<AttitudeAssessmentReview[]>(`${this.base}/reviews`, { ...this.options, params });
  }
  saveSuperiorDraft(id: number, request: SuperiorAttitudeAssessmentRequest) {
    return this.http.put<AttitudeAssessment>(`${this.base}/${id}/superior-draft`, request, this.options);
  }
  completeReview(id: number) {
    return this.http.post<AttitudeAssessment>(`${this.base}/${id}/complete-review`, {}, this.options);
  }
}
