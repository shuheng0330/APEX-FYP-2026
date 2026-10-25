import { Injectable, inject } from '@angular/core';
import { ActivatedRoute, ActivatedRouteSnapshot, BaseRouteReuseStrategy, ParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable, catchError, map, of, throwError } from 'rxjs';

/** Recreate only notification destinations, after their existing canDeactivate guards have succeeded. */
@Injectable()
export class NotificationRouteReuseStrategy extends BaseRouteReuseStrategy {
  override shouldReuseRoute(future: ActivatedRouteSnapshot, current: ActivatedRouteSnapshot): boolean {
    if (future.data['notificationWorkspace'] && notificationSelectionChanged(current, future)) return false;
    return super.shouldReuseRoute(future, current);
  }
}

/** Tab-only changes retain the existing guarded tab navigation; record changes run route guards first. */
export function notificationSelectionChanged(current: ActivatedRouteSnapshot, future: ActivatedRouteSnapshot): boolean {
  if (current.queryParamMap.get('notice') !== future.queryParamMap.get('notice')) return true;
  if (current.queryParamMap.get('reviewPeriodId') !== future.queryParamMap.get('reviewPeriodId')) return true;
  const keys = ['planId', 'checkpointId', 'planVersionId', 'assessmentId', 'attitudeAssessmentId', 'revisionId', 'assistanceId', 'view'];
  if (current.queryParamMap.get('tab') !== future.queryParamMap.get('tab') && keys.every(key => !future.queryParamMap.has(key))) return false;
  return keys
    .some(key => current.queryParamMap.getAll(key).join('|') !== future.queryParamMap.getAll(key).join('|'));
}

export function localNotificationUrl(link: string): boolean {
  return link.startsWith('/') && !link.startsWith('//') && !/[\\\u0000-\u0020]/.test(link);
}

/** Initial URL selections are consumed once; later manual filters/refreshes remain in the user's control. */
export class NotificationTarget {
  unavailable = false;
  private consumed = new Set<string>();
  constructor(private params: ParamMap | undefined, private translate: TranslateService) {}
  take(key: string): number | null {
    if (this.unavailable) this.fail();
    if (this.consumed.has(key)) return null;
    this.consumed.add(key);
    return this.value(key);
  }
  value(key: string): number | null {
    const values = this.params?.getAll(key) ?? [];
    if (!values.length) return null;
    if (values.length !== 1 || !/^[1-9]\d*$/.test(values[0]) || !Number.isSafeInteger(Number(values[0]))) this.fail();
    return Number(values[0]);
  }
  period(id: number) { const expected = this.value('reviewPeriodId'); this.require(expected === null || expected === id); }
  takeTab(allowed: readonly string[]): string | null {
    return this.takeOption('tab', allowed);
  }
  validateReviewTab(allowed: readonly string[]) {
    // Preserve the existing fallback for old tab-only bookmarks, but never retarget a notification/record link.
    if (this.params?.has('notice') || ['planId', 'assessmentId', 'attitudeAssessmentId', 'assistanceId', 'revisionId'].some(key => this.params?.has(key)))
      this.takeTab(allowed);
  }
  takeOption(key: string, allowed: readonly string[]): string | null {
    if (this.unavailable) this.fail();
    if (this.consumed.has(key)) return null;
    this.consumed.add(key);
    const values = this.params?.getAll(key) ?? [];
    this.require(values.length <= 1 && (!values.length || allowed.includes(values[0]))); return values[0] ?? null;
  }
  choose<T>(rows: readonly T[], key: string, id: (row: T) => number, fallback: number | null): number | null {
    const requested = this.take(key);
    if (requested === null) return fallback;
    if (!rows.some(row => id(row) === requested)) this.fail();
    return requested;
  }
  record<T extends { id: number; reviewPeriodId: number }>(rows: readonly T[], key: string, periodId?: number | null): T | null {
    const id = this.take(key); if (id === null) return null;
    const row = rows.find(row => row.id === id);
    this.require(!!row && (periodId == null || row.reviewPeriodId === periodId));
    this.period(row!.reviewPeriodId); return row!;
  }
  load<T extends { id: number | null; reviewPeriodId: number }>(row: { id: number; reviewPeriodId: number } | null,
    read: (id: number) => Observable<T>): Observable<T | null> {
    return row ? read(row.id).pipe(map(fresh => {
      this.require(fresh.id === row.id && fresh.reviewPeriodId === row.reviewPeriodId); return fresh;
    }), catchError(error => {
      if (error.status === 403 || error.status === 404) this.unavailable = true;
      return throwError(() => error);
    })) : of(null);
  }
  require(valid: boolean) { if (!valid) this.fail(); }
  fail(): never {
    this.unavailable = true;
    throw { error: { message: this.translate.instant('NOTIFICATIONS.TARGET_UNAVAILABLE') } };
  }
}

export function notificationTarget(): NotificationTarget {
  return new NotificationTarget(inject(ActivatedRoute, { optional: true })?.snapshot.queryParamMap, inject(TranslateService));
}
