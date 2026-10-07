import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiPlan, KpiPlanRequest, KpiPeriodContext } from '../models/kpi-plan.model';
@Injectable({ providedIn: 'root' })
export class KpiPlanService {
  private readonly base = `${environment.apiBaseUrl}/company-kpi-plans`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods() { return this.http.get<KpiPeriodContext[]>(`${this.base}/periods`, this.options); }
  list() { return this.http.get<KpiPlan[]>(this.base, this.options); }
  get(id: number) { return this.http.get<KpiPlan>(`${this.base}/${id}`, this.options); }
  create(request: KpiPlanRequest) { return this.http.post<KpiPlan>(this.base, request, this.options); }
  update(id: number, request: KpiPlanRequest) { return this.http.put<KpiPlan>(`${this.base}/${id}`, request, this.options); }
  publish(id: number) { return this.http.post<KpiPlan>(`${this.base}/${id}/publish`, {}, this.options); }
}
