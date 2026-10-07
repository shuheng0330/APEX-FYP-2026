import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { forkJoin, finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { KpiPlan, KpiItem, KpiPeriodContext } from '../../models/kpi-plan.model';
import { KpiPlanService } from '../../services/kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
@Component({
  standalone: true, imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, KpiItemEditorComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  template: `
    <main class="review-page">
      <div class="page-heading"><div><h2>{{ 'KPI_PLAN.COMPANY_TITLE' | translate }}</h2><p class="help">{{ 'KPI_PLAN.COMPANY_HELP' | translate }}</p></div></div>
      @if (error) { <div class="error-box" role="alert">{{ error }}</div> }
      @if (success) { <div class="notice" role="status">{{ success }}</div> }
      <section class="card"><div class="card-body">
        <label class="field">{{ 'KPI_PLAN.PERIOD' | translate }}
          <nz-select class="plan-picker" [(ngModel)]="selectedId" (ngModelChange)="selectPeriod()" [nzDisabled]="busy">
            @for (period of periods; track period.id) { <nz-option [nzValue]="period.id" [nzLabel]="(period.name || '-') + ' (' + period.status + ')'" /> }
          </nz-select>
        </label>
        @if (selectedPeriod) {
          <p class="help">{{ 'KPI_PLAN.SETUP_DEADLINE' | translate }}: {{ selectedPeriod.kpiSetupDeadline | date:'dd MMM yyyy' }}</p>
          <p><span class="status" [attr.data-status]="plan?.status || 'DRAFT'">{{ 'KPI_PLAN.STATUS.' + (plan?.status || 'DRAFT') | translate }}</span></p>
          @if (plan?.overdue) { <p class="warning">{{ 'KPI_PLAN.LATE_HELP' | translate }}</p> }
          @if (readonly) { <p class="notice">{{ 'KPI_PLAN.READ_ONLY' | translate }}</p> }
        }
      </div></section>
      @if (selectedPeriod) {
        <app-kpi-item-editor [items]="items" [readonly]="readonly || busy" />
        @if (!readonly) {
          <div class="footer-actions"><button nz-button nzType="primary" [nzLoading]="busy" (click)="save()">{{ 'KPI_PLAN.SAVE_DRAFT' | translate }}</button></div>
        }
      }
    </main>
  `
})
export class CompanyKpiPlanComponent implements OnInit {
  private destroyRef = inject(DestroyRef);
  periods: KpiPeriodContext[] = []; plans: KpiPlan[] = []; selectedId: number | null = null;
  plan: KpiPlan | null = null; items: KpiItem[] = []; busy = false; error = ''; success = '';
  constructor(private api: KpiPlanService, private translate: TranslateService) {}
  ngOnInit() {
    this.busy = true;
    forkJoin({ periods: this.api.periods(), plans: this.api.list() }).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.periods = result.periods; this.plans = result.plans; this.selectedId = this.periods[0]?.id ?? null; this.selectPeriod(); },
      error: e => this.fail(e)
    });
  }
  get selectedPeriod() { return this.periods.find(p => p.id === this.selectedId); }
  get readonly() { return this.selectedPeriod?.status === 'CLOSED' || (!!this.plan && this.plan.status !== 'DRAFT'); }
  selectPeriod() { this.plan = this.plans.find(p => p.reviewPeriodId === this.selectedId) ?? null; this.items = structuredClone(this.plan?.items ?? []); this.error = ''; this.success = ''; }
  save() {
    if (this.busy || this.readonly || this.selectedId === null) return;
    this.busy = true; this.error = ''; this.success = '';
    const request = { reviewPeriodId: this.selectedId, items: this.items };
    const operation = this.plan ? this.api.update(this.plan.id, request) : this.api.create(request);
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.plan = plan; this.plans = [...this.plans.filter(p => p.id !== plan.id), plan]; this.items = structuredClone(plan.items); this.success = this.translate.instant('KPI_PLAN.SAVED'); },
      error: e => this.fail(e)
    });
  }
  private fail(error: { error?: { message?: string } }) { this.error = error.error?.message || this.translate.instant('KPI_PLAN.ERROR'); }
}
