import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges, TemplateRef, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';

import {
  AppraisalCategory,
  AppraisalDecisionType,
  AppraisalRecordDto
} from '../../../../models/appraisal-record.model';
import { AppraisalRecordService } from '../../../../services/appraisal-record.service';

interface ReviewDecisionCard {
  title: string;
  readinessScore?: number | null;
  systemCategory?: AppraisalCategory | null;
  finalCategory?: AppraisalCategory | null;
  overrideReason?: string | null;
  hasManagerOverride: boolean;
}

@Component({
  selector: 'app-hr-appraisal-review-panel',
  standalone: true,
  templateUrl: './hr-appraisal-review-panel.component.html',
  styleUrls: ['./hr-appraisal-review-panel.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    NzAlertModule,
    NzButtonModule,
    NzCardModule,
    NzIconModule,
    NzInputModule,
    NzModalModule,
    NzSelectModule,
    NzSpinModule,
    NzTagModule
  ]
})
export class HrAppraisalReviewPanelComponent implements OnChanges {
  @Input({ required: true }) staffId!: string;
  @Input() appraisalId: string | null = null;

  @ViewChild('overrideModalTemplate', { static: true }) overrideModalTemplate!: TemplateRef<unknown>;
  @ViewChild('returnModalTemplate', { static: true }) returnModalTemplate!: TemplateRef<unknown>;

  loading = true;
  actionLoading = false;
  record?: AppraisalRecordDto;
  decisionCards: ReviewDecisionCard[] = [];
  overrideCategory?: AppraisalCategory;
  overrideReason = '';
  returnReason = '';
  private actionModal?: NzModalRef;

  readonly categoryOptions: AppraisalCategory[] = ['READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'];

  constructor(
    private appraisalRecordService: AppraisalRecordService,
    private modal: NzModalService,
    private message: NzMessageService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['staffId'] || changes['appraisalId']) && this.staffId) {
      this.loadRecord();
    }
  }

  loadRecord(): void {
    this.loading = true;
    this.appraisalRecordService.getReviewRecords().subscribe({
      next: (records) => {
        this.record = this.selectRecord(records);
        this.decisionCards = this.buildDecisionCards(this.record);
        this.loading = false;
      },
      error: () => {
        this.record = undefined;
        this.decisionCards = [];
        this.loading = false;
        this.message.error('Unable to load the appraisal review record.');
      }
    });
  }

  get canReview(): boolean {
    return this.record?.status === 'PENDING_REVIEW' && !this.actionLoading;
  }

  get statusLabel(): string {
    return this.formatValue(this.record?.status);
  }

  get statusIcon(): string {
    switch (this.record?.status) {
      case 'PENDING_REVIEW': return 'clock-circle';
      case 'RETURNED': return 'rollback';
      case 'APPROVED': return 'check-circle';
      default: return 'info-circle';
    }
  }

  approve(): void {
    if (!this.record?.id || !this.canReview) {
      return;
    }

    this.modal.confirm({
      nzTitle: 'Approve appraisal?',
      nzContent: 'This will approve and lock the appraisal record.',
      nzOkText: 'Approve',
      nzOkType: 'primary',
      nzOnOk: () => this.performApprove()
    });
  }

  openOverrideModal(): void {
    this.overrideCategory = undefined;
    this.overrideReason = '';
    this.actionModal = this.modal.create({
      nzTitle: 'Override and Approve',
      nzContent: this.overrideModalTemplate,
      nzFooter: null,
      nzMaskClosable: false
    });
  }

  openReturnModal(): void {
    this.returnReason = '';
    this.actionModal = this.modal.create({
      nzTitle: 'Return for Revision',
      nzContent: this.returnModalTemplate,
      nzFooter: null,
      nzMaskClosable: false
    });
  }

  submitOverride(): void {
    if (!this.record?.id || !this.overrideCategory || !this.overrideReason.trim()) {
      this.message.error('HR override category and reason are required.');
      return;
    }

    this.actionLoading = true;
    this.appraisalRecordService.overrideAndApprove(this.record.id, {
      hrOverrideCategory: this.overrideCategory,
      hrOverrideReason: this.overrideReason.trim()
    }).subscribe({
      next: (record) => {
        this.finishAction(record, 'Appraisal overridden and approved.');
      },
      error: () => {
        this.actionLoading = false;
        this.message.error('Unable to override and approve the appraisal.');
      }
    });
  }

  submitReturn(): void {
    if (!this.record?.id || !this.returnReason.trim()) {
      this.message.error('Return reason is required.');
      return;
    }

    this.actionLoading = true;
    this.appraisalRecordService.returnForRevision(this.record.id, {
      hrReturnReason: this.returnReason.trim()
    }).subscribe({
      next: (record) => {
        this.finishAction(record, 'Appraisal returned for revision.');
      },
      error: () => {
        this.actionLoading = false;
        this.message.error('Unable to return the appraisal for revision.');
      }
    });
  }

  closeActionModal(): void {
    this.actionModal?.destroy();
  }

  getCategoryColor(category?: AppraisalCategory | null): string {
    if (category === 'READY') return 'green';
    if (category === 'BORDERLINE') return 'orange';
    if (category === 'NEEDS_IMPROVEMENT') return 'red';
    return 'default';
  }

  formatValue(value?: string | null): string {
    if (!value) return '-';
    return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
  }

  formatReviewPeriod(years?: number): string {
    return years === 1 ? 'Last 1 year' : `Last ${years ?? '-'} years`;
  }

  private performApprove(): void {
    if (!this.record?.id) {
      return;
    }

    this.actionLoading = true;
    this.appraisalRecordService.approve(this.record.id).subscribe({
      next: (record) => this.finishAction(record, 'Appraisal approved.'),
      error: () => {
        this.actionLoading = false;
        this.message.error('Unable to approve the appraisal.');
      }
    });
  }

  private finishAction(record: AppraisalRecordDto, successMessage: string): void {
    this.record = record;
    this.decisionCards = this.buildDecisionCards(record);
    this.actionLoading = false;
    this.actionModal?.destroy();
    this.message.success(successMessage);
  }

  private buildDecisionCards(record?: AppraisalRecordDto): ReviewDecisionCard[] {
    if (!record) {
      return [];
    }

    const cards: ReviewDecisionCard[] = [];
    if (this.includesPromotion(record.decisionType)) {
      cards.push(this.createDecisionCard(
        'Promotion',
        record.promotionReadinessScore,
        record.promotionSystemCategory,
        record.promotionFinalCategory,
        record.promotionOverrideReason
      ));
    }
    if (this.includesSalary(record.decisionType)) {
      cards.push(this.createDecisionCard(
        'Salary Increment',
        record.salaryReadinessScore,
        record.salarySystemCategory,
        record.salaryFinalCategory,
        record.salaryOverrideReason
      ));
    }
    return cards;
  }

  private createDecisionCard(
    title: string,
    readinessScore?: number | null,
    systemCategory?: AppraisalCategory | null,
    finalCategory?: AppraisalCategory | null,
    overrideReason?: string | null
  ): ReviewDecisionCard {
    return {
      title,
      readinessScore,
      systemCategory,
      finalCategory,
      overrideReason,
      hasManagerOverride: !!overrideReason || (!!finalCategory && finalCategory !== systemCategory)
    };
  }

  private selectRecord(records: AppraisalRecordDto[]): AppraisalRecordDto | undefined {
    if (this.appraisalId) {
      return records.find(record => record.id === this.appraisalId && record.staffId === this.staffId);
    }

    return records
      .filter(record => record.staffId === this.staffId)
      .sort((a, b) => this.getRecordDate(b) - this.getRecordDate(a))[0];
  }

  private getRecordDate(record: AppraisalRecordDto): number {
    return new Date(record.evaluationCycleEndDate || record.submittedAt || record.createdAt || '').getTime();
  }

  private includesPromotion(decisionType: AppraisalDecisionType): boolean {
    return decisionType === 'PROMOTION' || decisionType === 'BOTH';
  }

  private includesSalary(decisionType: AppraisalDecisionType): boolean {
    return decisionType === 'SALARY_INCREMENT' || decisionType === 'BOTH';
  }
}
