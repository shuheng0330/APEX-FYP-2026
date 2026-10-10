import { CommonModule, DOCUMENT } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzIconModule, NzIconService } from 'ng-zorro-antd/icon';
import { CheckCircleOutline, WarningOutline } from '@ant-design/icons-angular/icons';
import { catchError, finalize, forkJoin, of, Subject, switchMap, tap } from 'rxjs';
import { AttitudeConfiguration, AttitudeConfigurationOptions, AttitudeConfigurationRequest, AttitudeCriterion,
  AttitudeFormat, AttitudePeriodConfiguration, AttitudeRating, AttitudeRoleOption, attitudePublicationIssues, emptyAttitudeConfiguration } from '../../models/attitude-configuration.model';
import { AttitudeConfigurationService } from '../../services/attitude-configuration.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzInputModule, NzSelectModule, NzDrawerModule, NzModalModule, NzIconModule],
  templateUrl: './attitude-configuration.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', './attitude-configuration.component.scss']
})
export class AttitudeConfigurationComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  editions: AttitudeConfiguration[] = [];
  options: AttitudeConfigurationOptions = { formats: [], roles: [], reviewPeriods: [] };
  selected: AttitudeConfiguration | null = null;
  selectionId: number | null = null;
  form = emptyAttitudeConfiguration();
  private saved = JSON.stringify(this.form);
  busy = false;
  loaded = false;
  error = '';
  success = '';
  showValidation = false;
  selectedFormat: AttitudeFormat = 'MANAGER';
  roleSearch = '';
  previewVisible = false;
  editorMode: 'criterion' | 'rating' | null = null;
  editorReadonly = false;
  criterionIndex: number | null = null;
  criterion: AttitudeCriterion | null = null;
  rating: AttitudeRating | null = null;
  editorError = '';
  periodId: number | null = null;
  periodContext: AttitudePeriodConfiguration | null = null;
  bindingConfigurationId: number | null = null;
  bindingBusy = false;
  bindingError = '';
  bindingSuccess = '';
  private readonly periodSelection = new Subject<number | null>();

  constructor(private api: AttitudeConfigurationService, private translate: TranslateService, private modal: NzModalService) {
    inject(NzIconService).addIcon(WarningOutline, CheckCircleOutline);
    this.periodSelection.pipe(switchMap(id => {
      this.periodContext = null; this.bindingError = ''; this.bindingSuccess = ''; this.bindingBusy = id !== null;
      return id === null ? of(null) : this.api.period(id).pipe(
        catchError(e => { this.bindingError = this.errorMessage(e); return of(null); }),
        finalize(() => this.bindingBusy = false));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(context => this.periodContext = context);
  }

  ngOnInit() {
    this.busy = true;
    forkJoin({ editions: this.api.list(), options: this.api.optionsList() })
      .pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => {
          this.editions = result.editions; this.options = result.options;
          this.selectedFormat = this.options.formats[0] ?? 'MANAGER';
          this.accept(result.editions[0] ?? null); this.loaded = true;
          this.bindingConfigurationId = this.publishedEditions[0]?.id ?? null;
          this.loadPeriod(this.options.reviewPeriods[0]?.id ?? null);
        }, error: e => this.fail(e)
      });
  }
  get readonly() { return this.selected?.status === 'PUBLISHED'; }
  get dirty() { return !this.readonly && JSON.stringify(this.payload()) !== this.saved; }
  get issues() { return attitudePublicationIssues(this.form, this.options); }
  get roleOptions(): AttitudeRoleOption[] {
    const roles = [...this.options.roles];
    for (const mapping of this.form.roleMappings) {
      if (!roles.some(r => r.roleId === mapping.roleId)) {
        roles.push({ roleId: mapping.roleId, roleName: mapping.roleName ?? String(mapping.roleId), departmentName: mapping.departmentName, evaluationFormat: mapping.evaluationFormat });
      }
    }
    return roles.filter(r => `${r.roleName} ${r.departmentName ?? ''}`.toLowerCase().includes(this.roleSearch.trim().toLowerCase()));
  }
  get unmappedCount() { return this.options.roles.filter(r => !this.formatForRole(r.roleId)).length; }
  criteriaFor(format: AttitudeFormat | null) {
    return this.form.criteria.filter(c => format === null ? c.criterionType === 'SHARED_CORE_VALUE' :
      c.criterionType === 'FORMAT_SPECIFIC' && c.evaluationFormat === format);
  }
  applicableCriteria(format: AttitudeFormat) {
    return this.form.criteria.filter(c => c.active && (c.criterionType === 'SHARED_CORE_VALUE' || c.evaluationFormat === format));
  }
  formatForRole(roleId: number) { return this.form.roleMappings.find(m => m.roleId === roleId)?.evaluationFormat ?? null; }
  setRoleFormat(role: AttitudeRoleOption, evaluationFormat: AttitudeFormat | null) {
    if (this.readonly || this.busy) return;
    this.form.roleMappings = this.form.roleMappings.filter(m => m.roleId !== role.roleId);
    if (evaluationFormat) this.form.roleMappings.push({ roleId: role.roleId, roleName: role.roleName, departmentName: role.departmentName, evaluationFormat });
  }
  isEligible(roleId: number) { return this.options.roles.some(r => r.roleId === roleId); }
  async selectEdition(id: number) {
    if (this.busy || this.editorMode || id === this.selected?.id) { this.selectionId = this.selected?.id ?? null; return; }
    if (!await this.canLeave()) { this.selectionId = this.selected?.id ?? null; return; }
    this.accept(this.editions.find(c => c.id === id) ?? null); this.error = ''; this.success = '';
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.busy || this.bindingBusy) return false;
    if (!this.loaded) return true;
    if (!this.dirty) return true;
    return new Promise(resolve => this.modal.confirm({
      nzTitle: this.translate.instant('ATTITUDE_SETUP.UNSAVED_TITLE'),
      nzContent: this.translate.instant('ATTITUDE_SETUP.UNSAVED_HELP'),
      nzOnOk: () => { resolve(true); }, nzOnCancel: () => { resolve(false); }
    }));
  }
  private accept(edition: AttitudeConfiguration | null) {
    this.selected = edition;
    this.selectionId = edition?.id ?? null;
    this.form = edition ? {
      name: edition.name, criteria: structuredClone(edition.criteria), roleMappings: structuredClone(edition.roleMappings),
      ratingDefinitions: [5, 4, 3, 2, 1].map(point => structuredClone(edition.ratingDefinitions.find(r => r.point === point) ?? { point, label: '', description: '' }))
    } : emptyAttitudeConfiguration();
    this.saved = JSON.stringify(this.payload()); this.showValidation = false;
  }
  private payload(): AttitudeConfigurationRequest {
    return {
      name: this.form.name,
      criteria: this.form.criteria.map(c => ({ id: c.id, name: c.name, description: c.description,
        criterionType: c.criterionType, evaluationFormat: c.evaluationFormat, active: c.active })),
      ratingDefinitions: this.form.ratingDefinitions.map(r => ({ point: r.point, label: r.label, description: r.description })),
      roleMappings: this.form.roleMappings.map(m => ({ roleId: m.roleId, evaluationFormat: m.evaluationFormat }))
    };
  }
  private remember(edition: AttitudeConfiguration) {
    this.editions = [edition, ...this.editions.filter(c => c.id !== edition.id)];
    this.accept(edition);
    if (!this.publishedEditions.some(c => c.id === this.bindingConfigurationId)) this.bindingConfigurationId = this.publishedEditions[0]?.id ?? null;
  }
  saveDraft(publish = false) {
    if (this.readonly || this.busy || !this.loaded || this.editorMode) return;
    if (publish && this.issues.length) { this.showValidation = true; this.focusSection(this.issues[0].section); return; }
    const request = this.payload();
    this.busy = true; this.error = ''; this.success = '';
    const save = this.selected ? this.api.update(this.selected.id, request) : this.api.create(request);
    save.pipe(tap(edition => this.remember(edition)),
      switchMap(edition => publish ? this.api.publish(edition.id) : of(edition)),
      finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: edition => { this.remember(edition); this.success = this.translate.instant(`ATTITUDE_SETUP.${publish ? 'PUBLISHED_SUCCESS' : 'SAVED'}`); },
        error: e => this.fail(e)
      });
  }
  confirmPublish() {
    if (this.readonly || this.busy || !this.loaded || this.editorMode) return;
    this.showValidation = true;
    if (this.issues.length) { this.focusSection(this.issues[0].section); return; }
    this.modal.confirm({ nzTitle: this.translate.instant('ATTITUDE_SETUP.PUBLISH'),
      nzContent: this.translate.instant('ATTITUDE_SETUP.PUBLISH_CONFIRM'), nzOnOk: () => this.saveDraft(true) });
  }
  copyPublished() {
    if (!this.readonly || !this.selected || this.busy) return;
    this.busy = true; this.error = ''; this.success = '';
    this.api.copy(this.selected.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: edition => { this.remember(edition); this.success = this.translate.instant('ATTITUDE_SETUP.COPIED'); }, error: e => this.fail(e)
    });
  }
  openCriterion(criterion: AttitudeCriterion | null, format: AttitudeFormat | null, view = false) {
    if (this.busy || (!criterion && this.readonly)) return;
    this.criterionIndex = criterion ? this.form.criteria.indexOf(criterion) : null;
    this.criterion = criterion ? structuredClone(criterion) : { name: '', description: '', active: true,
      criterionType: format === null ? 'SHARED_CORE_VALUE' : 'FORMAT_SPECIFIC', evaluationFormat: format };
    this.rating = null; this.editorMode = 'criterion'; this.editorReadonly = this.readonly || view; this.editorError = '';
  }
  openRating(rating: AttitudeRating) {
    if (this.busy) return;
    this.rating = structuredClone(rating); this.criterion = null; this.editorMode = 'rating';
    this.editorReadonly = this.readonly; this.editorError = '';
  }
  applyEditor() {
    if (this.editorReadonly || this.readonly || this.busy) return;
    const value = this.criterion ?? this.rating;
    if (!value) return;
    const name = this.criterion?.name ?? this.rating?.label;
    if ((name?.length ?? 0) > 255 || (value.description?.length ?? 0) > 10000) { this.editorError = 'TEXT_LIMIT'; return; }
    if (this.criterion) {
      const c = this.criterion;
      if (c.name?.trim() && this.form.criteria.some((other, index) => index !== this.criterionIndex && other.criterionType === c.criterionType &&
        other.evaluationFormat === c.evaluationFormat && other.name?.trim().toLowerCase() === c.name?.trim().toLowerCase())) {
        this.editorError = 'DUPLICATE_CRITERION'; return;
      }
      if (this.criterionIndex === null) this.form.criteria.push(structuredClone(c));
      else this.form.criteria[this.criterionIndex] = structuredClone(c);
    } else if (this.rating) {
      this.form.ratingDefinitions = this.form.ratingDefinitions.map(r => r.point === this.rating!.point ? structuredClone(this.rating!) : r);
    }
    this.editorMode = null;
  }
  toggleActive(criterion: AttitudeCriterion) { if (!this.readonly && !this.busy) criterion.active = !criterion.active; }
  moveCriterion(criterion: AttitudeCriterion, direction: -1 | 1) {
    if (this.readonly || this.busy) return;
    const group = this.criteriaFor(criterion.evaluationFormat);
    const other = group[group.indexOf(criterion) + direction];
    if (!other) return;
    const a = this.form.criteria.indexOf(criterion), b = this.form.criteria.indexOf(other);
    [this.form.criteria[a], this.form.criteria[b]] = [this.form.criteria[b], this.form.criteria[a]];
  }
  invalidCriterion(criterion: AttitudeCriterion) { return this.showValidation && criterion.active && (!criterion.name?.trim() || !criterion.description?.trim()); }
  focusSection(section: string) {
    const element = this.document.getElementById(`attitude-${section}`);
    element?.scrollIntoView({ behavior: 'smooth', block: 'start' }); element?.focus({ preventScroll: true });
  }
  get publishedEditions() {
    return this.editions.filter(c => c.status === 'PUBLISHED').sort((a, b) =>
      Date.parse(b.publishedAt ?? b.createdAt) - Date.parse(a.publishedAt ?? a.createdAt) || b.id - a.id);
  }
  get canBind() {
    return !this.busy && !this.bindingBusy && this.periodContext?.reviewPeriodId === this.periodId &&
      this.periodContext?.reviewPeriodStatus === 'OPEN' && this.periodContext.canBindInitially && !this.periodContext.configuration &&
      this.publishedEditions.some(c => c.id === this.bindingConfigurationId);
  }
  loadPeriod(id: number | null) { this.periodId = id; this.periodSelection.next(id); }
  confirmBinding() {
    if (!this.canBind) return;
    const periodId = this.periodId, configurationId = this.bindingConfigurationId;
    this.modal.confirm({ nzTitle: this.translate.instant('ATTITUDE_SETUP.BIND'),
      nzContent: this.translate.instant('ATTITUDE_SETUP.BIND_CONFIRM'),
      nzOnOk: () => { if (this.periodId === periodId && this.bindingConfigurationId === configurationId) this.bindPeriod(); } });
  }
  private bindPeriod() {
    if (!this.canBind || this.periodId === null || this.bindingConfigurationId === null) return;
    this.bindingBusy = true; this.bindingError = ''; this.bindingSuccess = '';
    this.api.bindInitially(this.periodId, this.bindingConfigurationId)
      .pipe(finalize(() => this.bindingBusy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: context => { this.periodContext = context; this.bindingSuccess = this.translate.instant('ATTITUDE_SETUP.BOUND_SUCCESS'); },
        error: e => this.bindingError = this.errorMessage(e)
      });
  }
  private errorMessage(error: { error?: { message?: string } }) { return error.error?.message || this.translate.instant('ATTITUDE_SETUP.ERROR'); }
  private fail(error: { error?: { message?: string } }) {
    this.error = this.errorMessage(error);
  }
}
