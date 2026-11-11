import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { AppraisalInsight, InsightScenario, EmployeeInsightState, EmployeeInsightResult, SubmittedEmployeeInsight } from '../models/appraisal-insight.model';

@Injectable({ providedIn: 'root' })
export class AppraisalInsightService {
  private readonly base = `${environment.apiBaseUrl}/annual-appraisal-insights/synthetic`;
  private readonly appraisals = `${environment.apiBaseUrl}/annual-appraisals`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({
    'X-Skip-Error-Handler': 'true', 'X-Skip-Loading': 'true'
  }) };
  constructor(private http: HttpClient) {}
  scenarios() { return this.http.get<InsightScenario[]>(`${this.base}/scenarios`, this.options); }
  generate(scenario: string, requestKey: string) {
    return this.http.post<AppraisalInsight>(`${this.base}/scenarios/${encodeURIComponent(scenario)}/generations`, null,
      { ...this.options, headers: this.options.headers.set('Idempotency-Key', requestKey) });
  }
  get(id: string) { return this.http.get<AppraisalInsight>(`${this.base}/generations/${encodeURIComponent(id)}`, this.options); }
  employeeState(participantId: number) {
    return this.http.get<EmployeeInsightState>(`${this.appraisals}/participants/${participantId}/ai-insights`, this.options);
  }
  generateEmployee(participantId: number, requestKey: string, regenerate = false) {
    return this.http.post<EmployeeInsightResult>(`${this.appraisals}/participants/${participantId}/ai-insights${regenerate ? '/regenerate' : ''}`, null,
      { ...this.options, headers: this.options.headers.set('Idempotency-Key', requestKey) });
  }
  employeeGeneration(participantId: number, id: string) {
    return this.http.get<EmployeeInsightResult>(`${this.appraisals}/participants/${participantId}/ai-insights/generations/${encodeURIComponent(id)}`, this.options);
  }
  submitted(appraisalId: number, submissionActivityId: number | null = null) {
    return this.http.get<SubmittedEmployeeInsight>(`${this.appraisals}/${appraisalId}/ai-insight`, {
      ...this.options, params: submissionActivityId === null ? {} : { submissionActivityId: String(submissionActivityId) }
    });
  }
}
