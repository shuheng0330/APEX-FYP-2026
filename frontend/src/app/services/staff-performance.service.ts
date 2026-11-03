import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { PerformanceApprovedAppraisal, PerformanceCheckpointDetails, PerformancePeriod, StaffPerformance } from '../models/staff-performance.model';

@Injectable({ providedIn: 'root' })
export class StaffPerformanceService {
  private readonly base = `${environment.apiBaseUrl}/annual-performance`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods(employeeId: string) {
    return this.http.get<PerformancePeriod[]>(`${this.base}/staff/${encodeURIComponent(employeeId)}/periods`, this.options);
  }
  dashboard(employeeId: string, reviewPeriodId: number) {
    return this.http.get<StaffPerformance>(`${this.base}/staff/${encodeURIComponent(employeeId)}`,
      { ...this.options, params: { reviewPeriodId, historyYears: 5 } });
  }
  checkpoint(participantId: number, checkpointId: number) {
    return this.http.get<PerformanceCheckpointDetails>(`${this.base}/participants/${participantId}/checkpoints/${checkpointId}`, this.options);
  }
  appraisal(participantId: number, appraisalId: number) {
    return this.http.get<PerformanceApprovedAppraisal>(`${this.base}/participants/${participantId}/appraisals/${appraisalId}`, this.options);
  }
}
