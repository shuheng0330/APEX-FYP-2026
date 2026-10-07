import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzMessageService } from 'ng-zorro-antd/message';
import { Subject, catchError, finalize, of, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AnnualReviewPeriod, ReviewPeriodStatus, editableReviewPeriod } from '../../models/annual-review-period.model';
import { AnnualReviewPeriodService } from '../../services/annual-review-period.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslateModule, NzButtonModule, NzInputModule, NzSelectModule, NzModalModule],
  templateUrl: './review-period-list.component.html',
  styleUrl: './review-period.scss'
})
export class ReviewPeriodListComponent implements OnInit {
  periods: AnnualReviewPeriod[] = [];
  query = '';
  status: ReviewPeriodStatus | null = null;
  statuses: ReviewPeriodStatus[] = ['DRAFT', 'UPCOMING', 'OPEN', 'CLOSED'];
  loading = false;
  deleting: number | null = null;
  error = '';
  editable = editableReviewPeriod;
  deletable(period: AnnualReviewPeriod): boolean { return this.editable(period) && period.canDelete !== false; }
  private reload$ = new Subject<void>();
  private destroyRef = inject(DestroyRef);
  constructor(private api: AnnualReviewPeriodService, private modal: NzModalService,
    private translate: TranslateService, private message: NzMessageService) {}

  ngOnInit(): void {
    this.reload$.pipe(startWith(undefined), switchMap(() => {
      this.loading = true; this.error = '';
      return this.api.list().pipe(catchError(error => {
        this.error = error.error?.message || this.translate.instant('REVIEW_PERIOD.LOAD_ERROR');
        return of(null);
      }), finalize(() => this.loading = false));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(periods => { if (periods) this.periods = periods; });
  }

  get filtered(): AnnualReviewPeriod[] {
    const query = this.query.trim().toLowerCase();
    return this.periods.filter(period => (!this.status || period.status === this.status) && (period.name ?? '').toLowerCase().includes(query));
  }

  retry(): void { this.reload$.next(); }

  confirmDelete(period: AnnualReviewPeriod): void {
    if (!this.deletable(period) || period.id === null || this.deleting !== null) return;
    this.modal.confirm({
      nzTitle: this.translate.instant('REVIEW_PERIOD.DELETE_TITLE'),
      nzContent: this.translate.instant('REVIEW_PERIOD.DELETE_DETAIL', { name: period.name }),
      nzOkText: this.translate.instant('REVIEW_PERIOD.DELETE'), nzOkDanger: true,
      nzCancelText: this.translate.instant('REVIEW_PERIOD.CANCEL'),
      nzOnOk: () => this.remove(period.id!)
    });
  }

  private remove(id: number): void {
    this.deleting = id;
    this.api.delete(id).pipe(finalize(() => this.deleting = null), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.message.success(this.translate.instant('REVIEW_PERIOD.DELETED')); this.reload$.next(); },
      error: error => { this.error = error.error?.message || this.translate.instant('REVIEW_PERIOD.DELETE_ERROR'); }
    });
  }
}
