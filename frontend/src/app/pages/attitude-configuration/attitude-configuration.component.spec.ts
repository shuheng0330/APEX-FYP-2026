import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline, SearchOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { AttitudeConfigurationComponent } from './attitude-configuration.component';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';
import { AttitudeConfigurationService } from '../../services/attitude-configuration.service';
import { AttitudeConfiguration, AttitudeConfigurationOptions, AttitudeConfigurationRequest,
  AttitudeCriterion, AttitudePeriodConfiguration, attitudePublicationIssues, emptyAttitudeConfiguration } from '../../models/attitude-configuration.model';

describe('Attitude Evaluation Setup', () => {
  let fixture: ComponentFixture<AttitudeConfigurationComponent>;
  let component: AttitudeConfigurationComponent;
  let api: jasmine.SpyObj<AttitudeConfigurationService>;
  let modal: jasmine.SpyObj<NzModalService>;
  let query: { queryParamMap: ReturnType<typeof convertToParamMap> };
  const options: AttitudeConfigurationOptions = { formats: ['MANAGER', 'SALES', 'OTHERS'],
    roles: [{ roleId: 2, roleName: 'Sales Executive', departmentName: 'Sales', evaluationFormat: null }, { roleId: 3, roleName: 'Manager', departmentName: 'Operations', evaluationFormat: null }], reviewPeriods: [] };
  const period = (configuration: AttitudeConfiguration | null = null): AttitudePeriodConfiguration => ({
    reviewPeriodId: 4, reviewPeriodName: 'Annual Review 2026', reviewPeriodStatus: 'OPEN', configuration,
    unmappedRoleNames: [], canBindInitially: configuration === null });
  const criterion = (name = 'Integrity'): AttitudeCriterion => ({ id: 10, name, description: 'Acts honestly.',
    criterionType: 'SHARED_CORE_VALUE', evaluationFormat: null, active: true, displayOrder: 0 });
  const complete = (): AttitudeConfigurationRequest => ({ name: '2027 Attitude Configuration', criteria: [criterion()],
    ratingDefinitions: [5, 4, 3, 2, 1].map(point => ({ point, label: `Label ${point}`, description: `Expected behaviour ${point}` })), roleMappings: [] });
  const edition = (status: AttitudeConfiguration['status'] = 'DRAFT', id = 1, request = complete()): AttitudeConfiguration => ({
    ...structuredClone(request), id, status, createdAt: '2026-10-10T10:00:00Z', updatedAt: '2026-10-10T10:00:00Z',
    createdBy: 'actor-id', updatedBy: 'actor-id', publishedAt: status === 'PUBLISHED' ? '2026-10-10T11:00:00Z' : null, publishedBy: null });
  beforeEach(async () => {
    query = { queryParamMap: convertToParamMap({}) };
    api = jasmine.createSpyObj<AttitudeConfigurationService>('api', ['list', 'optionsList', 'create', 'update', 'copy', 'publish', 'period', 'bindInitially', 'assignment', 'updateAssignment']);
    api.list.and.returnValue(of([])); api.optionsList.and.returnValue(of(structuredClone(options)));
    api.create.and.callFake(request => of(edition('DRAFT', 1, request)));
    api.update.and.callFake((id, request) => of(edition('DRAFT', id, request)));
    api.copy.and.returnValue(of(edition('DRAFT', 2))); api.publish.and.returnValue(of(edition('PUBLISHED')));
    api.period.and.returnValue(of(period())); api.bindInitially.and.returnValue(of(period(edition('PUBLISHED'))));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [AttitudeConfigurationComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: query } },
      { provide: AttitudeConfigurationService, useValue: api }, { provide: NzModalService, useValue: modal },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline, SearchOutline] }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { ATTITUDE_SETUP: { PERIOD_UNMAPPED: 'These Job Roles have no format: {{roles}}.' } }); translate.use('en');
    fixture = TestBed.createComponent(AttitudeConfigurationComponent); component = fixture.componentInstance; fixture.detectChanges();
  });
  function decide(accept = true) {
    const settings = modal.confirm.calls.mostRecent().args[0];
    const action = accept ? settings?.nzOnOk : settings?.nzOnCancel;
    if (action && !(action instanceof EventEmitter)) action(undefined);
  }
  function load(value: AttitudeConfiguration) {
    api.list.and.returnValue(of([value])); component.ngOnInit(); fixture.detectChanges();
  }
  it('starts with five fixed blank points and no invented criteria, labels or Role mappings', () => {
    expect(component.form).toEqual(emptyAttitudeConfiguration()); expect(component.dirty).toBeFalse();
    expect(component.form.ratingDefinitions.map(r => r.point)).toEqual([5, 4, 3, 2, 1]);
    expect(fixture.nativeElement.querySelectorAll('.rating-table tbody tr').length).toBe(5);
    expect(api.create).not.toHaveBeenCalled(); expect(api.copy).not.toHaveBeenCalled();
  });
  afterEach(() => { expect(api.assignment).not.toHaveBeenCalled(); expect(api.updateAssignment).not.toHaveBeenCalled(); });
  it('keeps editor actions with the labelled editor rather than the page or period summary', () => {
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('.page-heading button')).toBeNull();
    const editor = page.querySelector('#attitude-details')!;
    expect(editor.querySelector('h3')?.textContent).toContain('ATTITUDE_SETUP.EDITOR_TITLE');
    expect(editor.querySelector('h3')?.textContent).toContain('ATTITUDE_SETUP.STATUS.DRAFT');
    expect(editor.querySelector('.editor-heading')?.textContent).toContain('ATTITUDE_SETUP.DRAFT_EDITOR_HELP');
    expect(editor.querySelectorAll('.editor-actions button').length).toBe(3);
    expect(editor.querySelector('.preservation-note')?.textContent).toContain('ATTITUDE_SETUP.REUSE_HELP');
    load(edition('PUBLISHED')); fixture.detectChanges();
    expect(editor.querySelector('h3')?.textContent).toContain('ATTITUDE_SETUP.STATUS.PUBLISHED');
    expect(editor.querySelector('.editor-heading')?.textContent).toContain('ATTITUDE_SETUP.PUBLISHED_EDITOR_HELP');
    expect(editor.querySelectorAll('.editor-actions button').length).toBe(2);
  });
  it('shows compact assigned details and an integrated View action for Upcoming, Open and Closed periods without mutating either configuration', () => {
    const bound = edition('PUBLISHED', 8); load(edition('DRAFT', 9));
    for (const status of ['UPCOMING', 'OPEN', 'CLOSED'] as const) {
      api.period.and.returnValue(of({ ...period(bound), reviewPeriodStatus: status, canBindInitially: false }));
      component.loadPeriod(4); fixture.detectChanges();
      const summary: HTMLElement = fixture.nativeElement.querySelector('.assigned-summary');
      expect(summary.querySelectorAll('.assigned-details > div').length).toBe(4);
      expect(summary.textContent).toContain('Annual Review 2026'); expect(summary.textContent).toContain(bound.name!);
      expect(summary.textContent).toContain('ATTITUDE_SETUP.COVERAGE_COMPLETE'); expect(summary.textContent).toContain('10 Oct 2026');
      expect(summary.querySelector('.assigned-summary-footer button')?.textContent).toContain('ATTITUDE_SETUP.VIEW_ASSIGNED');
      expect(summary.querySelector('.readiness-state')).toBeNull();
      expect(component.selected?.id).toBe(9); expect(component.periodContext?.configuration?.id).toBe(8);
      expect(api.update).not.toHaveBeenCalled(); expect(api.bindInitially).not.toHaveBeenCalled();
      for (const width of [1440, 768, 360]) {
        const view = reviewLayout(summary, width);
        try { expect(view.root.scrollWidth).withContext(`${status}: ${width}`).toBeLessThanOrEqual(width); }
        finally { view.remove(); }
      }
    }
  });
  it('never renders or fetches deferred correction controls in any period status', () => {
    for (const status of ['DRAFT', 'UPCOMING', 'OPEN', 'CLOSED'] as const) {
      api.period.and.returnValue(of({ ...period(edition('PUBLISHED')), reviewPeriodStatus: status, canBindInitially: false }));
      component.loadPeriod(4); fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.assignment-correction')).toBeNull();
      expect(fixture.nativeElement.querySelector('#attitude-assignment-history')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('ATTITUDE_SETUP.CORRECTION.');
    }
  });
  it('opens the requested period and its assigned Published edition, never the newer global Draft', () => {
    const bound = edition('PUBLISHED', 8);
    api.list.and.returnValue(of([edition('DRAFT', 9), bound]));
    api.optionsList.and.returnValue(of({ ...structuredClone(options), reviewPeriods: [{ id: 4, name: 'Annual Review 2026', status: 'OPEN' }] }));
    api.period.and.returnValue(of({ ...period(bound), unmappedRoleNames: ['Sales Executive', 'Manager'] }));
    query.queryParamMap = convertToParamMap({ reviewPeriodId: '4', configurationId: '9' });
    component.ngOnInit(); fixture.detectChanges();
    expect(api.period).toHaveBeenCalledWith(4); expect(component.selected?.id).toBe(8); expect(component.readonly).toBeTrue();
    expect(fixture.nativeElement.querySelector('.readiness-required').textContent).toContain('REVIEW_PERIOD.ATTITUDE_CONFIGURATION.INCOMPLETE');
    expect(fixture.nativeElement.textContent).toContain('Sales Executive, Manager');
    expect(api.create).not.toHaveBeenCalled(); expect(api.bindInitially).not.toHaveBeenCalled();
  });
  it('opens a Published edition from a new-period setup link without binding or choosing a different period', () => {
    const bound = edition('PUBLISHED', 8);
    api.list.and.returnValue(of([edition('DRAFT', 9), bound]));
    query.queryParamMap = convertToParamMap({ configurationId: '8' }); component.ngOnInit();
    expect(component.selected?.id).toBe(8); expect(api.bindInitially).not.toHaveBeenCalled(); expect(component.dirty).toBeFalse();
  });
  it('shows unavailable context for invalid selectors and does not silently open another period', () => {
    api.period.calls.reset(); query.queryParamMap = convertToParamMap({ reviewPeriodId: '-1' }); component.ngOnInit();
    expect(api.period).not.toHaveBeenCalled(); expect(component.periodContext).toBeNull();
    expect(component.bindingError).toBe('ATTITUDE_SETUP.CONTEXT_UNAVAILABLE');
  });
  it('does not open an unrelated global Draft or overwrite new edits while a linked-period lookup completes', () => {
    const pending = new Subject<AttitudePeriodConfiguration>();
    api.list.and.returnValue(of([edition('DRAFT', 9), edition('PUBLISHED', 8)]));
    api.period.and.returnValue(pending); query.queryParamMap = convertToParamMap({ reviewPeriodId: '4' }); component.ngOnInit();
    expect(component.selected).toBeNull();
    component.form.name = 'Unsaved editor change';
    pending.next(period(edition('PUBLISHED', 8))); pending.complete();
    expect(component.form.name).toBe('Unsaved editor change'); expect(component.selected).toBeNull(); expect(component.dirty).toBeTrue();
    expect(component.periodContext?.configuration?.id).toBe(8); expect(api.update).not.toHaveBeenCalled();
  });
  it('opens a saved Draft period with its Published selection rather than the global configuration Draft', () => {
    const saved = edition('PUBLISHED', 8);
    api.list.and.returnValue(of([edition('DRAFT', 9), saved]));
    api.period.and.returnValue(of({ ...period(saved), reviewPeriodStatus: 'DRAFT', canBindInitially: false }));
    query.queryParamMap = convertToParamMap({ reviewPeriodId: '4', configurationId: '8' }); component.ngOnInit(); fixture.detectChanges();
    expect(component.periodId).toBe(4); expect(component.selected?.id).toBe(8); expect(component.readonly).toBeTrue();
    expect(component.editionNotice).toBe('ATTITUDE_SETUP.VIEWING_SAVED_DRAFT_SELECTION');
    expect(fixture.nativeElement.querySelector('#attitude-period-binding').textContent).toContain('ATTITUDE_SETUP.SELECTED_PERIOD');
    expect(api.bindInitially).not.toHaveBeenCalled(); expect(api.updateAssignment).not.toHaveBeenCalled();
  });
  it('shows a valid unsaved Published choice for a Draft without changing its saved selection', () => {
    const saved = edition('PUBLISHED', 8), chosen = edition('PUBLISHED', 7);
    api.list.and.returnValue(of([edition('DRAFT', 9), saved, chosen]));
    api.period.and.returnValue(of({ ...period(saved), reviewPeriodStatus: 'DRAFT', canBindInitially: false }));
    query.queryParamMap = convertToParamMap({ reviewPeriodId: '4', configurationId: '7' }); component.ngOnInit();
    expect(component.selected?.id).toBe(7); expect(component.periodContext?.configuration?.id).toBe(8);
    expect(component.editionNotice).toBe('ATTITUDE_SETUP.VIEWING_DRAFT_SELECTION'); expect(component.canBind).toBeFalse();
    expect(api.update).not.toHaveBeenCalled(); expect(api.bindInitially).not.toHaveBeenCalled(); expect(api.updateAssignment).not.toHaveBeenCalled();
  });
  function openDraftContext(unsaved = false) {
    const saved = edition('PUBLISHED', 8), chosen = edition('PUBLISHED', 7);
    api.list.and.returnValue(of([edition('DRAFT', 9), saved, chosen]));
    api.period.and.returnValue(of({ ...period(saved), reviewPeriodStatus: 'DRAFT', canBindInitially: false, unmappedRoleNames: ['Sales Executive', 'Manager'] }));
    query.queryParamMap = convertToParamMap({ reviewPeriodId: '4', configurationId: unsaved ? '7' : '8' });
    component.ngOnInit(); fixture.detectChanges();
  }
  it('shows compact Draft context and verified missing Roles without a period switcher or assignment controls', () => {
    openDraftContext();
    const context: HTMLElement = fixture.nativeElement.querySelector('.draft-context');
    expect(context.textContent).toContain('Annual Review 2026'); expect(context.textContent).toContain('2027 Attitude Configuration');
    expect(context.textContent).toContain('Sales Executive'); expect(context.textContent).toContain('Manager');
    expect(context.textContent).toContain('ATTITUDE_SETUP.DRAFT_RESOLUTION');
    expect(context.querySelector('nz-select')).toBeNull(); expect(context.querySelector('button')).toBeNull();
    expect(context.querySelector('.readiness-state')).toBeNull();
    expect(component.periodContext?.configuration?.id).toBe(8);
  });
  it('labels an unsaved Published choice and never claims the saved configuration coverage describes it', () => {
    openDraftContext(true);
    const context: HTMLElement = fixture.nativeElement.querySelector('.draft-context');
    expect(component.draftConfiguration?.id).toBe(7); expect(component.unsavedDraftSelection).toBeTrue();
    expect(context.textContent).toContain('ATTITUDE_SETUP.UNSAVED_SELECTION'); expect(context.textContent).toContain('ATTITUDE_SETUP.VERIFY_SELECTION');
    expect(context.querySelector('.attitude-role-list')).toBeNull();
    expect(context.textContent).not.toContain('Sales Executive'); expect(context.textContent).not.toContain('ALL_COVERED');
    expect(component.periodContext?.configuration?.id).toBe(8);
  });
  it('selects and focuses an editable Draft copy, then reminds HR to return and explicitly select the Published version', async () => {
    openDraftContext(); const original = structuredClone(component.periodContext);
    component.copyPublished(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(api.copy).toHaveBeenCalledOnceWith(8); expect(component.selected?.id).toBe(2); expect(component.readonly).toBeFalse();
    expect(fixture.nativeElement.querySelector('#configuration-name').disabled).toBeFalse();
    expect(document.activeElement?.id).toBe('attitude-details'); expect(component.draftConfiguration?.id).toBe(8);
    component.setRoleFormat(options.roles[0], 'SALES');
    api.publish.and.callFake(id => of(edition('PUBLISHED', id, component.form)));
    component.saveDraft(true); fixture.detectChanges();
    expect(component.selected?.id).toBe(2); expect(component.readonly).toBeTrue();
    expect(component.success).toBe('ATTITUDE_SETUP.PUBLISHED_RETURN');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('ATTITUDE_SETUP.PUBLISHED_RETURN');
    expect(component.periodContext).toEqual(original); expect(component.draftConfiguration?.id).toBe(8);
    expect(api.bindInitially).not.toHaveBeenCalled();
  });
  it('does not show a return-after-publication reminder for Draft saving or failed publication', () => {
    openDraftContext(); component.copyPublished(); component.saveDraft(); expect(component.success).toBe('ATTITUDE_SETUP.SAVED');
    api.publish.and.returnValue(throwError(() => ({ error: { message: 'Publication rejected' } })));
    component.saveDraft(true); fixture.detectChanges();
    expect(component.success).toBe(''); expect(component.error).toBe('Publication rejected'); expect(component.readonly).toBeFalse();
    expect(fixture.nativeElement.textContent).not.toContain('ATTITUDE_SETUP.PUBLISHED_RETURN');
    expect(component.periodContext?.configuration?.id).toBe(8);
  });
  it('shows the return reminder for successful publication from a new unsaved period link too', () => {
    api.list.and.returnValue(of([edition('PUBLISHED', 8)]));
    query.queryParamMap = convertToParamMap({ configurationId: '8' }); component.ngOnInit(); component.copyPublished(); component.saveDraft(true);
    expect(component.success).toBe('ATTITUDE_SETUP.PUBLISHED_RETURN'); expect(component.periodId).toBeNull();
    expect(api.bindInitially).not.toHaveBeenCalled();
  });
  it('wraps compact Draft context and missing Role names at desktop, tablet and phone widths', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { ATTITUDE_SETUP: { DRAFT_CONTEXT: 'Review Period Draft', SELECTED_PERIOD: 'Selected Annual Review Period',
      CONFIGURATION_FOR_DRAFT: 'Configuration Selected for This Draft', MISSING_ROLE_FORMATS: 'Job Roles Missing Formats',
      DRAFT_RESOLUTION: 'Create a Draft Copy, assign missing formats and publish. Return to your Review Period to refresh and select it.' } }, true);
    openDraftContext(); component.periodContext!.unmappedRoleNames = ['LongJobRole'.repeat(18)]; fixture.detectChanges();
    for (const width of [1440, 768, 360]) {
      const view = reviewLayout(fixture.nativeElement.querySelector('.draft-context'), width);
      try { expect(view.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width); }
      finally { view.remove(); }
    }
  });
  it('never treats an unavailable or unpublished link as the selected Published edition', () => {
    api.list.and.returnValue(of([edition('DRAFT', 9)]));
    query.queryParamMap = convertToParamMap({ configurationId: '9' }); component.ngOnInit();
    expect(component.selected).toBeNull(); expect(component.bindingError).toBe('ATTITUDE_SETUP.CONTEXT_UNAVAILABLE');
    expect(api.create).not.toHaveBeenCalled(); expect(api.update).not.toHaveBeenCalled();
  });
  it('preserves the authoritative Closed-period assignment even if another Published edition is linked', () => {
    const saved = edition('PUBLISHED', 8), other = edition('PUBLISHED', 7);
    api.list.and.returnValue(of([edition('DRAFT', 9), saved, other]));
    api.period.and.returnValue(of({ ...period(saved), reviewPeriodStatus: 'CLOSED', canBindInitially: false }));
    query.queryParamMap = convertToParamMap({ reviewPeriodId: '4', configurationId: '7' }); component.ngOnInit();
    expect(component.selected?.id).toBe(8); expect(component.canBind).toBeFalse();
    expect(api.updateAssignment).not.toHaveBeenCalled();
  });
  it('distinguishes assignment from complete Role coverage and keeps the assigned edition read-only', () => {
    const bound=edition('PUBLISHED',8);
    api.list.and.returnValue(of([edition('DRAFT',9),bound]));
    api.optionsList.and.returnValue(of({ ...structuredClone(options), reviewPeriods: [{ id: 4, name: 'Annual Review 2026', status: 'OPEN' }] }));
    api.period.and.returnValue(of(period(bound))); component.ngOnInit(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.readiness-ready').textContent).toContain('ATTITUDE_SETUP.COVERAGE_COMPLETE');
    expect(component.selected?.id).toBe(9); expect(component.canBind).toBeFalse();
    component.viewAssignedConfiguration();
    return fixture.whenStable().then(() => { expect(component.selected?.id).toBe(8); expect(component.readonly).toBeTrue(); });
  });
  it('allows an incomplete Draft to be saved and does not publish it', () => {
    component.saveDraft(); expect(api.create).toHaveBeenCalledOnceWith(emptyAttitudeConfiguration());
    expect(component.selected?.status).toBe('DRAFT'); expect(component.dirty).toBeFalse(); expect(api.publish).not.toHaveBeenCalled();
    component.form.name = 'New name'; component.saveDraft(); expect(api.update).toHaveBeenCalled(); expect(api.create).toHaveBeenCalledTimes(1);
  });
  it('blocks incomplete publication and points to the relevant sections with inline feedback', () => {
    component.confirmPublish(); fixture.detectChanges();
    expect(component.showValidation).toBeTrue(); expect(component.issues.map(i => i.section)).toEqual(['details', 'ratings', 'criteria']);
    expect(fixture.nativeElement.querySelectorAll('.rating-table .field-error').length).toBe(10);
    expect(api.publish).not.toHaveBeenCalled(); expect(modal.confirm).not.toHaveBeenCalled();
  });
  it('permits a complete shared criterion to cover all formats without demanding all Role mappings', () => {
    component.form = complete(); expect(component.issues).toEqual([]); expect(component.unmappedCount).toBe(2);
    component.confirmPublish(); expect(api.publish).not.toHaveBeenCalled(); decide();
    expect(api.create).toHaveBeenCalled(); expect(api.publish).toHaveBeenCalledOnceWith(1);
    expect(component.readonly).toBeTrue(); expect(component.selected?.status).toBe('PUBLISHED');
  });
  it('requires format-specific coverage only where no active shared value exists', () => {
    component.form = complete(); component.form.criteria[0].criterionType = 'FORMAT_SPECIFIC'; component.form.criteria[0].evaluationFormat = 'SALES';
    expect(component.issues.map(i => i.key)).toContain('FORMATS_REQUIRED');
    component.form.criteria.push({ ...criterion('Respect'), active: false });
    expect(component.applicableCriteria('MANAGER')).toEqual([]);
    component.form.criteria[1].active = true; expect(component.issues).toEqual([]);
  });
  it('edits ratings with fixed points and preserves incomplete definitions in Draft', () => {
    component.openRating(component.form.ratingDefinitions[0]); component.rating!.label = 'Excellent'; component.applyEditor();
    expect(component.form.ratingDefinitions[0]).toEqual({ point: 5, label: 'Excellent', description: '' });
    expect(component.issues.map(i => i.key)).toContain('RATINGS_REQUIRED');
  });
  it('shows required markers without initial errors, then nonblocking readiness after saving an incomplete Draft', () => {
    expect(fixture.nativeElement.querySelectorAll('.required-mark').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('.draft-readiness')).toBeNull();
    component.saveDraft(); fixture.detectChanges();
    expect(api.create).toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.draft-readiness')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.rating-table .field-error').length).toBe(10);
  });
  it('validates editor fields on blur and Apply while allowing incomplete Draft definitions', () => {
    component.openRating(component.form.ratingDefinitions[0]);
    expect(component.editorFieldError('label')).toBeNull();
    component.editorTouched.add('label'); expect(component.editorFieldError('label')).toBe('ATTITUDE_SETUP.REQUIRED_FIELD');
    component.rating!.label = 'Meets expectation'; expect(component.editorFieldError('label')).toBeNull();
    component.applyEditor(); expect(component.editorMode).toBeNull(); expect(component.draftValidation).toBeTrue();
    component.openRating(component.form.ratingDefinitions[0]);
    expect(component.editorFieldError('description')).toBe('ATTITUDE_SETUP.REQUIRED_FIELD');
    component.rating!.description = 'x'.repeat(10001); component.applyEditor();
    expect(component.editorMode).toBe('rating'); expect(component.editorFieldError('description')).toBe('ATTITUDE_SETUP.FIELD_LIMIT');
  });
  it('does not mark inactive criteria or optional Role mappings as required', () => {
    component.openCriterion(null, null); component.criterion!.active = false; component.editorValidation = true;
    expect(component.editorFieldError('name')).toBeNull(); expect(component.editorFieldError('description')).toBeNull();
    component.applyEditor(); component.form = complete(); component.saveDraft();
    expect(api.create).toHaveBeenCalled(); expect(component.issues).toEqual([]);
  });
  it('keeps cancelled criterion/rating edits out of the working configuration', () => {
    component.form = complete(); component.openCriterion(component.form.criteria[0], null); component.criterion!.name = 'Unsaved';
    component.editorMode = null; expect(component.form.criteria[0].name).toBe('Integrity');
    component.openRating(component.form.ratingDefinitions[0]); component.rating!.label = 'Unsaved'; component.editorMode = null;
    expect(component.form.ratingDefinitions[0].label).toBe('Label 5');
  });
  it('adds shared and specific criteria without duplicating shared values across formats', () => {
    component.openCriterion(null, null); component.criterion!.name = 'Integrity'; component.applyEditor();
    component.openCriterion(null, 'SALES'); component.criterion!.name = 'Customer care'; component.applyEditor();
    expect(component.form.criteria.length).toBe(2); expect(component.criteriaFor(null).length).toBe(1);
    expect(component.applicableCriteria('SALES').length).toBe(2); expect(component.applicableCriteria('MANAGER').length).toBe(1);
  });
  it('rejects duplicate criterion names within a group but permits the same name in another format', () => {
    component.form = complete(); component.openCriterion(null, null); component.criterion!.name = ' integrity '; component.applyEditor();
    expect(component.editorError).toBe('DUPLICATE_CRITERION'); expect(component.editorMode).toBe('criterion');
    component.openCriterion(null, 'SALES'); component.criterion!.name = 'Integrity'; component.applyEditor(); expect(component.form.criteria.length).toBe(2);
  });
  it('retains inactive criteria and moves criteria only within their own group', () => {
    component.form = complete(); const second = { ...criterion('Respect'), id: 11 };
    const sales = { ...criterion('Customer care'), id: 12, criterionType: 'FORMAT_SPECIFIC' as const, evaluationFormat: 'SALES' as const };
    component.form.criteria.push(sales, second); component.moveCriterion(second, -1);
    expect(component.form.criteria.map(c => c.name)).toEqual(['Respect', 'Customer care', 'Integrity']);
    component.toggleActive(second); expect(component.form.criteria.length).toBe(3); expect(component.criteriaFor(null).length).toBe(2);
    expect(component.applicableCriteria('MANAGER').map(c => c.name)).toEqual(['Integrity']);
  });
  it('maps and clears Roles by ID, without inferring a format from the Role name', () => {
    expect(component.formatForRole(3)).toBeNull(); component.setRoleFormat(options.roles[1], 'OTHERS');
    expect(component.formatForRole(3)).toBe('OTHERS'); component.setRoleFormat(options.roles[1], 'SALES');
    expect(component.form.roleMappings.length).toBe(1); component.setRoleFormat(options.roles[1], null);
    expect(component.form.roleMappings).toEqual([]);
  });
  it('shows unavailable historical Roles and allows their Draft mappings to be cleared', () => {
    component.form = complete(); component.form.roleMappings = [{ roleId: 9, roleName: 'Old Role', evaluationFormat: 'MANAGER' }];
    expect(component.roleOptions.map(r => r.roleName)).toContain('Old Role'); expect(component.issues.map(i => i.key)).toContain('INELIGIBLE_ROLE');
    component.setRoleFormat(component.roleOptions.find(r => r.roleId === 9)!, null); expect(component.issues).toEqual([]);
  });
  it('does not allow any mutations or publication of a Published configuration', async () => {
    load(edition('PUBLISHED')); const before = structuredClone(component.form);
    component.openRating(component.form.ratingDefinitions[0]); expect(component.editorReadonly).toBeTrue();
    component.rating!.label = 'Changed'; component.applyEditor(); component.editorMode = null;
    component.toggleActive(component.form.criteria[0]); component.setRoleFormat(options.roles[0], 'SALES');
    component.moveCriterion(component.form.criteria[0], 1); component.saveDraft(); component.confirmPublish();
    expect(component.form).toEqual(before); expect(api.update).not.toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled();
    await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#configuration-name').disabled).toBeTrue();
  });
  it('creates an independent Draft copy and leaves the historical edition unchanged', () => {
    const original = edition('PUBLISHED'); load(original); component.copyPublished(); component.form.criteria[0].name = 'Changed';
    expect(api.copy).toHaveBeenCalledOnceWith(1); expect(component.selected?.id).toBe(2); expect(component.readonly).toBeFalse();
    expect(component.editions.find(e => e.id === 1)?.criteria[0].name).toBe('Integrity'); expect(original.criteria[0].name).toBe('Integrity');
  });
  it('previews unsaved changes without saving or publishing and includes format/rating/mapping information', () => {
    component.form = complete(); component.form.roleMappings = [{ roleId: 2, roleName: 'Sales Executive', evaluationFormat: 'SALES' }];
    component.previewVisible = true; fixture.detectChanges();
    const preview = document.querySelector('.preview-content')!;
    expect(preview.textContent).toContain('2027 Attitude Configuration'); expect(preview.querySelectorAll('.preview-rating').length).toBe(5);
    expect(preview.querySelectorAll('.preview-format').length).toBe(3); expect(preview.textContent).toContain('Sales Executive');
    expect(api.create).not.toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled();
  });
  it('preserves unsaved inputs on save failure and keeps a saved Draft after publish failure', () => {
    component.form = complete(); api.create.and.returnValue(throwError(() => ({ error: { message: 'Save conflict' } })));
    component.saveDraft(); expect(component.form.name).toBe('2027 Attitude Configuration'); expect(component.error).toBe('Save conflict');
    expect(component.dirty).toBeTrue(); expect(component.busy).toBeFalse();
    api.create.and.callFake(request => of(edition('DRAFT', 1, request)));
    api.publish.and.returnValue(throwError(() => ({ error: { message: 'Role eligibility changed' } })));
    component.saveDraft(true); expect(component.selected?.id).toBe(1); expect(component.readonly).toBeFalse();
    expect(component.error).toBe('Role eligibility changed'); component.saveDraft(); expect(api.update).toHaveBeenCalled();
  });
  it('prevents duplicate saves and navigation during an in-flight request', () => {
    const pending = new Subject<AttitudeConfiguration>(); api.create.and.returnValue(pending);
    component.saveDraft(); component.saveDraft(); expect(api.create).toHaveBeenCalledTimes(1); expect(component.canLeave()).toBeFalse();
    pending.next(edition('DRAFT')); pending.complete(); expect(component.busy).toBeFalse(); expect(component.canLeave()).toBeTrue();
  });
  it('retains the selected edition and unsaved inputs when a history change is cancelled', async () => {
    load(edition()); component.editions.push(edition('PUBLISHED', 2)); component.form.name = 'Unsaved'; component.selectionId = 2;
    const selection = component.selectEdition(2); decide(false); await selection;
    expect(component.selected?.id).toBe(1); expect(component.selectionId).toBe(1); expect(component.form.name).toBe('Unsaved');
    const confirmed = component.selectEdition(2); decide(); await confirmed; expect(component.readonly).toBeTrue(); expect(component.dirty).toBeFalse();
  });
  it('shows API load failures instead of allowing unauthorised or unavailable configuration edits', () => {
    api.list.and.returnValue(throwError(() => ({ error: { message: 'Permission required' } })));
    component.loaded = false; component.ngOnInit(); component.saveDraft();
    expect(component.error).toBe('Permission required'); expect(api.create).not.toHaveBeenCalled(); expect(component.busy).toBeFalse();
  });
  it('validates text limits without requiring incomplete inactive criteria', () => {
    const request = complete(); request.criteria.push({ ...criterion(''), description: '', active: false });
    expect(attitudePublicationIssues(request, options)).toEqual([]); request.ratingDefinitions[0].label = 'x'.repeat(256);
    expect(attitudePublicationIssues(request, options).map(i => i.key)).toContain('TEXT_LIMIT');
    component.openCriterion(null, null); component.criterion!.name = 'x'.repeat(256); component.applyEditor(); expect(component.editorError).toBe('TEXT_LIMIT');
  });
  it('distinguishes identical Job Role names by department and keeps mapping keyed by Role ID', () => {
    component.options.roles = [{ ...options.roles[0], roleName: 'Executive' }, { ...options.roles[1], roleName: 'Executive' }];
    component.setRoleFormat(component.options.roles[0], 'SALES'); component.setRoleFormat(component.options.roles[1], 'OTHERS'); fixture.detectChanges();
    const rows = [...fixture.nativeElement.querySelectorAll('.role-table tbody tr')] as HTMLElement[];
    expect(rows[0].textContent).toContain('Sales'); expect(rows[1].textContent).toContain('Operations');
    expect(component.formatForRole(2)).toBe('SALES'); expect(component.formatForRole(3)).toBe('OTHERS');
    component.roleSearch = 'operations'; expect(component.roleOptions.map(r => r.roleId)).toEqual([3]);
    component.roleSearch = ''; component.saveDraft();
    expect(api.create.calls.mostRecent().args[0].roleMappings).toEqual([{ roleId: 2, evaluationFormat: 'SALES' }, { roleId: 3, evaluationFormat: 'OTHERS' }]);
  });
  it('shows the actionable administrator message when an Open period has no binding or published configuration', () => {
    component.options.reviewPeriods = [{ id: 4, name: 'Annual Review 2026', status: 'OPEN' }]; component.loadPeriod(4); fixture.detectChanges();
    const warning = fixture.nativeElement.querySelector('#attitude-period-binding .readiness-required');
    expect(warning.textContent).toContain('ATTITUDE_SETUP.SETUP_REQUIRED');
    expect(warning.textContent).toContain('ATTITUDE_SETUP.ACTION_REQUIRED');
    expect(warning.textContent).toContain('Annual Review 2026');
    expect(warning.textContent).toContain('ATTITUDE_SETUP.UNBOUND_HELP');
    expect(warning.textContent).toContain('ATTITUDE_SETUP.PUBLISH_NEXT_STEP');
    expect(warning.querySelector('[nzType="warning"]')).not.toBeNull();
    expect(component.canBind).toBeFalse(); component.confirmBinding(); expect(api.bindInitially).not.toHaveBeenCalled();
  });
  it('places readiness first and saved configuration history last without changing the selected configuration', () => {
    load(edition());
    const sections = [...fixture.nativeElement.querySelectorAll('.attitude-page > section.card')] as HTMLElement[];
    expect(sections.map(section => section.id)).toEqual(['attitude-period-binding', 'attitude-details', 'attitude-ratings',
      'attitude-criteria', 'attitude-roles', 'attitude-history']);
    expect(sections[sections.length - 1].querySelector('#configuration-history')).not.toBeNull();
    expect(component.selected?.id).toBe(1);
  });
  it('guides an unbound Open period to assign an existing Published configuration instead of publishing again', () => {
    component.options.reviewPeriods = [{ id: 4, name: 'Annual Review 2026', status: 'OPEN' }];
    component.editions = [edition('PUBLISHED')]; component.bindingConfigurationId = 1;
    component.loadPeriod(4); fixture.detectChanges();
    const section = fixture.nativeElement.querySelector('#attitude-period-binding');
    expect(section.querySelector('.readiness-required').textContent).toContain('ATTITUDE_SETUP.ASSIGN_NEXT_STEP');
    expect(section.querySelector('#attitude-published-configuration')).not.toBeNull();
    expect(section.querySelector('.prepare-configuration')).toBeNull();
    expect(api.bindInitially).not.toHaveBeenCalled();
  });
  it('replaces the action-required warning with a compact assigned state after successful binding', () => {
    component.options.reviewPeriods = [{ id: 4, name: 'Annual Review 2026', status: 'OPEN' }];
    component.editions = [edition('PUBLISHED')]; component.bindingConfigurationId = 1;
    component.loadPeriod(4); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.readiness-required')).not.toBeNull();
    component.confirmBinding(); decide(); fixture.detectChanges();
    const ready = fixture.nativeElement.querySelector('.readiness-ready');
    expect(ready.textContent).toContain('ATTITUDE_SETUP.CONFIGURATION_ASSIGNED');
    expect(ready.textContent).toContain('Annual Review 2026');
    expect(ready.textContent).toContain('2027 Attitude Configuration');
    expect(ready.textContent).toContain('10 Oct 2026');
    expect(fixture.nativeElement.querySelector('.readiness-required')).toBeNull();
    expect(fixture.nativeElement.querySelector('.binding-actions')).toBeNull();
  });
  it('does not show an action-required warning for no Open periods, a loading or failed lookup, or a non-Open period', () => {
    expect(fixture.nativeElement.querySelector('.readiness-required')).toBeNull();
    component.options.reviewPeriods = [{ id: 4, name: 'Annual Review 2026', status: 'OPEN' }];
    const pending = new Subject<AttitudePeriodConfiguration>(); api.period.and.returnValue(pending);
    component.loadPeriod(4); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.readiness-required')).toBeNull();
    pending.error({ error: { message: 'Unavailable' } }); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.readiness-required')).toBeNull();
    api.period.and.returnValue(of({ ...period(), reviewPeriodStatus: 'CLOSED', canBindInitially: false }));
    component.loadPeriod(4); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.readiness-required')).toBeNull();
  });
  it('preserves the affected Role warning without claiming every employee is ready when formats are missing', () => {
    component.options.reviewPeriods = [{ id: 4, name: 'Annual Review 2026', status: 'OPEN' }];
    api.period.and.returnValue(of({ ...period(edition('PUBLISHED')), unmappedRoleNames: ['Sales Executive'] }));
    component.loadPeriod(4); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.readiness-required').textContent).toContain('ATTITUDE_SETUP.CONFIGURATION_ASSIGNED');
    expect(fixture.nativeElement.querySelector('#attitude-period-binding .warning').textContent).toContain('Sales Executive');
    expect(fixture.nativeElement.querySelector('.readiness-ready')).toBeNull();
    expect(component.canBind).toBeFalse();
  });
  it('requires explicit initial binding and does not change an existing Open period just by publishing', () => {
    component.loadPeriod(4); component.form = complete(); component.saveDraft(true); fixture.detectChanges();
    expect(component.periodContext?.configuration).toBeNull(); expect(api.bindInitially).not.toHaveBeenCalled();
    expect(component.bindingConfigurationId).toBe(1); expect(component.canBind).toBeTrue();
    component.confirmBinding(); expect(api.bindInitially).not.toHaveBeenCalled(); decide();
    expect(api.bindInitially).toHaveBeenCalledOnceWith(4, 1); expect(component.periodContext?.configuration?.id).toBe(1);
    expect(component.canBind).toBeFalse(); component.confirmBinding(); expect(api.bindInitially).toHaveBeenCalledTimes(1);
  });
  it('does not replace a bound configuration when a newer one is published', () => {
    const bound = edition('PUBLISHED'); api.period.and.returnValue(of(period(bound))); component.loadPeriod(4);
    component.form = complete(); api.publish.and.returnValue(of(edition('PUBLISHED', 2))); component.saveDraft(true);
    expect(component.periodContext?.configuration?.id).toBe(1); expect(component.canBind).toBeFalse();
    expect(api.bindInitially).not.toHaveBeenCalled();
  });
  it('offers only Published editions, defaults to the most recently published and preserves unsaved configuration edits', () => {
    const older = edition('PUBLISHED', 1), latest = { ...edition('PUBLISHED', 2), publishedAt: '2026-10-11T11:00:00Z' };
    api.list.and.returnValue(of([edition('DRAFT', 3), older, latest]));
    api.optionsList.and.returnValue(of({ ...options, reviewPeriods: [{ id: 4, name: 'Open', status: 'OPEN' }] })); component.ngOnInit();
    expect(component.publishedEditions.map(c => c.id)).toEqual([2, 1]); expect(component.bindingConfigurationId).toBe(2);
    component.form.name = 'Unsaved working copy'; component.confirmBinding(); decide();
    expect(component.form.name).toBe('Unsaved working copy'); expect(component.dirty).toBeTrue();
    expect(api.bindInitially).toHaveBeenCalledOnceWith(4, 2);
  });
  it('does not bind non-Open periods or allow unavailable/stale context to authorise a binding', () => {
    component.editions = [edition('PUBLISHED')]; component.bindingConfigurationId = 1; component.loadPeriod(4);
    component.periodContext!.reviewPeriodStatus = 'CLOSED'; component.confirmBinding(); expect(api.bindInitially).not.toHaveBeenCalled();
    api.period.and.returnValue(throwError(() => ({ error: { message: 'Permission changed' } })));
    component.loadPeriod(4); expect(component.periodContext).toBeNull(); expect(component.bindingError).toBe('Permission changed'); expect(component.canBind).toBeFalse();
  });
  it('cancels a stale period lookup and blocks duplicate binding clicks while preserving failure details', () => {
    const oldRequest = new Subject<AttitudePeriodConfiguration>(); api.period.and.returnValue(oldRequest);
    component.loadPeriod(4); api.period.and.returnValue(of({ ...period(), reviewPeriodId: 5 })); component.loadPeriod(5);
    oldRequest.next(period()); expect(component.periodContext?.reviewPeriodId).toBe(5); expect(component.bindingBusy).toBeFalse();
    component.editions = [edition('PUBLISHED')]; component.bindingConfigurationId = 1;
    const pending = new Subject<AttitudePeriodConfiguration>(); api.bindInitially.and.returnValue(pending);
    component.confirmBinding(); decide(); component.confirmBinding(); expect(api.bindInitially).toHaveBeenCalledTimes(1);
    expect(component.canLeave()).toBeFalse(); pending.error({ error: { message: 'Another administrator already bound this period' } });
    expect(component.bindingError).toContain('already bound'); expect(component.bindingBusy).toBeFalse();
  });
});
