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
  }

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
