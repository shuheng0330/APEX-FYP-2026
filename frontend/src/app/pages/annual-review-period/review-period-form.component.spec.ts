import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CalendarOutline, CloseCircleFill, DownOutline, LeftOutline, RightOutline, DoubleLeftOutline, DoubleRightOutline, SearchOutline, CheckOutline, CloseOutline, LoadingOutline, ExclamationCircleFill } from '@ant-design/icons-angular/icons';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalService } from 'ng-zorro-antd/modal';
import { en_US, provideNzI18n } from 'ng-zorro-antd/i18n';
import { AnnualReviewPeriodService } from '../../services/annual-review-period.service';
import { RoleService } from '../../services/role.service';
import { AuthService } from '../../services/auth.service';
import { AnnualReviewCreationDefaults, REVIEW_DATE_FIELDS, pickerDate } from '../../models/annual-review-period.model';
import { reviewLevels, savedReview, validReviewRequest } from './review-period.fixtures.spec';
import { ReviewPeriodFormComponent } from './review-period-form.component';

describe('Annual review period form', () => {
  let fixture: ComponentFixture<ReviewPeriodFormComponent>;
  let component: ReviewPeriodFormComponent;
  let api: jasmine.SpyObj<AnnualReviewPeriodService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let snapshot: { data: { viewOnly?: boolean } };
  let router: Router;
  const defaults = (): AnnualReviewCreationDefaults => ({ sourceReviewPeriodId: 1, employeeLevelConfigurations: savedReview().employeeLevelConfigurations, roleConfigurations: savedReview().roleConfigurations });

  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({})); snapshot = { data: {} };
    api = jasmine.createSpyObj<AnnualReviewPeriodService>('api', ['roles', 'creationDefaults', 'get', 'create', 'update', 'preview', 'publish']);
    api.roles.and.returnValue(of(savedReview().roleConfigurations)); api.creationDefaults.and.returnValue(of(defaults()));
    api.get.and.returnValue(of({ ...savedReview(), status: 'DRAFT' }));
    api.create.and.returnValue(of({ ...savedReview(), status: 'DRAFT' }));
    api.update.and.returnValue(of({ ...savedReview(), status: 'DRAFT' }));
    api.publish.and.returnValue(of(savedReview())); api.preview.and.returnValue(of(savedReview()));
    TestBed.configureTestingModule({ imports: [ReviewPeriodFormComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), provideRouter([]), provideNzI18n(en_US),
      { provide: NZ_ICONS, useValue: [CalendarOutline, CloseCircleFill, DownOutline, LeftOutline, RightOutline, DoubleLeftOutline, DoubleRightOutline, SearchOutline, CheckOutline, CloseOutline, LoadingOutline, ExclamationCircleFill] },
      { provide: AnnualReviewPeriodService, useValue: api },
      { provide: RoleService, useValue: { getEmployeeLevels: () => of(reviewLevels) } },
      { provide: AuthService, useValue: { hasRole: () => true } },
      { provide: ActivatedRoute, useValue: { paramMap: params, snapshot } },
      { provide: NzMessageService, useValue: { success: jasmine.createSpy('success') } },
      { provide: NzModalService, useValue: { confirm: jasmine.createSpy('confirm') } }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: { confirm: jasmine.createSpy('confirm') } });
    await TestBed.compileComponents();
    fixture = TestBed.createComponent(ReviewPeriodFormComponent); component = fixture.componentInstance;
    router = TestBed.inject(Router); spyOn(router, 'navigate').and.resolveTo(true);
  });

  function completeForm(): void {
    const request = validReviewRequest();
    component.form.patchValue({ name: request.name, kpiPerformanceWeight: 50, attitudeEvaluationWeight: 50,
      annualKpiConsolidationMethod: 'FINAL_CHECKPOINT', selfAssessmentDaysAfterCheckpoint: 5, superiorAssessmentDaysAfterSelfDeadline: 5 });
    REVIEW_DATE_FIELDS.forEach(field => component.dates[field].setValue(pickerDate(request[field])));
    component.roleRows.at(0).controls.selected.setValue(true);
    if (component.departmentBased) component.changeDepartmentFrequency(10, 'MONTHLY');
  }

  it('starts new periods with Department schedules without inheriting Annual Role frequency', () => {
    api.roles.and.returnValue(of([{ ...savedReview().roleConfigurations[0], reviewFrequency: 'ANNUALLY' }]));
    fixture.detectChanges(); component.roleRows.at(0).controls.selected.setValue(true); fixture.detectChanges();
    expect(component.request().frequencyPolicy).toBe('DEPARTMENT_BASED');
    expect(component.request().departmentConfigurations).toEqual([]);
    expect(component.request().roleConfigurations[0].reviewFrequency).toBeNull();
    expect(fixture.nativeElement.querySelector('#review-roles nz-select[formControlName="reviewFrequency"]')).toBeNull();
    component.changeDepartmentFrequency(10, 'QUARTERLY');
    expect(component.request().departmentConfigurations).toEqual([{ departmentId: 10, departmentName: 'Sales', reviewFrequency: 'QUARTERLY' }]);
    expect(component.form.dirty).toBeTrue();
  });
  it('keeps saved Average and an explicit Average choice when defaults load', () => {
    fixture.detectChanges();
    component.form.controls.annualKpiConsolidationMethod.setValue('AVERAGE');
    component.form.controls.annualKpiConsolidationMethod.markAsDirty();
    component.dates.startDate.setValue(new Date(2028, 0, 1));
    expect(component.request().annualKpiConsolidationMethod).toBe('AVERAGE');
    api.get.and.returnValue(of({ ...savedReview(), status: 'DRAFT', annualKpiConsolidationMethod: 'AVERAGE' }));
    params.next(convertToParamMap({ id: '29' }));
    expect(component.request().annualKpiConsolidationMethod).toBe('AVERAGE');
  });
  it('preserves a copied consolidation choice when the Start Date loads another default', () => {
    api.creationDefaults.and.returnValues(of({ ...defaults(), annualKpiConsolidationMethod: 'AVERAGE' }),
      of({ ...defaults(), annualKpiConsolidationMethod: 'FINAL_CHECKPOINT' }));
    fixture.detectChanges(); component.dates.startDate.setValue(new Date(2028, 0, 1));
    component.dates.startDate.setValue(new Date(2029, 0, 1));
    expect(component.request().annualKpiConsolidationMethod).toBe('AVERAGE');
  });
  it('copies Department defaults and consolidation without replacing a chosen schedule', () => {
    api.creationDefaults.and.returnValue(of({ ...defaults(), annualKpiConsolidationMethod: 'AVERAGE',
      departmentConfigurations: [{ departmentId: 10, reviewFrequency: 'MONTHLY' }] }));
    fixture.detectChanges(); component.roleRows.at(0).controls.selected.setValue(true);
    component.changeDepartmentFrequency(10, 'QUARTERLY'); component.dates.startDate.setValue(new Date(2028, 0, 1));
    expect(component.request().departmentConfigurations?.[0].reviewFrequency).toBe('QUARTERLY');
    expect(component.request().annualKpiConsolidationMethod).toBe('AVERAGE');
  });
  it('keeps a legacy Draft unchanged until explicit conversion confirmation', () => {
    params.next(convertToParamMap({ id: '29' })); fixture.detectChanges();
    expect(component.departmentBased).toBeFalse(); component.adoptDepartmentSchedule();
    expect(component.departmentBased).toBeFalse();
    const confirm = TestBed.inject(NzModalService).confirm as jasmine.Spy;
    confirm.calls.mostRecent().args[0].nzOnOk();
    expect(component.departmentBased).toBeTrue(); expect(component.request().departmentConfigurations).toEqual([]);
    expect(component.form.dirty).toBeTrue();
  });
  it('shows every missing Role level despite filters and preserves input after refreshing', () => {
    api.roles.and.returnValue(of([7, 8].map(roleId => ({ ...savedReview().roleConfigurations[0], roleId, employeeLevelId: null }))));
    fixture.detectChanges(); component.selectAll(true); component.roleSearch = 'does not match';
    component.form.controls.name.setValue('Unsaved name'); component.changeDepartmentFrequency(10, 'QUARTERLY'); fixture.detectChanges();
    const links: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('a[target="_blank"]'));
    expect(links.length).toBe(2); expect(links[0].href).toContain('editRoleId=7'); expect(links[1].href).toContain('editRoleId=8');
    api.roles.and.returnValue(of([7, 8].map(roleId => ({ ...savedReview().roleConfigurations[0], roleId }))));
    component.refreshRoles();
    expect(component.missingLevels).toEqual([]); expect(component.form.controls.name.value).toBe('Unsaved name');
    expect(component.request().departmentConfigurations?.[0].reviewFrequency).toBe('QUARTERLY');
  });
  it('shows administrator guidance without edit links when Role management is unavailable', () => {
    spyOn(TestBed.inject(AuthService), 'hasRole').and.returnValue(false);
    api.roles.and.returnValue(of([{ ...savedReview().roleConfigurations[0], employeeLevelId: null }]));
    fixture.detectChanges(); component.selectAll(true); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a[target="_blank"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('REVIEW_PERIOD.DEPARTMENT_SCHEDULE.CONTACT_ADMIN');
  });
  it('freezes new-policy Upcoming schedules while retaining permitted name edits', () => {
    params.next(convertToParamMap({ id: '29' }));
    api.get.and.returnValue(of({ ...savedReview(), frequencyPolicy: 'DEPARTMENT_BASED',
      departmentConfigurations: [{ departmentId: 10, departmentName: 'Recorded Sales', reviewFrequency: 'QUARTERLY' }] }));
    fixture.detectChanges();
    expect(component.dates.endDate.disabled).toBeTrue();
    expect(component.form.controls.selfAssessmentDaysAfterCheckpoint.disabled).toBeTrue();
    component.changeDepartmentFrequency(10, 'MONTHLY');
    expect(component.request().departmentConfigurations?.[0].reviewFrequency).toBe('QUARTERLY');
    expect(component.form.controls.name.enabled).toBeTrue();
    component.save(); expect(api.update).toHaveBeenCalled();
  });

  it('renders six level rows and saves an incomplete dateless Draft without obsolete weights', () => {
    fixture.detectChanges(); expect(component.levelRows.length).toBe(6);
    component.save();
    expect(api.create).toHaveBeenCalledWith(jasmine.objectContaining({ name: null, startDate: null, employeeLevelConfigurations: null }));
    expect(api.create.calls.mostRecent().args[0]).not.toEqual(jasmine.objectContaining({ companyKpiWeight: jasmine.anything() }));
  });
  it('shows nonblocking required-field readiness after saving an incomplete Draft, including after reload', () => {
    const incomplete = { ...savedReview(), status: 'DRAFT' as const, name: null, startDate: null, endDate: null, roleConfigurations: [] };
    api.create.and.returnValue(of(incomplete)); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.draft-readiness')).toBeNull();
    component.save(); fixture.detectChanges();
    expect(api.create).toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.draft-readiness')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#period-name').getAttribute('aria-invalid')).toBe('true');
    expect(component.fieldIssue('roles')?.key).toBe('REVIEW_PERIOD.ERROR.ROLES');
    api.get.and.returnValue(of(incomplete)); params.next(convertToParamMap({ id: '29' })); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.draft-readiness')).not.toBeNull();
  });
  it('highlights supplied invalid weights on Draft save without calling the API', () => {
    fixture.detectChanges(); component.levelRows.at(0).controls.companyKpiWeight.setValue(100.001);
    component.save(); fixture.detectChanges();
    expect(api.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#review-levels nz-input-number[aria-invalid="true"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#review-levels .field-error')).not.toBeNull();
  });
  it('loads both defaults on the first Start Date and keeps copied weights independent', () => {
    const values = defaults(); api.creationDefaults.and.returnValue(of(values)); fixture.detectChanges();
    component.dates.startDate.setValue(new Date(2028, 0, 1));
    expect(api.creationDefaults).toHaveBeenCalledWith('2028-01-01');
    expect(component.levelRows.at(3).controls.companyKpiWeight.value).toBe(15);
    component.levelRows.at(3).controls.companyKpiWeight.setValue(20);
    expect(values.employeeLevelConfigurations[3].companyKpiWeight).toBe(15);
  });
  it('does not save implicitly when Enter submits the form', () => {
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    expect(api.create).not.toHaveBeenCalled();
    expect(api.update).not.toHaveBeenCalled();
  });
  it('shows only the single setup deadline and sends only that active deadline field', () => {
    fixture.detectChanges(); completeForm(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('#review-setup nz-date-picker').length).toBe(1);
    expect(component.request().kpiSetupDeadline).toBe('2027-12-15');
    expect(Object.keys(component.request())).not.toContain('individualKpiApprovalDeadline');
    expect(Object.keys(component.request())).not.toContain('companyKpiCreationDeadline');
  });
  it('places the required opening date before the employee deadline without prefilling it', () => {
    fixture.detectChanges(); component.dates.startDate.setValue(new Date(2028, 0, 1)); fixture.detectChanges();
    expect(component.dates.attitudeSelfAssessmentStartDate.value).toBeNull();
    const labels: HTMLLabelElement[] = Array.from(fixture.nativeElement.querySelectorAll('#review-attitude label'));
    expect(labels[0].htmlFor).toBe('attitudeSelfAssessmentStartDate');
    expect(labels[1].htmlFor).toBe('attitudeSelfAssessmentDeadline');
    expect(labels[0].querySelector('.required-mark')).not.toBeNull();
    expect(labels[0].parentElement?.textContent).toContain('REVIEW_PERIOD.ATTITUDE_OPENING_HELP');
    expect(component.fieldIssue('attitudeSelfAssessmentStartDate')).toBeUndefined();
  });
  it('groups Attitude and Appraisal dates without changing values or preview when the guide is opened', () => {
    fixture.detectChanges(); completeForm(); component.form.markAsDirty();
    const request = structuredClone(component.request());
    fixture.detectChanges();
    const preview = savedReview(); component.previewPeriod = preview;
    const groups: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('#review-attitude .schedule-subsection'));
    expect(groups.length).toBe(2);
    expect(groups[0].querySelectorAll('nz-date-picker').length).toBe(3);
    expect(groups[1].querySelectorAll('nz-date-picker').length).toBe(2);
    expect(groups[0].querySelector('h4')?.textContent).toContain('REVIEW_PERIOD.ATTITUDE_SCHEDULE');
    expect(groups[1].querySelector('h4')?.textContent).toContain('REVIEW_PERIOD.APPRAISAL_SCHEDULE');
    const guide: HTMLDetailsElement = fixture.nativeElement.querySelector('#review-assessment details');
    expect(guide.open).toBeFalse(); guide.querySelector('summary')!.click();
    expect(guide.open).toBeTrue(); expect(component.request()).toEqual(request);
    expect(component.form.dirty).toBeTrue(); expect(component.previewPeriod).toBe(preview);
    expect(api.create).not.toHaveBeenCalled(); expect(api.update).not.toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled();
  });
  it('shows field help and keeps Appraisal validation beside the correct input', () => {
    fixture.detectChanges(); completeForm();
    component.dates.hrFinalisationDeadline.setValue(pickerDate('2029-01-01')); component.validate(true); fixture.detectChanges();
    const field: HTMLElement = fixture.nativeElement.querySelector('#appraisal-schedule-heading + .help + .grid label[for="hrFinalisationDeadline"]').parentElement;
    expect(field.querySelector('.field-error')?.textContent).toContain('REVIEW_PERIOD.ERROR.DEADLINE_ORDER');
    expect(field.querySelector('nz-date-picker')?.getAttribute('aria-describedby')).toBe('hrFinalisationDeadline-help');
    expect(field.querySelector('#hrFinalisationDeadline-help')?.textContent).toContain('REVIEW_PERIOD.SCHEDULE_FIELD_HELP.hrFinalisationDeadline');
  });
  it('highlights an invalid opening date before saving and allows an incomplete Draft', () => {
    fixture.detectChanges(); completeForm();
    component.dates.attitudeSelfAssessmentStartDate.setValue(new Date(2027, 11, 31)); component.save(); fixture.detectChanges();
    expect(api.create).not.toHaveBeenCalled();
    const field = fixture.nativeElement.querySelector('label[for=attitudeSelfAssessmentStartDate]').parentElement;
    expect(field.querySelector('.field-error').textContent).toContain('ATTITUDE_OPENING_BEFORE_START');
    component.dates.attitudeSelfAssessmentStartDate.setValue(null); component.preview();
    expect(api.preview).not.toHaveBeenCalled();
    expect(component.fieldIssue('attitudeSelfAssessmentStartDate')?.key).toContain('REQUIRED');
    component.save();
    expect(api.create).toHaveBeenCalledWith(jasmine.objectContaining({ attitudeSelfAssessmentStartDate: null }));
  });
  it('sends an edited Upcoming opening date through the existing update request', () => {
    params.next(convertToParamMap({ id: '29' })); api.get.and.returnValue(of(savedReview())); fixture.detectChanges();
    expect(component.dates.attitudeSelfAssessmentStartDate.enabled).toBeTrue();
    component.dates.attitudeSelfAssessmentStartDate.setValue(new Date(2028, 10, 1)); component.save();
    expect(api.update).toHaveBeenCalledWith(29, jasmine.objectContaining({ attitudeSelfAssessmentStartDate: '2028-11-01' }));
  });
  it('includes the opening date in the read-only period summary', () => {
    params.next(convertToParamMap({ id: '29' })); api.get.and.returnValue(of({ ...savedReview(), status: 'OPEN' })); fixture.detectChanges();
    const labels: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('dt'));
    const opening = labels.find(label => label.textContent?.includes('REVIEW_PERIOD.FIELD.attitudeSelfAssessmentStartDate'));
    expect(opening?.nextElementSibling?.textContent).toContain('01 Oct 2028');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });
  it('loads changed published settings for an earlier new Start Date and can explicitly reload them', () => {
    const values = defaults(); values.sourceReviewPeriodName = '2027 Annual Review Period';
    values.employeeLevelConfigurations[0].companyKpiWeight = 70;
    values.roleConfigurations[0].reviewFrequency = 'QUARTERLY';
    api.creationDefaults.and.returnValue(of(values)); fixture.detectChanges();
    component.dates.startDate.setValue(new Date(2026, 9, 7));
    expect(component.levelRows.at(0).controls.companyKpiWeight.value).toBe(70);
    expect(component.roleRows.at(0).controls.reviewFrequency.value).toBe('QUARTERLY');
    component.levelRows.at(0).controls.companyKpiWeight.setValue(60);
    component.loadDefaults();
    expect(component.levelRows.at(0).controls.companyKpiWeight.value).toBe(70);
    expect(values.employeeLevelConfigurations[0].companyKpiWeight).toBe(70);
  });
  it('cancels an older defaults request when the Start Date changes', () => {
    const first = new Subject<AnnualReviewCreationDefaults>(); const second = new Subject<AnnualReviewCreationDefaults>();
    api.creationDefaults.and.returnValues(first, second); fixture.detectChanges();
    component.dates.startDate.setValue(new Date(2028, 0, 1));
    component.dates.startDate.setValue(new Date(2029, 0, 1));
    const old = defaults(); old.employeeLevelConfigurations[0].companyKpiWeight = 99;
    first.next(old); second.next(defaults());
    expect(component.levelRows.at(0).controls.companyKpiWeight.value).toBe(15);
  });
  it('does not overwrite manually edited weights on a later Start Date change', () => {
    fixture.detectChanges(); component.dates.startDate.setValue(new Date(2028, 0, 1));
    component.levelRows.at(0).controls.companyKpiWeight.setValue(20); component.levelRows.at(0).controls.companyKpiWeight.markAsDirty();
    api.creationDefaults.calls.reset(); component.dates.startDate.setValue(new Date(2029, 0, 1));
    expect(api.creationDefaults).not.toHaveBeenCalled(); expect(component.defaultsNeedRefresh).toBeTrue();
    expect(component.levelRows.at(0).controls.companyKpiWeight.value).toBe(20);
  });
  it('retains existing period values without auto-loading defaults when edited', () => {
    params.next(convertToParamMap({ id: '29' })); fixture.detectChanges();
    component.dates.startDate.setValue(new Date(2029, 0, 1));
    expect(api.creationDefaults).not.toHaveBeenCalled(); expect(component.levelRows.at(0).controls.companyKpiWeight.value).toBe(15);
  });
  it('refreshes Draft Role levels without discarding form edits or frequencies', () => {
    params.next(convertToParamMap({ id: '29' })); fixture.detectChanges();
    component.form.controls.name.setValue('Edited name');
    component.roleRows.at(0).controls.reviewFrequency.setValue('QUARTERLY');
    api.roles.and.returnValue(of([{ ...savedReview().roleConfigurations[0], employeeLevelId: 3, employeeLevelName: 'Junior Management' }]));
    component.refreshRoles();
    expect(component.roleOptions[0].employeeLevelId).toBe(3);
    expect(component.form.controls.name.value).toBe('Edited name');
    expect(component.roleRows.at(0).controls.reviewFrequency.value).toBe('QUARTERLY');
  });
  it('preserves published Role level snapshots when current Role classifications change', () => {
    params.next(convertToParamMap({ id: '29' })); api.get.and.returnValue(of(savedReview())); fixture.detectChanges();
    api.roles.and.returnValue(of([{ ...savedReview().roleConfigurations[0], employeeLevelId: 3 }]));
    component.refreshRoles();
    expect(component.roleOptions[0].employeeLevelId).toBe(4);
    expect(component.roleRows.at(0).controls.reviewFrequency.value).toBe('MONTHLY');
  });
  it('shows missing publication fields without sending an invalid preview', () => {
    fixture.detectChanges(); component.preview();
    expect(api.preview).not.toHaveBeenCalled(); expect(component.issues.some(issue => issue.section === 'roles')).toBeTrue();
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });
  it('uses the backend-generated schedule and saves Draft edits before publishing', () => {
    params.next(convertToParamMap({ id: '29' })); fixture.detectChanges(); component.preview();
    expect(api.preview).toHaveBeenCalledWith(jasmine.anything(), 29);
    expect(component.previewPeriod?.checkpoints[0].superiorAssessmentDeadline).toBe('2028-02-10');
    component.publish();
    expect(api.update).toHaveBeenCalled(); expect(api.publish).toHaveBeenCalledWith(29);
    expect(api.update).toHaveBeenCalledBefore(api.publish);
    expect(router.navigate).toHaveBeenCalledWith(['/kpi-administration/review-periods', 29]);
  });
  it('creates and publishes a new record only after successful preview', () => {
    fixture.detectChanges(); completeForm(); component.publish(); expect(api.create).not.toHaveBeenCalled();
    component.preview(); component.publish(); expect(api.create).toHaveBeenCalledWith(jasmine.anything(), true);
  });
  it('renders Open periods read-only even when an edit URL is requested', () => {
    params.next(convertToParamMap({ id: '29' })); api.get.and.returnValue(of({ ...savedReview(), status: 'OPEN' }));
    fixture.detectChanges(); expect(component.viewOnly).toBeTrue(); expect(fixture.nativeElement.querySelector('form')).toBeNull();
    component.save(); component.preview(); component.publish();
    expect(api.update).not.toHaveBeenCalled(); expect(api.preview).not.toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled();
  });
  it('switches to read-only when saving an Upcoming period makes it Open', () => {
    params.next(convertToParamMap({ id: '29' })); api.get.and.returnValue(of(savedReview())); fixture.detectChanges();
    api.update.and.returnValue(of({ ...savedReview(), status: 'OPEN' }));
    component.save(); fixture.detectChanges();
    expect(api.update).toHaveBeenCalled(); expect(component.viewOnly).toBeTrue();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });
  it('limits Upcoming edits to name, dates and deadlines while sending frozen settings unchanged', () => {
    params.next(convertToParamMap({ id: '29' })); api.get.and.returnValue(of({ ...savedReview(), editMode: 'LIMITED' }));
    fixture.detectChanges();
    expect(component.limitedEdit).toBeTrue(); expect(component.roleRows.disabled).toBeTrue(); expect(component.levelRows.disabled).toBeTrue();
    expect(component.form.controls.kpiPerformanceWeight.disabled).toBeTrue();
    expect(component.form.controls.annualKpiConsolidationMethod.disabled).toBeTrue();
    expect(component.form.controls.name.enabled).toBeTrue(); expect(component.dates.endDate.enabled).toBeTrue();
    component.selectAll(false); expect(component.selectedRoleCount).toBe(1);
    component.form.controls.name.setValue('Updated name'); component.save();
    expect(api.update).toHaveBeenCalledWith(29, jasmine.objectContaining({ name: 'Updated name',
      roleConfigurations: [{ roleId: 7, reviewFrequency: 'MONTHLY' }], kpiPerformanceWeight: 50, attitudeEvaluationWeight: 50 }));
    expect(api.creationDefaults).not.toHaveBeenCalled();
  });
  it('keeps the form and API error when publication fails', () => {
    params.next(convertToParamMap({ id: '29' })); fixture.detectChanges(); component.preview();
    api.publish.and.returnValue(throwError(() => ({ error: { message: 'Dates overlap an Open period' } })));
    component.publish(); expect(component.error).toBe('Dates overlap an Open period');
    expect(component.errorSection).toBe('basic');
    expect(component.form.controls.name.value).toBe('2028 Annual KPI Review'); expect(component.busy).toBeFalse();
  });
});
