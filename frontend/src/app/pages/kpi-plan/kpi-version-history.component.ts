import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { KpiRevision } from '../../models/kpi-revision.model';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { KpiChangesComponent } from './kpi-changes.component';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiRevisionActivityComponent } from './kpi-revision-activity.component';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef, inject } from '@angular/core';

@Component({
  selector: 'app-kpi-version-history', standalone: true,
  imports: [CommonModule, TranslateModule, NzModalModule, NzButtonModule, KpiChangesComponent, KpiItemEditorComponent, KpiRevisionActivityComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss', './kpi-revision.scss'],
  template: `
    <button nz-button [disabled]="busy || disabled" (click)="open()">{{ 'KPI_REVISIONS.HISTORY' | translate }}</button>
    <nz-modal [nzVisible]="visible" [nzTitle]="'KPI_REVISIONS.HISTORY' | translate" nzWidth="min(1040px, 96vw)"
      [nzFooter]="footer" (nzOnCancel)="visible = false">
      <ng-container *nzModalContent>
        @if (error) { <p class="error-box" role="alert">{{ error }}</p> }
        @if (busy) { <p role="status">{{ 'KPI_PLAN.LOADING' | translate }}</p> }
        @if (selected; as version) {
          <section class="version-summary">
            <h3>{{ 'KPI_REVISIONS.VERSION' | translate:{ number: version.versionNumber } }}
              <span class="status" [attr.data-status]="version.status">{{ 'KPI_REVISIONS.STATUS.' + version.status | translate }}</span></h3>
            <p class="help">{{ version.reviewPeriodName }} @if (version.departmentName) { / {{ version.departmentName }} }</p>
            @if (version.employeeName) { <p><strong>{{ version.employeeName }}</strong></p> }
            @if (effectiveOnly) {
              <p class="help">{{ (version.effectiveAt ? 'KPI_REVISIONS.EFFECTIVE_ON' : 'KPI_REVISIONS.LAST_UPDATED') | translate }}: {{ (version.effectiveAt || version.updatedAt) | date:'dd MMM yyyy, h:mm a' }}</p>
              @if (actorName(version) && (version.level !== 'INDIVIDUAL' || !version.approvedBy || (version.confirmedBy || version.createdBy) !== version.approvedBy)) {
                <p class="help">{{ (version.confirmedBy ? 'KPI_REVISIONS.CONFIRMER' : 'KPI_REVISIONS.PREPARER') | translate }}: {{ actorName(version) }}</p>
              }
              @if (version.level === 'INDIVIDUAL' && version.approvedBy) {
                <p class="help">{{ 'KPI_REVISIONS.APPROVER' | translate }}: {{ version.actorNames[version.approvedBy] || '-' }} · {{ version.approvedAt | date:'dd MMM yyyy, h:mm a' }}</p>
              }
            }
            @if (version.revisionReason) { <p class="preserve"><strong>{{ 'KPI_REVISIONS.REASON' | translate }}:</strong> {{ version.revisionReason }}</p> }
            @if (version.cancellationReason) { <p class="preserve"><strong>{{ 'KPI_REVISIONS.CANCEL_REASON' | translate }}:</strong> {{ version.cancellationReason }}</p> }
          </section>
          @if (version.baseVersionId) {
            <h4>{{ 'KPI_REVISIONS.COMPARED_WITH_BASE' | translate:{ number: baseNumber(version) } }}</h4>
            <app-kpi-changes [changes]="version.changes" [summaryTable]="true" emptyKey="KPI_REVISIONS.NO_CHANGES" />
          }
          <section class="history-plan">
            <button type="button" nz-button nzType="link" class="revision-disclosure" [attr.aria-expanded]="fullPlanVisible"
              [attr.aria-controls]="disclosureId + '-plan'" (click)="fullPlanVisible = !fullPlanVisible">
              {{ (fullPlanVisible ? 'KPI_REVISIONS.HIDE_FULL_PLAN' : 'KPI_REVISIONS.VIEW_FULL_PLAN') | translate }}
            </button>
            <div [id]="disclosureId + '-plan'" [hidden]="!fullPlanVisible">
              @if (fullPlanVisible) {
                @for (item of version.items; track item.id; let index = $index) {
                  <section class="history-item">
                    <div class="history-item-heading"><strong>{{ item.name || '-' }} · {{ item.weightage }}%</strong>
                      <button type="button" nz-button nzType="link" class="revision-disclosure" [attr.aria-expanded]="expandedItems.has(index)"
                        [attr.aria-controls]="disclosureId + '-item-' + index" (click)="toggleItem(index)">
                        {{ (expandedItems.has(index) ? 'KPI_REVISIONS.HIDE_KPI_DETAILS' : 'KPI_REVISIONS.VIEW_KPI_DETAILS') | translate }}
                      </button>
                    </div>
                    <div [id]="disclosureId + '-item-' + index" [hidden]="!expandedItems.has(index)">
                      @if (expandedItems.has(index)) {
                        <app-kpi-item-editor [items]="[item]" [singleItem]="true" [readonly]="true" [scope]="version.level" />
                      }
                    </div>
                  </section>
                }
              }
            </div>
          </section>
          @if (!effectiveOnly) {
            <section class="revision-activity">
              <button type="button" nz-button nzType="link" class="revision-disclosure" [attr.aria-expanded]="activityVisible"
                [attr.aria-controls]="disclosureId + '-activity'" (click)="activityVisible = !activityVisible">
                {{ (activityVisible ? 'KPI_REVISIONS.HIDE_ACTIVITY' : 'KPI_REVISIONS.VIEW_ACTIVITY') | translate }}
              </button>
              <div [id]="disclosureId + '-activity'" [hidden]="!activityVisible">
                @if (activityVisible) { <app-kpi-revision-activity [revision]="version" /> }
              </div>
            </section>
          }
          <h4 class="history-version-heading">{{ 'KPI_REVISIONS.HISTORY' | translate }}</h4>
          <div class="actions version-buttons">
            @for (entry of versions; track entry.id) {
              <button nz-button [nzType]="entry.id === version.id ? 'primary' : 'default'" (click)="selectVersion(entry)">
                {{ 'KPI_REVISIONS.VERSION' | translate:{ number: entry.versionNumber } }} — {{ 'KPI_REVISIONS.STATUS.' + entry.status | translate }}
              </button>
            }
          </div>
        } @else if (!busy && !error) { <p class="empty">{{ 'KPI_REVISIONS.NO_HISTORY' | translate }}</p> }
      </ng-container>
    </nz-modal>
    <ng-template #footer><button nz-button (click)="visible = false">{{ 'KPI_PLAN.CLOSE' | translate }}</button></ng-template>
  `
})
export class KpiVersionHistoryComponent {
  private static nextId = 0;
  readonly disclosureId = `revision-history-${KpiVersionHistoryComponent.nextId++}`;
  @Input({ required: true }) planId!: number;
  @Input() disabled = false;
  @Input() effectiveOnly = false;
  private readonly destroyRef = inject(DestroyRef);
  visible = false; busy = false; error = ''; versions: KpiRevision[] = []; selected: KpiRevision | null = null;
  fullPlanVisible = false; activityVisible = false; expandedItems = new Set<number>();
  constructor(private api: KpiRevisionService, private translate: TranslateService) {}
  open() {
    if (this.busy || this.disabled) return;
    this.visible = true; this.busy = true; this.error = ''; this.selected = null; this.versions = [];
    this.resetExpansion();
    this.api.history(this.planId).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: versions => { this.versions = versions.filter(v => !this.effectiveOnly || ['EFFECTIVE', 'SUPERSEDED'].includes(v.status))
          .sort((a, b) => b.versionNumber - a.versionNumber);
        this.selectVersion(this.versions.find(v => v.status === 'EFFECTIVE') ?? this.versions[0] ?? null); },
      error: e => this.error = e.error?.message || this.translate.instant('KPI_PLAN.ERROR')
    });
  }
  selectVersion(version: KpiRevision | null) { this.selected = version; this.resetExpansion(); }
  toggleItem(index: number) {
    if (this.expandedItems.has(index)) this.expandedItems.delete(index); else this.expandedItems.add(index);
  }
  private resetExpansion() { this.fullPlanVisible = false; this.activityVisible = false; this.expandedItems.clear(); }
  baseNumber(version: KpiRevision) { return this.versions.find(v => v.id === version.baseVersionId)?.versionNumber ?? '-'; }
  actorName(version: KpiRevision) {
    const actor = version.confirmedBy || version.createdBy;
    return version.actorNames?.[actor] || '';
  }
}
