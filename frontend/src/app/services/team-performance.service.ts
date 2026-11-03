import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { TeamMetric, TeamPerformance, TeamPeriod, TeamScope } from '../models/team-performance.model';

@Injectable({ providedIn: 'root' })
export class TeamPerformanceService {
  private readonly base = `${environment.apiBaseUrl}/annual-performance/team`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  scopes() { return this.http.get<TeamScope[]>(`${this.base}/scopes`, this.options); }
  periods(scope: TeamScope) {
    return this.http.get<TeamPeriod[]>(`${this.base}/periods`, { ...this.options, params: this.scopeParams(scope) });
  }
  dashboard(scope: TeamScope, reviewPeriodId: number, historyYears: 3 | 5, metric: TeamMetric) {
    return this.http.get<TeamPerformance>(this.base, { ...this.options, params: { ...this.scopeParams(scope), reviewPeriodId, historyYears, metric } });
  }
  private scopeParams(scope: TeamScope): Record<string, string | number> {
    return { scope: scope.type, ...(scope.departmentId === null ? {} : { departmentId: scope.departmentId }) };
  }
}
