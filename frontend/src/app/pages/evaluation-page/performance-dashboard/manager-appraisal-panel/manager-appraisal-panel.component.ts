import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { AppraisalRecordService } from '../../../../services/appraisal-record.service';
import { EvaluationCycleDto, EvaluationCycleService } from '../../../../services/evaluation-cycle.service';
import {
  AppraisalCategory,
  AppraisalDecisionSelection,
  AppraisalDecisionType,
  AppraisalReadinessDto,
  AppraisalRecordDto,
  AppraisalStatus
} from '../../../../models/appraisal-record.model';

interface DecisionCardState {
  key: 'promotion' | 'salary';
  title: string;
  readinessScore?: number | null;
  systemCategory?: AppraisalCategory | null;
  finalCategory?: AppraisalCategory | null;
  overrideEnabled: boolean;
  overrideReason: string;
}

@Component({
  selector: 'app-manager-appraisal-panel',
  standalone: true,
  templateUrl: './manager-appraisal-panel.component.html',
  styleUrls: ['./manager-appraisal-panel.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    NzAlertModule,
    NzButtonModule,
    NzCardModule,
    NzCheckboxModule,
    NzCollapseModule,
    NzIconModule,
    NzInputModule,
    NzRadioModule,
    NzSelectModule,
    NzSpinModule,
    NzTagModule
  ]
})
export class ManagerAppraisalPanelComponent implements OnChanges {
  @Input({ required: true }) staffId!: string;

  loading = true;
  scoreLoading = false;
  saving = false;
  submitting = false;
  noClosedCycle = false;

  latestClosedCycle?: EvaluationCycleDto;
  activeRecord?: AppraisalRecordDto;
  previousApprovedRecords: AppraisalRecordDto[] = [];

  reviewPeriodYears = 1;
  decisionType: AppraisalDecisionSelection = 'NONE';
  managerComment = '';
  aiInsight = '';
  generatedInsightVisible = false;

  promotionCard: DecisionCardState = this.createDecisionCard('promotion', 'Promotion');
  salaryCard: DecisionCardState = this.createDecisionCard('salary', 'Salary Increment');

  reviewPeriodOptions = [
    { label: 'Last 1 year', value: 1 },
    { label: 'Last 3 years', value: 3 },
    { label: 'Last 5 years', value: 5 }
  ];

  categoryOptions: AppraisalCategory[] = ['READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'];

  constructor(
    private appraisalRecordService: AppraisalRecordService,
    private evaluationCycleService: EvaluationCycleService,
    private message: NzMessageService
  ) { }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['staffId'] && this.staffId) {
      this.loadPanelData();
    }
  }

  loadPanelData(): void {
    this.loading = true;
    forkJoin({
      cycles: this.evaluationCycleService.getHistory(),
      records: this.appraisalRecordService.getByStaff(this.staffId)
    }).subscribe({
      next: ({ cycles, records }) => {
        this.latestClosedCycle = this.findLatestClosedCycle(cycles);
        this.noClosedCycle = !this.latestClosedCycle?.id;
        this.prepareRecords(records);

        if (!this.noClosedCycle && !this.activeRecord) {
          this.resetForm();
        }

        if (!this.noClosedCycle && this.decisionType !== 'NONE' && !this.activeRecord) {
          this.fetchReadinessScore();
        }

        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.message.error('Unable to load appraisal records.');
      }
    });
  }

  onReviewPeriodChange(): void {
    if (!this.isReadOnly && this.decisionType !== 'NONE') {
      this.fetchReadinessScore();
    }
  }

  onDecisionTypeChange(): void {
    if (this.decisionType === 'NONE') {
      this.clearScoreCards();
      return;
    }

    if (!this.isReadOnly) {
      this.fetchReadinessScore();
    }
  }

  generateInsight(): void {
    this.aiInsight = 'AI insight generation will be available in a future update';
    this.generatedInsightVisible = true;
  }

  saveDraft(): void {
    if (!this.canPersist) {
      return;
    }

    if (!this.validateOverrides()) {
      return;
    }

    this.saving = true;
    this.appraisalRecordService.saveDraft(this.buildRequest()).subscribe({
      next: (record) => {
        this.activeRecord = record;
        this.applyRecordToForm(record);
        this.saving = false;
        this.message.success('Appraisal draft saved.');
      },
      error: () => {
        this.saving = false;
        this.message.error('Unable to save appraisal draft.');
      }
    });
  }

  submitToHr(): void {
    if (!this.canPersist) {
      return;
    }

    if (!this.managerComment?.trim() || this.managerComment.trim().length < 10) {
      this.message.error('Manager comment must be at least 10 characters before submitting to HR.');
      return;
    }

    if (!this.validateOverrides()) {
      return;
    }

    this.submitting = true;
    this.appraisalRecordService.saveDraft(this.buildRequest()).subscribe({
      next: (savedRecord) => {
        if (!savedRecord.id) {
          this.submitting = false;
          this.message.error('Unable to submit appraisal because the saved record id is missing.');
          return;
        }

        this.appraisalRecordService.submit(savedRecord.id).subscribe({
          next: (submittedRecord) => {
            this.activeRecord = submittedRecord;
            this.applyRecordToForm(submittedRecord);
            this.submitting = false;
            this.message.success('Appraisal submitted to HR.');
          },
          error: () => {
            this.submitting = false;
            this.message.error('Unable to submit appraisal to HR.');
          }
        });
      },
      error: () => {
        this.submitting = false;
        this.message.error('Unable to save appraisal before submission.');
      }
    });
  }

  get visibleCards(): DecisionCardState[] {
    if (this.decisionType === 'PROMOTION') {
      return [this.promotionCard];
    }
    if (this.decisionType === 'SALARY_INCREMENT') {
      return [this.salaryCard];
    }
    if (this.decisionType === 'BOTH') {
      return [this.promotionCard, this.salaryCard];
    }
    return [];
  }

  get canPersist(): boolean {
    return !this.noClosedCycle
      && !this.isReadOnly
      && this.decisionType !== 'NONE'
      && !!this.latestClosedCycle?.id
      && !this.saving
      && !this.submitting;
  }

  get isReadOnly(): boolean {
    return this.activeRecord?.status === 'PENDING_REVIEW' || this.activeRecord?.status === 'APPROVED';
  }

  get isReturned(): boolean {
    return this.activeRecord?.status === 'RETURNED';
  }

  get pendingBannerVisible(): boolean {
    return this.activeRecord?.status === 'PENDING_REVIEW';
  }

  get approvedBannerVisible(): boolean {
    return this.activeRecord?.status === 'APPROVED';
  }

  get status(): AppraisalStatus | undefined {
    return this.activeRecord?.status;
  }

  get statusLabel(): string {
    switch (this.status) {
      case 'DRAFT':
        return 'Draft';
      case 'PENDING_REVIEW':
        return 'Pending Review';
      case 'RETURNED':
        return 'Returned';
      case 'APPROVED':
        return 'Approved';
      default:
        return '';
    }
  }

  get statusIcon(): string {
    switch (this.status) {
      case 'DRAFT':
        return 'edit';
      case 'PENDING_REVIEW':
        return 'clock-circle';
      case 'RETURNED':
        return 'rollback';
      case 'APPROVED':
        return 'check-circle';
      default:
        return 'info-circle';
    }
  }

  get approvedDate(): string | null | undefined {
    return this.activeRecord?.approvedAt;
  }

  get returnReason(): string {
    return this.activeRecord?.hrReturnReason || '';
  }

  get categoryTagColor(): Record<AppraisalCategory, string> {
    return {
      READY: 'green',
      BORDERLINE: 'orange',
      NEEDS_IMPROVEMENT: 'red'
    };
  }

  getCategoryColor(category?: AppraisalCategory | null): string {
    if (!category) return 'default';
    return this.categoryTagColor[category];
  }

  get finalCategorySummary(): string {
    const categories = this.visibleCards
      .map(card => card.finalCategory || card.systemCategory)
      .filter(Boolean);

    return categories.map(category => this.formatCategory(category as AppraisalCategory)).join(' / ');
  }

  formatDecisionType(decisionType?: string | null): string {
    if (decisionType === 'PROMOTION') return 'Promotion';
    if (decisionType === 'SALARY_INCREMENT') return 'Salary Increment';
    if (decisionType === 'BOTH') return 'Both';
    return 'None';
  }

  formatCategory(category?: AppraisalCategory | null): string {
    if (!category) return '-';
    return category.replace(/_/g, ' ');
  }

  getRecordFinalCategory(record: AppraisalRecordDto): AppraisalCategory | null | undefined {
    if (record.decisionType === 'PROMOTION') return record.promotionFinalCategory;
    if (record.decisionType === 'SALARY_INCREMENT') return record.salaryFinalCategory;
    return record.promotionFinalCategory || record.salaryFinalCategory;
  }

  private prepareRecords(records: AppraisalRecordDto[]): void {
    const latestCycleId = this.latestClosedCycle?.id;
    this.activeRecord = latestCycleId
      ? records.find(record => record.evaluationCycleId === latestCycleId)
      : undefined;

    this.previousApprovedRecords = records
      .filter(record => record.status === 'APPROVED' && record.evaluationCycleId !== latestCycleId)
      .sort((a, b) => new Date(b.evaluationCycleEndDate || b.createdAt || '').getTime()
        - new Date(a.evaluationCycleEndDate || a.createdAt || '').getTime());

    if (this.activeRecord) {
      this.applyRecordToForm(this.activeRecord);
    }
  }

  private applyRecordToForm(record: AppraisalRecordDto): void {
    this.reviewPeriodYears = record.reviewPeriodYears ?? 1;
    this.decisionType = record.decisionType;
    this.managerComment = record.managerComment || '';
    this.aiInsight = record.aiInsight || '';
    this.generatedInsightVisible = !!record.aiInsight;

    this.promotionCard = this.createDecisionCard('promotion', 'Promotion');
    this.salaryCard = this.createDecisionCard('salary', 'Salary Increment');

    this.promotionCard.readinessScore = record.promotionReadinessScore;
    this.promotionCard.systemCategory = record.promotionSystemCategory;
    this.promotionCard.finalCategory = record.promotionFinalCategory || record.promotionSystemCategory;
    this.promotionCard.overrideEnabled = !!record.promotionOverrideReason
      || (!!record.promotionFinalCategory && record.promotionFinalCategory !== record.promotionSystemCategory);
    this.promotionCard.overrideReason = record.promotionOverrideReason || '';

    this.salaryCard.readinessScore = record.salaryReadinessScore;
    this.salaryCard.systemCategory = record.salarySystemCategory;
    this.salaryCard.finalCategory = record.salaryFinalCategory || record.salarySystemCategory;
    this.salaryCard.overrideEnabled = !!record.salaryOverrideReason
      || (!!record.salaryFinalCategory && record.salaryFinalCategory !== record.salarySystemCategory);
    this.salaryCard.overrideReason = record.salaryOverrideReason || '';
  }

  private fetchReadinessScore(): void {
    if (!this.latestClosedCycle?.id) {
      return;
    }

    this.scoreLoading = true;
    this.appraisalRecordService.getReadinessScore(this.staffId, this.latestClosedCycle.id, this.reviewPeriodYears)
      .subscribe({
        next: (readiness) => {
          this.applyReadinessToVisibleCards(readiness);
          this.scoreLoading = false;
        },
        error: () => {
          this.scoreLoading = false;
          this.message.error('Unable to calculate readiness score.');
        }
      });
  }

  private applyReadinessToVisibleCards(readiness: AppraisalReadinessDto): void {
    for (const card of this.visibleCards) {
      card.readinessScore = readiness.readinessScore;
      card.systemCategory = readiness.systemCategory;

      if (!card.overrideEnabled) {
        card.finalCategory = readiness.systemCategory;
      }
    }
  }

  private buildRequest(): AppraisalRecordDto {
    const decisionType = this.decisionType as AppraisalDecisionType;

    return {
      id: this.activeRecord?.id,
      staffId: this.staffId,
      evaluationCycleId: this.latestClosedCycle!.id!,
      reviewPeriodYears: this.reviewPeriodYears,
      decisionType,
      promotionReadinessScore: this.includesPromotion(decisionType) ? this.promotionCard.readinessScore : null,
      salaryReadinessScore: this.includesSalary(decisionType) ? this.salaryCard.readinessScore : null,
      promotionSystemCategory: this.includesPromotion(decisionType) ? this.promotionCard.systemCategory : null,
      salarySystemCategory: this.includesSalary(decisionType) ? this.salaryCard.systemCategory : null,
      promotionFinalCategory: this.includesPromotion(decisionType)
        ? this.resolveFinalCategory(this.promotionCard)
        : null,
      salaryFinalCategory: this.includesSalary(decisionType)
        ? this.resolveFinalCategory(this.salaryCard)
        : null,
      promotionOverrideReason: this.includesPromotion(decisionType) && this.promotionCard.overrideEnabled
        ? this.promotionCard.overrideReason
        : null,
      salaryOverrideReason: this.includesSalary(decisionType) && this.salaryCard.overrideEnabled
        ? this.salaryCard.overrideReason
        : null,
      managerComment: this.managerComment,
      aiInsight: this.aiInsight
    };
  }

  private validateOverrides(): boolean {
    for (const card of this.visibleCards) {
      if (card.overrideEnabled && !card.finalCategory) {
        this.message.error(`${card.title} override category is required.`);
        return false;
      }

      if (card.overrideEnabled && !card.overrideReason.trim()) {
        this.message.error(`${card.title} override reason is required.`);
        return false;
      }
    }

    return true;
  }

  private resolveFinalCategory(card: DecisionCardState): AppraisalCategory | null | undefined {
    if (card.overrideEnabled) {
      return card.finalCategory;
    }
    return card.systemCategory;
  }

  private includesPromotion(decisionType: AppraisalDecisionType): boolean {
    return decisionType === 'PROMOTION' || decisionType === 'BOTH';
  }

  private includesSalary(decisionType: AppraisalDecisionType): boolean {
    return decisionType === 'SALARY_INCREMENT' || decisionType === 'BOTH';
  }

  private resetForm(): void {
    this.reviewPeriodYears = 1;
    this.decisionType = 'NONE';
    this.managerComment = '';
    this.aiInsight = '';
    this.generatedInsightVisible = false;
    this.clearScoreCards();
  }

  private clearScoreCards(): void {
    this.promotionCard = this.createDecisionCard('promotion', 'Promotion');
    this.salaryCard = this.createDecisionCard('salary', 'Salary Increment');
  }

  private createDecisionCard(key: 'promotion' | 'salary', title: string): DecisionCardState {
    return {
      key,
      title,
      readinessScore: null,
      systemCategory: null,
      finalCategory: null,
      overrideEnabled: false,
      overrideReason: ''
    };
  }

  private findLatestClosedCycle(cycles: EvaluationCycleDto[]): EvaluationCycleDto | undefined {
    return cycles
      .filter(cycle => cycle.status === 'CLOSED')
      .sort((a, b) => new Date(b.endDate).getTime() - new Date(a.endDate).getTime())[0];
  }
}
