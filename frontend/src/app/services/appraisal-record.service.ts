import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AppraisalReadinessDto, AppraisalRecordDto } from '../models/appraisal-record.model';

@Injectable({
  providedIn: 'root'
})
export class AppraisalRecordService {
  private baseUrl = `${environment.apiBaseUrl}/appraisal`;
  private headers = new HttpHeaders({ 'X-Skip-Error-Handler': 'true' });

  constructor(private http: HttpClient) { }

  getByStaff(staffId: string): Observable<AppraisalRecordDto[]> {
    return this.http.get<AppraisalRecordDto[]>(`${this.baseUrl}/by-staff/${staffId}`, {
      withCredentials: true,
      headers: this.headers
    });
  }

  getReviewRecords(): Observable<AppraisalRecordDto[]> {
    return this.http.get<AppraisalRecordDto[]>(`${this.baseUrl}/review-records`, {
      withCredentials: true,
      headers: this.headers
    });
  }

  getLatestTeamAppraisals(): Observable<AppraisalRecordDto[]> {
    return this.http.get<AppraisalRecordDto[]>(`${this.baseUrl}/team/latest`, {
      withCredentials: true,
      headers: this.headers
    });
  }

  getReadinessScore(staffId: string, evaluationCycleId: number, reviewPeriodYears: number): Observable<AppraisalReadinessDto> {
    const params = new HttpParams()
      .set('staffId', staffId)
      .set('evaluationCycleId', evaluationCycleId)
      .set('reviewPeriodYears', reviewPeriodYears);

    return this.http.get<AppraisalReadinessDto>(`${this.baseUrl}/readiness-score`, {
      withCredentials: true,
      headers: this.headers,
      params
    });
  }

  saveDraft(dto: AppraisalRecordDto): Observable<AppraisalRecordDto> {
    return this.http.post<AppraisalRecordDto>(this.baseUrl, dto, {
      withCredentials: true,
      headers: this.headers
    });
  }

  submit(id: string): Observable<AppraisalRecordDto> {
    return this.http.put<AppraisalRecordDto>(`${this.baseUrl}/${id}/submit`, {}, {
      withCredentials: true,
      headers: this.headers
    });
  }
}
