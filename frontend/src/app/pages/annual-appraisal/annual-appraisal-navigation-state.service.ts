import { Injectable } from '@angular/core';
import { AnnualAppraisalStatus } from '../../models/annual-appraisal.model';

export type AppraisalQueueStatus = AnnualAppraisalStatus | 'READY' | 'NOT_READY';
export interface AppraisalQueueState {
  periodId: number; filter: AppraisalQueueStatus; search: string; department: string | null;
  pageIndex: number; pageSize: number; scrollTop: number;
}

@Injectable({ providedIn: 'root' })
export class AnnualAppraisalNavigationState {
  private key(userId: string | null, hr: boolean) { return `annual-appraisal-queue:${hr ? 'hr' : 'team'}:${userId}`; }
  read(userId: string | null, hr: boolean): AppraisalQueueState | null {
    try {
      const value: unknown = JSON.parse(sessionStorage.getItem(this.key(userId, hr)) || 'null');
      if (!value || typeof value !== 'object') return null;
      const v = value as Record<string, unknown>;
      const statuses = hr ? ['PENDING_REVIEW', 'RETURNED', 'APPROVED'] : ['READY', 'NOT_READY', 'DRAFT', 'RETURNED', 'PENDING_REVIEW', 'APPROVED'];
      if (!Number.isSafeInteger(v['periodId']) || Number(v['periodId']) < 1 || !statuses.includes(String(v['filter']))
        || typeof v['search'] !== 'string' || !(v['department'] === null || typeof v['department'] === 'string')
        || !Number.isSafeInteger(v['pageIndex']) || Number(v['pageIndex']) < 1
        || ![10, 20, 50, 100].includes(Number(v['pageSize'])) || typeof v['scrollTop'] !== 'number'
        || !Number.isFinite(v['scrollTop']) || v['scrollTop'] < 0) return null;
      return { periodId: Number(v['periodId']), filter: v['filter'] as AppraisalQueueStatus, search: v['search'],
        department: v['department'], pageIndex: Number(v['pageIndex']), pageSize: Number(v['pageSize']), scrollTop: v['scrollTop'] };
    } catch { return null; }
  }
  save(userId: string | null, hr: boolean, state: AppraisalQueueState) {
    try {
      // Whitelist queue fields. Recommendations and HR decision text never belong here.
      const { periodId, filter, search, department, pageIndex, pageSize, scrollTop } = state;
      sessionStorage.setItem(this.key(userId, hr), JSON.stringify({ periodId, filter, search, department, pageIndex, pageSize, scrollTop }));
      sessionStorage.setItem(`annual-appraisal-period:${hr ? 'hr' : 'team'}:${userId}`, String(periodId));
    } catch { /* Navigation still works when browser storage is unavailable. */ }
  }
  rememberPeriod(userId: string | null, hr: boolean, periodId: number) {
    const previous = this.read(userId, hr);
    if (previous?.periodId === periodId) return;
    this.save(userId, hr, { periodId, filter: hr ? 'PENDING_REVIEW' : 'READY', search: '', department: null, pageIndex: 1, pageSize: 10, scrollTop: 0 });
  }
}
