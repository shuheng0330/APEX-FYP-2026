import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AnnualReviewCreationDefaults, AnnualReviewPeriod, AnnualReviewPeriodRequest, ReviewRoleConfiguration } from '../models/annual-review-period.model';

@Injectable({ providedIn: 'root' })
export class AnnualReviewPeriodService {
  private readonly base = `${environment.apiBaseUrl}/annual-kpi-review-periods`;
  // This feature displays API failures next to the form, without a duplicate global notification.
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}

  list(): Observable<AnnualReviewPeriod[]> { return this.http.get<AnnualReviewPeriod[]>(this.base, this.options); }
  get(id: number): Observable<AnnualReviewPeriod> { return this.http.get<AnnualReviewPeriod>(`${this.base}/${id}`, this.options); }
  roles(): Observable<ReviewRoleConfiguration[]> { return this.http.get<ReviewRoleConfiguration[]>(`${this.base}/roles`, this.options); }
  creationDefaults(startDate: string): Observable<AnnualReviewCreationDefaults> {
    return this.http.get<AnnualReviewCreationDefaults>(`${this.base}/creation-defaults`, { ...this.options, params: { startDate } });
  }
  create(request: AnnualReviewPeriodRequest, publish = false): Observable<AnnualReviewPeriod> {
    return this.http.post<AnnualReviewPeriod>(this.base, request, { ...this.options, params: { publish } });
  }
  update(id: number, request: AnnualReviewPeriodRequest): Observable<AnnualReviewPeriod> {
    return this.http.put<AnnualReviewPeriod>(`${this.base}/${id}`, request, this.options);
  }
  publish(id: number): Observable<AnnualReviewPeriod> {
    return this.http.post<AnnualReviewPeriod>(`${this.base}/${id}/publish`, {}, this.options);
  }
  preview(request: AnnualReviewPeriodRequest, id: number | null): Observable<AnnualReviewPeriod> {
    const params = id === null ? new HttpParams() : new HttpParams().set('excludedPeriodId', id);
    return this.http.post<AnnualReviewPeriod>(`${this.base}/preview`, request, { ...this.options, params });
  }
  delete(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/${id}`, this.options); }
}
