import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormArray, FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, finalize, forkJoin, of, Subject, switchMap } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzMessageService } from 'ng-zorro-antd/message';
import { AnnualReviewCreationDefaults, AnnualReviewPeriod, AnnualReviewPeriodRequest, calendarDate, ConsolidationMethod,
  EmployeeLevelConfiguration, editableReviewPeriod, pickerDate, REVIEW_DATE_FIELDS, REVIEW_FREQUENCIES, ReviewFrequency,
  ReviewRoleConfiguration, weightTotal } from '../../models/annual-review-period.model';
import { EmployeeLevel } from '../../models/role.model';
import { AnnualReviewPeriodService } from '../../services/annual-review-period.service';
import { RoleService } from '../../services/role.service';
import { AuthService } from '../../services/auth.service';
import { ReviewPeriodSummaryComponent } from './review-period-summary.component';
import { FINAL_DEADLINES, ReviewSection, ReviewValidationIssue, SETUP_DEADLINES, validateReviewConfiguration } from './review-period.validation';

function roleForm(option: ReviewRoleConfiguration, selected: boolean) {
  return new FormGroup({ roleId: new FormControl(option.roleId, { nonNullable: true }),
    selected: new FormControl(selected, { nonNullable: true }), reviewFrequency: new FormControl(option.reviewFrequency, { nonNullable: true }) });
}
function levelForm(level: EmployeeLevel, saved?: EmployeeLevelConfiguration) {
  return new FormGroup({ employeeLevelId: new FormControl(level.id, { nonNullable: true }),
    companyKpiWeight: new FormControl<number | null>(saved?.companyKpiWeight ?? null),
    departmentKpiWeight: new FormControl<number | null>(saved?.departmentKpiWeight ?? null),
    individualKpiWeight: new FormControl<number | null>(saved?.individualKpiWeight ?? null) });
}

@Component({
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ReactiveFormsModule, TranslateModule, NzButtonModule,
    NzInputModule, NzInputNumberModule, NzDatePickerModule, NzSelectModule, NzCheckboxModule, NzRadioModule,
    NzModalModule, ReviewPeriodSummaryComponent],
  templateUrl: './review-period-form.component.html', styleUrl: './review-period.scss'
})
export class ReviewPeriodFormComponent implements OnInit {
  readonly sections: ReviewSection[] = ['basic', 'roles', 'levels', 'composition', 'consolidation', 'setup', 'assessment', 'attitude'];
  readonly setupDeadlines = SETUP_DEADLINES;
  readonly finalDeadlines = FINAL_DEADLINES;
  readonly frequencies = REVIEW_FREQUENCIES;
  readonly dates = Object.fromEntries(REVIEW_DATE_FIELDS.map(field => [field, new FormControl<Date | null>(null)])) as Record<typeof REVIEW_DATE_FIELDS[number], FormControl<Date | null>>;
  readonly roleRows = new FormArray<ReturnType<typeof roleForm>>([]);
  readonly levelRows = new FormArray<ReturnType<typeof levelForm>>([]);
  readonly form = new FormGroup({ ...this.dates, name: new FormControl<string | null>(null),
    kpiPerformanceWeight: new FormControl<number | null>(50), attitudeEvaluationWeight: new FormControl<number | null>(50),
    annualKpiConsolidationMethod: new FormControl<ConsolidationMethod | null>(null),
    selfAssessmentDaysAfterCheckpoint: new FormControl<number | null>(null),
    superiorAssessmentDaysAfterSelfDeadline: new FormControl<number | null>(null),
    roleConfigurations: this.roleRows, employeeLevelConfigurations: this.levelRows });
  period: AnnualReviewPeriod | null = null;
  previewPeriod: AnnualReviewPeriod | null = null;
  employeeLevels: EmployeeLevel[] = [];
  roleOptions: ReviewRoleConfiguration[] = [];
  issues: ReviewValidationIssue[] = [];
  loading = true;
  busy = false;
  loadingDefaults = false;
  loadingRoles = false;
  error = '';
  errorSection: ReviewSection | null = null;
  defaultsNotice = '';
  defaultsNeedRefresh = false;
  viewOnly = false;
  get limitedEdit(): boolean { return this.period?.status === 'UPCOMING'; }
  roleSearch = '';
  departmentFilter: string | null = null;
  private defaults$ = new Subject<string | null>();
  private publishingValidation = false;
  private destroyRef = inject(DestroyRef);
  total = weightTotal;
  editable = editableReviewPeriod;

  constructor(private api: AnnualReviewPeriodService, private roleService: RoleService, public auth: AuthService,
    private route: ActivatedRoute, private router: Router, private modal: NzModalService,
    private translate: TranslateService, private message: NzMessageService) {}

  ngOnInit(): void {
    this.route.paramMap.pipe(switchMap(params => {
      this.loading = true; this.error = ''; this.errorSection = null; this.period = null; this.previewPeriod = null;
      this.defaults$.next(null);
      const id = params.get('id');
      this.viewOnly = this.route.snapshot.data['viewOnly'] === true;
      if (id && (!/^\d+$/.test(id) || Number(id) <= 0 || !Number.isSafeInteger(Number(id)))) {
        this.error = this.translate.instant('REVIEW_PERIOD.LOAD_ERROR'); this.loading = false;
        return of(null);
      }
      return forkJoin({ period: id ? this.api.get(Number(id)) : of(null),
        roles: this.viewOnly ? of([] as ReviewRoleConfiguration[]) : this.api.roles(),
        levels: this.viewOnly ? of([] as EmployeeLevel[]) : this.roleService.getEmployeeLevels()
      }).pipe(catchError(error => { this.setError(error); return of(null); }), finalize(() => this.loading = false));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      if (!data) return;
      this.period = data.period;
      this.viewOnly = this.viewOnly || (!!data.period && !editableReviewPeriod(data.period));
      this.employeeLevels = data.levels;
      this.populate(data.period, data.roles);
    });
    this.defaults$.pipe(switchMap(date => {
      if (!date) { this.loadingDefaults = false; return of(null); }
      this.loadingDefaults = true;
      return this.api.creationDefaults(date).pipe(catchError(error => { this.setError(error); return of(null); }), finalize(() => this.loadingDefaults = false));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(defaults => { if (defaults) this.applyDefaults(defaults); });
    this.dates.startDate.valueChanges.pipe(distinctUntilChanged((a, b) => calendarDate(a) === calendarDate(b)),
      takeUntilDestroyed(this.destroyRef)).subscribe(date => {
      if (this.period || this.viewOnly || this.loading) return;
      this.defaults$.next(null);
      this.defaultsNotice = '';
      this.defaultsNeedRefresh = !!date;
      if (!date) return;
      if (this.levelRows.pristine && this.roleRows.controls.every(row => row.controls.reviewFrequency.pristine)) this.loadDefaults();
    });
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.previewPeriod = null;
      if (this.issues.length) this.validate(this.publishingValidation);
    });
  }

  private populate(period: AnnualReviewPeriod | null, options: ReviewRoleConfiguration[]): void {
    this.form.enable({ emitEvent: false });
    this.defaultsNotice = ''; this.defaultsNeedRefresh = false; this.issues = [];
    this.form.reset({ kpiPerformanceWeight: 50, attitudeEvaluationWeight: 50 }, { emitEvent: false });
    this.roleRows.clear({ emitEvent: false }); this.levelRows.clear({ emitEvent: false });
    this.roleOptions = options.map(option => {
      const saved = period?.roleConfigurations.find(c => c.roleId === option.roleId);
      return !saved ? option : period?.status === 'DRAFT' ? { ...option, reviewFrequency: saved.reviewFrequency } : saved;
    });
    for (const saved of period?.roleConfigurations ?? []) {
      if (!this.roleOptions.some(r => r.roleId === saved.roleId)) this.roleOptions.push(saved);
    }
    this.roleOptions.forEach(option => this.roleRows.push(roleForm(option, !!period?.roleConfigurations.some(c => c.roleId === option.roleId)), { emitEvent: false }));
    this.employeeLevels.forEach(level => this.levelRows.push(levelForm(level, period?.employeeLevelConfigurations.find(c => c.employeeLevelId === level.id)), { emitEvent: false }));
    if (period) {
      for (const field of REVIEW_DATE_FIELDS) this.dates[field].setValue(pickerDate(period[field]), { emitEvent: false });
      this.form.patchValue({ name: period.name, kpiPerformanceWeight: period.kpiPerformanceWeight,
        attitudeEvaluationWeight: period.attitudeEvaluationWeight, annualKpiConsolidationMethod: period.annualKpiConsolidationMethod,
        selfAssessmentDaysAfterCheckpoint: period.selfAssessmentDaysAfterCheckpoint,
        superiorAssessmentDaysAfterSelfDeadline: period.superiorAssessmentDaysAfterSelfDeadline }, { emitEvent: false });
    }
    this.form.markAsPristine();
    if (this.limitedEdit) {
      this.roleRows.disable({ emitEvent: false }); this.levelRows.disable({ emitEvent: false });
      this.form.controls.kpiPerformanceWeight.disable({ emitEvent: false });
      this.form.controls.attitudeEvaluationWeight.disable({ emitEvent: false });
      this.form.controls.annualKpiConsolidationMethod.disable({ emitEvent: false });
    }
  }

  get departments(): string[] { return [...new Set(this.roleOptions.map(r => r.departmentName).filter((name): name is string => !!name))].sort(); }
  roleVisible(index: number): boolean {
    const role = this.roleOptions[index];
    return (!this.departmentFilter || role.departmentName === this.departmentFilter) && role.roleName.toLowerCase().includes(this.roleSearch.trim().toLowerCase());
  }
  get selectedRoleCount(): number { return this.roleRows.controls.filter(r => r.controls.selected.value).length; }
  selectAll(selected: boolean): void { if (this.limitedEdit) return; this.roleRows.controls.forEach((row, index) => { if (this.roleVisible(index)) row.controls.selected.setValue(selected); }); this.roleRows.markAsDirty(); }

  refreshRoles(): void {
    if (this.busy || this.loadingRoles || this.viewOnly || this.limitedEdit) return;
    this.loadingRoles = true;
    this.api.roles().pipe(finalize(() => this.loadingRoles = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: options => {
        this.roleOptions = this.roleOptions.map(existing => {
          const frozen = this.period?.status === 'UPCOMING' && this.period.roleConfigurations.find(role => role.roleId === existing.roleId);
          return frozen || options.find(role => role.roleId === existing.roleId) || existing;
        });
        for (const option of options) {
          if (!this.roleOptions.some(role => role.roleId === option.roleId)) {
            this.roleOptions.push(option);
            this.roleRows.push(roleForm(option, false), { emitEvent: false });
          }
        }
        this.previewPeriod = null;
        if (this.issues.length) this.validate(this.publishingValidation);
      }, error: error => this.setError(error)
    });
  }

  loadDefaults(): void {
    const date = calendarDate(this.dates.startDate.value);
    if (!date || this.period || this.viewOnly || this.busy) return;
    const edited = this.levelRows.dirty || this.roleRows.controls.some(row => row.controls.reviewFrequency.dirty);
    if (edited) {
      this.modal.confirm({ nzTitle: this.translate.instant('REVIEW_PERIOD.RELOAD_DEFAULTS_TITLE'),
        nzContent: this.translate.instant('REVIEW_PERIOD.RELOAD_DEFAULTS_DETAIL'),
        nzOkText: this.translate.instant('REVIEW_PERIOD.LOAD_DEFAULTS'), nzCancelText: this.translate.instant('REVIEW_PERIOD.CANCEL'),
        nzOnOk: () => this.defaults$.next(calendarDate(this.dates.startDate.value)) });
    } else this.defaults$.next(date);
  }

  private applyDefaults(defaults: AnnualReviewCreationDefaults): void {
    this.levelRows.controls.forEach(row => {
      const saved = defaults.employeeLevelConfigurations.find(c => c.employeeLevelId === row.controls.employeeLevelId.value);
      if (saved) row.patchValue({ companyKpiWeight: saved.companyKpiWeight, departmentKpiWeight: saved.departmentKpiWeight,
        individualKpiWeight: saved.individualKpiWeight }, { emitEvent: false });
    });
    this.roleRows.controls.forEach(row => {
      const saved = defaults.roleConfigurations.find(c => c.roleId === row.controls.roleId.value);
      if (saved) row.controls.reviewFrequency.setValue(saved.reviewFrequency, { emitEvent: false });
    });
    this.previewPeriod = null; this.defaultsNeedRefresh = false;
    this.defaultsNotice = defaults.sourceReviewPeriodName
      ? this.translate.instant('REVIEW_PERIOD.COPIED_DEFAULTS_FROM', { period: defaults.sourceReviewPeriodName })
      : this.translate.instant(defaults.sourceReviewPeriodId ? 'REVIEW_PERIOD.COPIED_DEFAULTS' : 'REVIEW_PERIOD.INITIAL_DEFAULTS');
    if (this.issues.length) this.validate(this.publishingValidation);
  }

  request(): AnnualReviewPeriodRequest {
    const value = this.form.getRawValue();
    const dates = Object.fromEntries(REVIEW_DATE_FIELDS.map(field => [field, calendarDate(value[field])])) as Record<typeof REVIEW_DATE_FIELDS[number], string | null>;
    const weights = this.levelRows.getRawValue();
    return { ...dates, name: value.name?.trim() || null,
      kpiPerformanceWeight: value.kpiPerformanceWeight, attitudeEvaluationWeight: value.attitudeEvaluationWeight,
      annualKpiConsolidationMethod: value.annualKpiConsolidationMethod,
      selfAssessmentDaysAfterCheckpoint: value.selfAssessmentDaysAfterCheckpoint,
      superiorAssessmentDaysAfterSelfDeadline: value.superiorAssessmentDaysAfterSelfDeadline,
      employeeLevelConfigurations: weights.every(w => w.companyKpiWeight === null && w.departmentKpiWeight === null && w.individualKpiWeight === null) && this.levelRows.pristine && !this.period ? null : weights,
      roleConfigurations: this.roleRows.getRawValue().filter(r => r.selected).map(r => ({ roleId: r.roleId, reviewFrequency: r.reviewFrequency })) };
  }

  validate(publish: boolean): boolean {
    this.publishingValidation = publish;
    this.issues = validateReviewConfiguration(this.request(), publish, this.employeeLevels.map(l => l.id), this.roleOptions);
    return !this.issues.length;
  }
  sectionIssues(section: ReviewSection): ReviewValidationIssue[] { return this.issues.filter(issue => issue.section === section); }
  fieldIssue(field: string): ReviewValidationIssue | undefined { return this.issues.find(issue => issue.field === field); }
  focusSection(section: ReviewSection): void {
    const card = document.getElementById(`review-${section}`);
    card?.scrollIntoView({ behavior: 'smooth', block: 'start' }); card?.focus({ preventScroll: true });
  }
  private check(publish: boolean): boolean {
    if (this.busy || this.loadingDefaults || this.loadingRoles || this.viewOnly) return false;
    this.error = '';
    this.errorSection = null;
    if (this.validate(publish)) return true;
    this.focusSection(this.issues[0].section); return false;
  }

  save(): void {
    if (!this.check(this.period?.status === 'UPCOMING')) return;
    this.busy = true;
    const id = this.period?.id;
    const operation = id ? this.api.update(id, this.request()) : this.api.create(this.request());
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: period => {
        this.period = period; this.viewOnly = !editableReviewPeriod(period); this.populate(period, this.roleOptions);
        this.message.success(this.translate.instant('REVIEW_PERIOD.SAVED'));
        if (!id) this.router.navigate(['/kpi-administration/review-periods', period.id, 'edit']);
      }, error: error => this.setError(error)
    });
  }

  preview(): void {
    if (!this.check(true)) return;
    this.busy = true;
    this.api.preview(this.request(), this.period?.id ?? null).pipe(finalize(() => this.busy = false),
      takeUntilDestroyed(this.destroyRef)).subscribe({ next: period => this.previewPeriod = period, error: error => this.setError(error) });
  }

  publish(): void {
    if (!this.previewPeriod || !this.check(true) || (this.period && this.period.status !== 'DRAFT')) return;
    this.busy = true;
    // Persist the current Draft edits before publishing that saved record.
    const operation = this.period?.id ? this.api.update(this.period.id, this.request()).pipe(
      switchMap(saved => { this.period = saved; return this.api.publish(saved.id!); })
    ) : this.api.create(this.request(), true);
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: period => {
        this.previewPeriod = null; this.message.success(this.translate.instant('REVIEW_PERIOD.PUBLISHED'));
        this.router.navigate(['/kpi-administration/review-periods', period.id]);
      }, error: error => { this.previewPeriod = null; this.setError(error); }
    });
  }

  private setError(error: { error?: { message?: string } }): void {
    this.error = error.error?.message || this.translate.instant('REVIEW_PERIOD.SAVE_ERROR');
    this.errorSection = /overlap|name already exists/i.test(this.error) ? 'basic'
      : /applicable role/i.test(this.error) ? 'roles' : null;
  }
}
