import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { KpiItem, KpiLevel, KpiPeriodContext, KpiPlan } from '../models/kpi-plan.model';
import { KpiAmendmentAccess, KpiRevision, KpiRevisionDecision, KpiRevisionPreparationMode } from '../models/kpi-revision.model';

@Injectable({ providedIn: 'root' })
export class KpiRevisionService {
  private readonly base = environment.apiBaseUrl;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  periods() { return this.http.get<KpiPeriodContext[]>(`${this.base}/kpi-plan-revisions/periods`, this.options); }
  plans(reviewPeriodId: number) {
    return this.http.get<KpiPlan[]>(`${this.base}/kpi-plan-revisions/plans`, { ...this.options, params: { reviewPeriodId } });
  }
  list(reviewPeriodId: number, level?: KpiLevel) {
    return this.http.get<KpiRevision[]>(`${this.base}/kpi-plan-revisions`, { ...this.options, params: { reviewPeriodId, ...(level ? { level } : {}) } });
  }
  get(id: number) { return this.http.get<KpiRevision>(`${this.base}/kpi-plan-revisions/${id}`, this.options); }
  history(planId: number) { return this.http.get<KpiRevision[]>(`${this.base}/kpi-plan-revisions/plans/${planId}/versions`, this.options); }
  start(plan: KpiPlan, reason: string, preparationMode: KpiRevisionPreparationMode = 'SUPERIOR') {
    const route = plan.level === 'COMPANY' ? 'company-kpi-plans' : plan.level === 'DEPARTMENT' ? 'department-kpi-plans' : 'individual-kpi-plans';
    const action = plan.level === 'COMPANY' ? 'revisions' : 'revision-requests';
    return this.http.post<KpiRevision>(`${this.base}/${route}/${plan.id}/${action}`,
      { reason: reason.trim(), ...(plan.level === 'INDIVIDUAL' ? { preparationMode } : {}) }, this.options);
  }
  save(revision: KpiRevision, items: KpiItem[]) {
    return this.http.put<KpiRevision>(`${this.base}/kpi-plan-revisions/${revision.id}`,
      { expectedLockVersion: revision.lockVersion, items }, this.options);
  }
  decide(revision: KpiRevision, action: KpiRevisionDecision, reason?: string) {
    return this.http.post<KpiRevision>(`${this.base}/kpi-plan-revisions/${revision.id}/${action.toLowerCase()}`,
      { expectedLockVersion: revision.lockVersion, ...(reason !== undefined ? { reason: reason.trim() } : {}) }, this.options);
  }
  access(periodId: number) { return this.http.get<KpiAmendmentAccess>(`${this.base}/kpi-amendment-access/${periodId}`, this.options); }
  saveAccess(periodId: number, enabled: boolean, closeDate: string | null) {
    return this.http.put<KpiAmendmentAccess>(`${this.base}/kpi-amendment-access/${periodId}`, { enabled, closeDate: closeDate || null }, this.options);
  }
}
