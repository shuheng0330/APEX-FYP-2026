import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { PermissionSelectorComponent } from './permission-selector.component';
import { permissionCatalogue, permissionPresentation } from './permission-presentation';
import { TranslatedAuthority } from '../../models/access-control.model';

describe('Permission selector', () => {
  let fixture: ComponentFixture<PermissionSelectorComponent>;
  let page: PermissionSelectorComponent;
  const catalogue: TranslatedAuthority[] = Object.keys(permissionCatalogue).map((name, i) =>
    ({ id: i + 1, name, label: 'auth.raw.key', description: 'auth.raw.description' }));
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PermissionSelectorComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations()] }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {ACCESS_PERMISSIONS: {"GROUPS":{"BASIC":"Basic Employee Access","STAFF":"Staff & Organisation","ACCESS":"Job Roles & Access","TRAINING":"Training & Competency","PERIOD":"Review Periods","PLANNING":"KPI Planning & Revisions","ASSESSMENT":"Performance Assessment & Attitude","APPRAISAL":"Staff Appraisal","ANALYTICS":"Performance Analytics","OTHER":"Other Permissions"},"ROLE_IMPACT":"Permissions are granted to this Job Role and affect employees assigned to it, not individual employees.","SEARCH":"Search permissions","COUNT":"{{selected}} of {{total}} selected","TOTAL":"{{selected}} permissions selected","LEGACY":"Existing evaluation access","EMPTY":"No matching permissions.","UNKNOWN":"Confirm this additional permission before changing access.","ITEMS":{"ROLE_USER":{"LABEL":"Basic Employee Access","DESCRIPTION":"Access your own profile, learning and eligible employee performance tasks."},"CAN_VIEW_STAFF":{"LABEL":"View Staff","DESCRIPTION":"View staff information across the organisation."},"CAN_MANAGE_STAFF":{"LABEL":"Manage Staff","DESCRIPTION":"Create and maintain staff accounts."},"CAN_MANAGE_ORG_CHART":{"LABEL":"Manage Organisation Structure","DESCRIPTION":"Maintain departments and the organisation structure."},"CAN_MANAGE_ROLE":{"LABEL":"Manage Job Roles","DESCRIPTION":"Maintain departmental Job Roles and their settings."},"CAN_VIEW_ACCESS_CONTROL":{"LABEL":"View Access Permissions","DESCRIPTION":"View permission assignments for Job Roles without changing them."},"CAN_MANAGE_ACCESS_CONTROL":{"LABEL":"Manage Access Permissions","DESCRIPTION":"Grant or revoke Job Role permissions and administer trainer access. Changes can affect multiple employees."},"CAN_MANAGE_COMPETENCY":{"LABEL":"Manage Competencies","DESCRIPTION":"Maintain competency information used across the system."},"CAN_MANAGE_TRAINING":{"LABEL":"Manage Training","DESCRIPTION":"Manage courses you own and associated training activities, subject to existing scope checks."},"CAN_ASSIGN_TRAINING":{"LABEL":"Assign Training","DESCRIPTION":"Assign courses across the organisation and access related training results."},"CAN_MANAGE_LEARNING_MATERIAL":{"LABEL":"Manage Learning Materials","DESCRIPTION":"Create and maintain learning materials."},"CAN_MANAGE_EVALUATION":{"LABEL":"Manage Competency Evaluations","DESCRIPTION":"Use existing competency-evaluation functionality. Also permits viewing other employees' SOP competency scores; this is not the annual KPI assessment permission."},"CAN_MANAGE_EVALUATION_CYCLE":{"LABEL":"Manage Competency Evaluation Cycles","DESCRIPTION":"Manage existing competency evaluation cycles and related reviews. Also permits viewing other employees' SOP competency scores; this is not Annual KPI Review Period management."},"CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD":{"LABEL":"Manage Annual KPI Review Periods","DESCRIPTION":"Configure, publish and close Annual KPI Review Periods, subject to readiness checks."},"CAN_MANAGE_COMPANY_KPI":{"LABEL":"Manage Company KPI Plans","DESCRIPTION":"Prepare and publish Company KPI plans and manage associated results within existing rules."},"CAN_MANAGE_DEPARTMENT_KPI":{"LABEL":"Prepare Department KPI Plans","DESCRIPTION":"Prepare and submit assigned Department KPI plans and enter associated results within your scope. Does not grant approval authority."},"CAN_APPROVE_DEPARTMENT_KPI":{"LABEL":"Review Department KPI Plans","DESCRIPTION":"Approve or return Department KPI plans and applicable revisions."},"CAN_REVIEW_INDIVIDUAL_KPI":{"LABEL":"Review Individual KPI Plans","DESCRIPTION":"Review direct reports' KPI plans and applicable revisions or assisted preparation, subject to reporting-line checks."},"CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE":{"LABEL":"Authorise Individual KPI Assistance","DESCRIPTION":"Approve or reject requests for a Superior to help prepare an employee's Individual KPI plan."},"CAN_MANAGE_KPI_AMENDMENTS":{"LABEL":"Manage KPI Revision Access","DESCRIPTION":"Manage amendment availability and cancel permitted unfinished Company or Department revisions."},"CAN_REVISE_COMPANY_KPI":{"LABEL":"Revise Company KPI Plans","DESCRIPTION":"Prepare and confirm Company KPI revisions under existing rules."},"CAN_PREPARE_DEPARTMENT_KPI_REVISION":{"LABEL":"Prepare Department KPI Revisions","DESCRIPTION":"Prepare requested Department revisions for the existing approval and confirmation process."},"CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS":{"LABEL":"Prepare Individual KPI Revisions","DESCRIPTION":"Request and prepare exceptional Individual KPI revisions for Superior approval."},"CAN_ENTER_COMPANY_KPI_RESULTS":{"LABEL":"Enter Company KPI Results","DESCRIPTION":"Enter Company results only for Review Periods where you also have an explicit result-entrant assignment. Does not allow plan editing."},"CAN_REVIEW_KPI_ASSESSMENT":{"LABEL":"Conduct Superior KPI Assessments","DESCRIPTION":"Review routed employee KPI submissions and record Superior ratings for employees within your authorised scope."},"CAN_MANAGE_ATTITUDE_CONFIGURATION":{"LABEL":"Manage Attitude Evaluation Setup","DESCRIPTION":"Create and publish evaluation criteria, rating definitions and Job Role formats. Initial assignment is available only where permitted."},"CAN_REVIEW_ATTITUDE_EVALUATION":{"LABEL":"Conduct Superior Attitude Evaluations","DESCRIPTION":"Review submitted employee Attitude Self-Assessments and record Superior ratings within your authorised scope."},"CAN_SUBMIT_ANNUAL_APPRAISAL":{"LABEL":"Prepare & Submit Appraisal Recommendations","DESCRIPTION":"Prepare and submit annual recommendations for direct reports. AI insights have separate eligibility safeguards; performance-profile viewing requires its own permission."},"CAN_REVIEW_ANNUAL_APPRAISAL":{"LABEL":"Review & Finalise Annual Appraisals","DESCRIPTION":"Approve, override or return eligible submitted recommendations, subject to existing self-review restrictions."},"CAN_VIEW_TEAM_PERFORMANCE":{"LABEL":"View Direct Reports' Performance","DESCRIPTION":"View current direct reports' performance. Does not grant assessment or appraisal actions."},"CAN_VIEW_DEPARTMENT_PERFORMANCE":{"LABEL":"View Department Performance","DESCRIPTION":"View performance for your authorised Department. Does not grant review actions."},"CAN_VIEW_ORGANISATION_PERFORMANCE":{"LABEL":"View Organisation Performance","DESCRIPTION":"View organisation performance, summary exports and authorised drill-downs, subject to privacy safeguards. Does not grant HR decision authority."}}}});
    translate.use('en');
    translate.setTranslation('en', {ACCESS_PERMISSIONS: {LEGACY_PRESERVED: 'Existing legacy permissions are preserved and are not changed here.'}}, true);
    fixture = TestBed.createComponent(PermissionSelectorComponent); page = fixture.componentInstance;
    fixture.componentRef.setInput('permissions', catalogue); fixture.detectChanges();
  });
  it('presents 29 visible permissions from the unchanged 32-entry catalogue', () => {
    expect(catalogue.length).toBe(32); expect(page.presented.length).toBe(29);
    for (const p of page.presented) {
      expect(p.label).not.toContain('auth.'); expect(p.label).not.toContain('ACCESS_PERMISSIONS');
      expect(p.description.length).toBeGreaterThan(20);
    }
    expect(page.presented.some(p => p.name === 'CAN_MANAGE_CAREER_PATHWAY')).toBeFalse();
    expect(page.presented.some(p => p.name === 'CAN_ENTER_COMPANY_KPI_RESULTS')).toBeFalse();
  });
  it('does not seed missing catalogue entries or auto-select Basic Employee Access', () => {
    fixture.componentRef.setInput('permissions', [catalogue[0]]); fixture.detectChanges();
    expect(page.presented.length).toBe(1); expect(page.values).toEqual([]);
    page.toggle(catalogue[0], true); expect(page.values).toEqual(['1']);
    page.toggle(catalogue[0], false); expect(page.values).toEqual([]);
  });
  it('keeps all selected IDs and their types through filtering and collapse', () => {
    const change = jasmine.createSpy('change'); page.registerOnChange(change);
    page.writeValue(['1', 14, '999']); page.query = 'no match'; fixture.detectChanges();
    expect(page.hasMatches).toBeFalse(); expect(page.values).toEqual(['1', 14, '999']);
    page.setExpanded('BASIC', false); page.query = '';
    expect(page.expanded.has('BASIC')).toBeTrue(); expect(change).not.toHaveBeenCalled();
    page.toggle(catalogue[1], true); expect(change).toHaveBeenCalledWith(['1', 14, '999', '2']);
  });
  it('preserves expansion across search and resets it on reopening', () => {
    page.writeValue(['1']); page.setExpanded('TRAINING', true); page.query = 'training';
    page.setExpanded('TRAINING', false); page.query = '';
    expect(page.expanded.has('TRAINING')).toBeTrue();
    page.writeValue(['14']); expect(page.expanded.has('BASIC')).toBeFalse();
    expect(page.expanded.has('PERIOD')).toBeTrue(); expect(page.query).toBe('');
  });
  it('uses numeric IDs for Grant Access without converting existing IDs', () => {
    page.idType = 'number'; page.writeValue([1]); page.toggle(catalogue[1], true);
    expect(page.values).toEqual([1, 2]); page.toggle(catalogue[0], false); expect(page.values).toEqual([2]);
  });
  it('honours disabled state and marks explicit checkbox changes touched', () => {
    const touched = jasmine.createSpy('touched'); page.registerOnTouched(touched);
    page.setDisabledState(true); page.toggle(catalogue[0], true); expect(page.values).toEqual([]);
    page.setDisabledState(false); page.toggle(catalogue[0], true); expect(touched).toHaveBeenCalledTimes(1);
  });
  it('hides both legacy entries from groups and search without changing the catalogue', () => {
    expect(page.members('TRAINING').length).toBe(4);
    for (const query of ['SOP', 'competency evaluations', 'evaluation cycles']) {
      page.query = query; fixture.detectChanges();
      expect(page.hasMatches).toBeFalse();
    }
    expect(page.presented.find(p => p.name === 'CAN_REVIEW_ATTITUDE_EVALUATION')!.label).toBe('Conduct Superior Attitude Evaluations');
    expect(page.presented.find(p => p.name === 'CAN_REVIEW_KPI_ASSESSMENT')!.label).toBe('Conduct Superior KPI Assessments');
  });
  it('keeps unknown API permissions in Other with plain text fallback', () => {
    const unknown = {id: 99, name: 'CAN_NEW_TASK', label: 'auth.new.task', description: 'auth.new.desc'};
    fixture.componentRef.setInput('permissions', [unknown]); fixture.detectChanges();
    expect(page.members('OTHER')[0].label).toBe('New task');
    fixture.componentRef.setInput('permissions', [{...unknown, label: '<script>bad</script>'}]);
    page.query = 'bad'; fixture.detectChanges();
    expect(permissionPresentation({...unknown, label: '<script>bad</script>'}, TestBed.inject(TranslateService)).label).toContain('<script>');
    expect(fixture.nativeElement.querySelector('script')).toBeNull();
  });
  it('updates only an explicitly clicked checkbox and keeps filtered selections', async () => {
    page.writeValue(['1', '999']); page.query = 'Manage competencies'; fixture.detectChanges(); await fixture.whenStable();
    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('.permission-option input[type=checkbox]');
    expect(checkbox).not.toBeNull(); checkbox.click(); fixture.detectChanges();
    expect(page.values).toContain('1'); expect(page.values).toContain('999'); expect(page.values.length).toBe(3);
    expect(page.count('TRAINING')).toBe(1);
    page.query = ''; fixture.detectChanges(); expect(page.values.length).toBe(3);
  });
  for (const hidden of [[], ['12'], ['13'], ['12', '13'], ['24'], ['12', '13', '24']]) {
    it('preserves hidden grants ' + JSON.stringify(hidden) + ' while changing visible selections', () => {
      const change = jasmine.createSpy('change'); page.registerOnChange(change);
      page.writeValue([...hidden, '1']); page.query = 'no match'; fixture.detectChanges();
      expect(page.visibleSelectedCount).toBe(1); expect(page.hasHiddenGrants).toBe(hidden.length > 0);
      expect(!!fixture.nativeElement.querySelector('.legacy-notice')).toBe(hidden.length > 0);
      page.query = ''; page.setExpanded('TRAINING', false);
      page.toggle(catalogue[1], true); expect(change).toHaveBeenCalledWith([...hidden, '1', '2']);
      page.toggle(catalogue[0], false); expect(change).toHaveBeenCalledWith([...hidden, '2']);
      page.writeValue([...hidden, '2']); expect(page.values).toEqual([...hidden, '2']);
      expect(page.visibleSelectedCount).toBe(1);
    });
  }
  it('supports hidden-only grants without displaying them or allowing selector changes to them', () => {
    page.writeValue(['12', 13]); fixture.detectChanges();
    expect(page.values).toEqual(['12', 13]); expect(page.visibleSelectedCount).toBe(0);
    expect(page.expanded.size).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Existing legacy permissions are preserved');
    page.toggle(catalogue[11], false); page.toggle(catalogue[12], false);
    expect(page.values).toEqual(['12', 13]);
    page.writeValue(null); expect(page.hasHiddenGrants).toBeFalse();
    page.idType = 'number'; page.toggle(catalogue[11], true); expect(page.values).toEqual([]);
    page.toggle(catalogue[23], true); expect(page.values).toEqual([]);
  });
  it('renders accessible search, Role impact, group counts and no bulk actions', () => {
    page.writeValue(['1']); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('input[type=search]')!.getAttribute('aria-label')).toBeTruthy();
    expect(root.textContent).toContain('Job Role'); expect(root.textContent).toContain('1 of 1 selected');
    expect(root.textContent).not.toContain('Select All');
    expect(root.querySelector('nz-collapse-panel')).not.toBeNull();
  });
});
