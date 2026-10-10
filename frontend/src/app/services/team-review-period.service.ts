import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiPeriodContext } from '../models/kpi-plan.model';

export interface TeamReviewPeriods { periods: KpiPeriodContext[]; defaultPeriodId: number | null; }

@Injectable({ providedIn: 'root' })
export class TeamReviewPeriodService {
  constructor(private http: HttpClient) {}
  periods() {
    return this.http.get<TeamReviewPeriods>(`${environment.apiBaseUrl}/team-reviews/periods`, {
      withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }
}
