import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiPlan, KpiPlanRequest, KpiPeriodContext } from '../models/kpi-plan.model';

export interface KpiDepartmentOption { id: number; name: string; }

@Injectable({ providedIn: 'root' })
export class DepartmentKpiPlanService {
  private readonly base = `${environment.apiBaseUrl}/department-kpi-plans`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods() { return this.http.get<KpiPeriodContext[]>(`${this.base}/periods`, this.options); }
  departments() { return this.http.get<KpiDepartmentOption[]>(`${this.base}/departments`, this.options); }
  list() { return this.http.get<KpiPlan[]>(this.base, this.options); }
  get(id: number) { return this.http.get<KpiPlan>(`${this.base}/${id}`, this.options); }
  create(request: KpiPlanRequest) { return this.http.post<KpiPlan>(this.base, request, this.options); }
  update(id: number, request: KpiPlanRequest) { return this.http.put<KpiPlan>(`${this.base}/${id}`, request, this.options); }
  submit(id: number) { return this.http.post<KpiPlan>(`${this.base}/${id}/submit`, {}, this.options); }
  approve(id: number) { return this.http.post<KpiPlan>(`${this.base}/${id}/approve`, {}, this.options); }
  returnForRevision(id: number, reason: string) { return this.http.post<KpiPlan>(`${this.base}/${id}/return`, { reason }, this.options); }
}
