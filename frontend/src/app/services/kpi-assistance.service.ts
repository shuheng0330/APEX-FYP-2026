import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiAssistance, KpiAssistanceEmployee } from '../models/kpi-assistance.model';
import { KpiItem, KpiPlan } from '../models/kpi-plan.model';

@Injectable({ providedIn: 'root' })
export class KpiAssistanceService {
  private readonly base = `${environment.apiBaseUrl}/individual-kpi-assistance`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  employees() { return this.http.get<KpiAssistanceEmployee[]>(`${this.base}/employees`, this.options); }
  list() { return this.http.get<KpiAssistance[]>(this.base, this.options); }
  get(id: number) { return this.http.get<KpiAssistance>(`${this.base}/${id}`, this.options); }
  request(ownerParticipantId: number, requestReason: string) { return this.http.post<KpiAssistance>(this.base, { ownerParticipantId, requestReason }, this.options); }
  approve(id: number) { return this.http.post<KpiAssistance>(`${this.base}/${id}/authorize`, {}, this.options); }
  reject(id: number, reason: string) { return this.http.post<KpiAssistance>(`${this.base}/${id}/reject`, { reason }, this.options); }
  plan(id: number) { return this.http.get<KpiPlan>(`${this.base}/${id}/plan`, this.options); }
  createPlan(id: number, items: KpiItem[]) { return this.http.post<KpiPlan>(`${this.base}/${id}/plan`, { items }, this.options); }
  updatePlan(id: number, items: KpiItem[]) { return this.http.put<KpiPlan>(`${this.base}/${id}/plan`, { items }, this.options); }
  confirmPlan(id: number) { return this.http.post<KpiPlan>(`${this.base}/${id}/confirm`, {}, this.options); }
}
