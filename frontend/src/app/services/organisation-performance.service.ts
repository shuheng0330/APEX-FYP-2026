import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { OrganisationPerformance } from '../models/organisation-performance.model';
import { TeamMetric, TeamPeriod } from '../models/team-performance.model';

@Injectable({ providedIn: 'root' })
export class OrganisationPerformanceService {
  private readonly base = `${environment.apiBaseUrl}/annual-performance/organisation`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods() { return this.http.get<TeamPeriod[]>(`${this.base}/periods`, this.options); }
  dashboard(reviewPeriodId: number, historyYears: 3 | 5, metric: TeamMetric) {
    return this.http.get<OrganisationPerformance>(this.base, { ...this.options, params: { reviewPeriodId, historyYears, metric } });
  }
  export(reviewPeriodId: number, historyYears: 3 | 5, metric: TeamMetric) {
    return this.http.get(`${this.base}/export`, { ...this.options, params: { reviewPeriodId, historyYears, metric }, responseType: 'blob' });
  }
}
