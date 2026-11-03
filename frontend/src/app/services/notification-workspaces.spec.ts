import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { KpiPlanService } from './kpi-plan.service';
import { DepartmentKpiPlanService } from './department-kpi-plan.service';
import { IndividualKpiPlanService } from './individual-kpi-plan.service';
import { KpiAssessmentService } from './kpi-assessment.service';
import { AttitudeAssessmentService } from './attitude-assessment.service';
import { KpiAssistanceService } from './kpi-assistance.service';
import { KpiRevisionService } from './kpi-revision.service';
import { KpiCentralResultService } from './kpi-central-result.service';
import { TeamReviewPeriodService } from './team-review-period.service';
import { AnnualAppraisalService } from './annual-appraisal.service';
import { StaffService } from './staff.service';
import { AuthorityService } from './authority.service';
import { MyAssessmentsComponent } from '../pages/kpi-assessment/my-assessments.component';
import { KpiAssessmentReviewsComponent } from '../pages/kpi-assessment/kpi-assessment-reviews.component';
import { AttitudeAssessmentReviewsComponent } from '../pages/kpi-assessment/attitude-assessment-reviews.component';
import { CompanyKpiPlanComponent } from '../pages/kpi-plan/company-kpi-plan.component';
import { DepartmentKpiPlanComponent } from '../pages/kpi-plan/department-kpi-plan.component';
import { MyKpiPlanComponent } from '../pages/kpi-plan/my-kpi-plan.component';
import { TeamReviewsComponent } from '../pages/kpi-plan/team-reviews.component';
import { DepartmentKpiReviewComponent } from '../pages/kpi-plan/department-kpi-review.component';
import { KpiAssistanceComponent } from '../pages/kpi-plan/kpi-assistance.component';
import { KpiRevisionsComponent } from '../pages/kpi-plan/kpi-revisions.component';
import { KpiRevisionWorkspaceComponent } from '../pages/kpi-plan/kpi-revision-workspace.component';
import { KpiCentralResultsComponent } from '../pages/kpi-plan/kpi-central-results.component';
import { AnnualAppraisalsComponent } from '../pages/annual-appraisal/annual-appraisals.component';
import { revisionPlan, revisionFixture } from '../pages/kpi-plan/kpi-revision.test-data';
import { assistanceCase } from '../pages/kpi-plan/kpi-assistance.testing';
import { period } from '../pages/annual-appraisal/annual-appraisal-test-fixtures';
import { KpiAssessment, KpiAssessmentCheckpoint, KpiAssessmentReview } from '../models/kpi-assessment.model';
import { AttitudeAssessment, AttitudeAssessmentReview } from '../models/attitude-assessment.model';

describe('Notification workspace selections use existing read APIs', () => {
  let params: Partial<Record<'reviewPeriodId' | 'tab' | 'planId' | 'checkpointId' | 'assessmentId' | 'attitudeAssessmentId' |
    'assistanceId' | 'revisionId' | 'planVersionId' | 'view', string>>;
  let grants: string[];
  let individual: jasmine.SpyObj<IndividualKpiPlanService>; let company: jasmine.SpyObj<KpiPlanService>;
  let department: jasmine.SpyObj<DepartmentKpiPlanService>; let kpi: jasmine.SpyObj<KpiAssessmentService>;
  let attitude: jasmine.SpyObj<AttitudeAssessmentService>; let assistance: jasmine.SpyObj<KpiAssistanceService>;
  let revisions: jasmine.SpyObj<KpiRevisionService>; let central: jasmine.SpyObj<KpiCentralResultService>;
  const plan = revisionPlan({ id: 10, level: 'INDIVIDUAL', reviewPeriodId: 2, status: 'PENDING_APPROVAL' });
  const checkpoint: KpiAssessmentCheckpoint = { id: 22, reviewFrequency: 'QUARTERLY', sequenceNumber: 2,
    startDate: '2026-04-01', endDate: '2026-06-30', availableFrom: '2026-07-01', selfAssessmentDeadline: '2026-07-07',
    superiorAssessmentDeadline: '2026-07-14', available: true, overdue: false, assessmentId: 7, assessmentStatus: 'REVIEWED' };
  const assessment: KpiAssessment = { id: 7, participantId: 3, employeeName: 'Bob', reviewPeriodId: 2,
    reviewPeriodName: '2026', reviewPeriodStatus: 'OPEN', checkpoint, status: 'REVIEWED', assessmentModel: 'CENTRAL_RESULTS',
    items: [], pendingResultLevels: [], missingLevels: [], kpiAllocation: null, submissionBlockers: [], canSaveDraft: false,
    canSubmit: false, overdue: false, createdAt: null, updatedAt: null, submittedAt: null, submittedBy: null,
    submittedToSuperiorId: null, submittedLate: null, reviewedAt: null, reviewedBy: null, reviewedLate: null, checkpointScore: 0 };
  const review: KpiAssessmentReview = { ...assessment, id: 7, status: 'REVIEWED', submittedAt: '2026-07-01', submittedLate: false,
    employeeId: 'bob', roleName: 'Developer', departmentName: 'IT', canReview: false, superiorOverdue: false };
  const attitudeRecord: AttitudeAssessment = { id: 9, participantId: 3, employeeName: 'Bob', roleName: 'Developer', departmentName: 'IT',
    reviewPeriodId: 2, reviewPeriodName: '2026', reviewPeriodStatus: 'OPEN', configurationId: 1, configurationName: 'Values',
    evaluationFormat: 'OTHERS', status: 'REVIEWED', createdAt: null, updatedAt: null, submittedAt: null, submittedBy: null,
    submittedToSuperiorId: null, submittedLate: null, availableFrom: '2026-10-01', openingPending: false,
    selfAssessmentDeadline: null, superiorEvaluationDeadline: null, available: true, availabilityTitle: null, availabilityMessage: null,
    canSaveDraft: false, canSubmit: false, overdue: false, submissionBlockers: [], ratingDefinitions: [], items: [] };
  const attitudeReview: AttitudeAssessmentReview = { ...attitudeRecord, id: 9, employeeId: 'bob', status: 'REVIEWED',
    evaluationFormat: 'OTHERS', reviewedAt: null, reviewedLate: null, attitudeScore: 80, canReview: false,
    superiorDraftSaved: false, superiorOverdue: false };
  beforeEach(() => {
    params = { reviewPeriodId: '2' }; grants = [];
    individual = jasmine.createSpyObj('individual', ['periods', 'mine', 'assigned', 'reviews', 'get', 'create']);
    individual.periods.and.returnValue(of([period])); individual.mine.and.returnValue(of([plan])); individual.assigned.and.returnValue(of([]));
    individual.reviews.and.returnValue(of([plan])); individual.get.and.returnValue(of(plan));
    company = jasmine.createSpyObj('company', ['periods', 'list']); company.periods.and.returnValue(of([period])); company.list.and.returnValue(of([]));
    department = jasmine.createSpyObj('department', ['periods', 'departments', 'list', 'get']);
    department.periods.and.returnValue(of([period])); department.departments.and.returnValue(of([{ id: 4, name: 'IT' }]));
    department.list.and.returnValue(of([{ ...plan, level: 'DEPARTMENT', departmentId: 4 }]));
    department.get.and.returnValue(of({ ...plan, level: 'DEPARTMENT', departmentId: 4 }));
    kpi = jasmine.createSpyObj('kpi', ['periods', 'checkpoints', 'mine', 'reviews', 'get', 'create', 'scoreBreakdown']);
    kpi.periods.and.returnValue(of([period])); kpi.checkpoints.and.returnValue(of([checkpoint])); kpi.mine.and.returnValue(of(assessment));
    kpi.reviews.and.returnValue(of([review])); kpi.get.and.returnValue(of(assessment)); kpi.scoreBreakdown.and.returnValue(of({ assessmentId: 7, checkpointScore: 0, levels: [] }));
    attitude = jasmine.createSpyObj('attitude', ['mine', 'reviews', 'get', 'create']); attitude.mine.and.returnValue(of(attitudeRecord));
    attitude.reviews.and.returnValue(of([attitudeReview])); attitude.get.and.returnValue(of(attitudeRecord));
    assistance = jasmine.createSpyObj('assistance', ['list', 'employees', 'get']);
    const request = { ...assistanceCase('AUTHORIZED'), reviewPeriodId: 2 };
    assistance.list.and.returnValue(of([request])); assistance.employees.and.returnValue(of([])); assistance.get.and.returnValue(of(request));
    revisions = jasmine.createSpyObj('revisions', ['periods', 'plans', 'list', 'get']);
    revisions.periods.and.returnValue(of([period])); revisions.plans.and.returnValue(of([]));
    const revision = revisionFixture({ id: 12, reviewPeriodId: 2, baseVersionId: null, status: 'RETURNED' });
    revisions.list.and.returnValue(of([revision])); revisions.get.and.returnValue(of(revision));
    central = jasmine.createSpyObj('central', ['periods', 'optionsForPeriod', 'list', 'checkpoints', 'create']);
    central.periods.and.returnValue(of([period])); central.optionsForPeriod.and.returnValue(of([
      { planVersionId: 40, versionNumber: 1, level: 'DEPARTMENT', departmentId: 4, departmentName: 'IT', currentVersion: true },
      { planVersionId: 41, versionNumber: 2, level: 'COMPANY', departmentId: null, departmentName: null, currentVersion: true }
    ])); central.list.and.returnValue(of([])); central.checkpoints.and.returnValue(of([checkpoint]));
  });
  async function start<T>(component: Type<T>, hr = false) {
    const queryParamMap = convertToParamMap(params);
    TestBed.configureTestingModule({ imports: [component, TranslateModule.forRoot()], providers: [provideRouter([]), provideNoopAnimations(),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap, data: { hrReview: hr } } } },
      { provide: AuthService, useValue: { userId: 'superior', hasRole: (p: string) => grants.includes(p) } },
      { provide: KpiPlanService, useValue: company }, { provide: DepartmentKpiPlanService, useValue: department },
      { provide: IndividualKpiPlanService, useValue: individual }, { provide: KpiAssessmentService, useValue: kpi },
      { provide: AttitudeAssessmentService, useValue: attitude }, { provide: KpiAssistanceService, useValue: assistance },
      { provide: KpiRevisionService, useValue: revisions }, { provide: KpiCentralResultService, useValue: central },
      { provide: StaffService, useValue: {} }, { provide: AuthorityService, useValue: {} },
      { provide: TeamReviewPeriodService, useValue: { periods: () => of({ periods: [period], defaultPeriodId: 2 }) } },
      { provide: AnnualAppraisalService, useValue: { periods: () => of([period]), team: () => of([]), reviews: () => of([]) } }
    ] });
    TestBed.overrideComponent(component, { set: { template: '', imports: [] } });
    TestBed.overrideProvider(NzModalService, { useValue: jasmine.createSpyObj('modal', ['confirm']) });
    await TestBed.compileComponents(); const fixture = TestBed.createComponent(component); fixture.detectChanges(); return fixture.componentInstance;
  }
  it('selects an explicit Company setup period and rejects an unavailable one', async () => {
    params.reviewPeriodId = '99'; const page = await start(CompanyKpiPlanComponent);
    expect(page.target.unavailable).toBeTrue(); expect(page.selectedId).toBeNull(); expect(page.plan).toBeNull();
  });
  it('selects the actual Department plan/Department without choosing a different one', async () => {
    params.planId = '10'; const page = await start(DepartmentKpiPlanComponent);
    expect(page.plan?.id).toBe(10); expect(page.departmentId).toBe(4);
  });
  it('accepts an assigned Company plan link in My KPI Plan, not just an Individual plan', async () => {
    params.planId = '11'; individual.assigned.and.returnValue(of([{ ...plan, id: 11, level: 'COMPANY' }]));
    const page = await start(MyKpiPlanComponent); expect(page.target.unavailable).toBeFalse(); expect(page.assignedPlans[0].id).toBe(11);
    expect(individual.create).not.toHaveBeenCalled();
  });
  it('opens a selected KPI assessment and score breakdown read-only, retaining a legitimate zero', async () => {
    params = { ...params, tab: 'kpi', checkpointId: '22', assessmentId: '7', view: 'breakdown' };
    const page = await start(MyAssessmentsComponent); await Promise.resolve();
    expect(page.checkpointId).toBe(22); expect(page.assessment?.checkpointScore).toBe(0); expect(page.readonly).toBeTrue();
    expect(page.breakdownVisible).toBeTrue(); expect(kpi.scoreBreakdown).toHaveBeenCalledOnceWith(7); expect(kpi.create).not.toHaveBeenCalled();
  });
  it('does not substitute another checkpoint when the target is unavailable', async () => {
    params.checkpointId = '99'; const page = await start(MyAssessmentsComponent);
    expect(page.target.unavailable).toBeTrue(); expect(page.assessment).toBeNull(); expect(kpi.mine).not.toHaveBeenCalled();
  });
  it('selects the Attitude tab independently and never creates a persisted Draft', async () => {
    params.tab = 'attitude'; const page = await start(MyAssessmentsComponent);
    expect(page.selectedTab).toBe(1); expect(page.attitudeVisited).toBeTrue(); expect(attitude.create).not.toHaveBeenCalled();
  });
  it('opens a completed KPI review from the authorised queue instead of the pending filter', async () => {
    params.assessmentId = '7'; const page = await start(KpiAssessmentReviewsComponent);
    expect(page.filter).toBe('REVIEWED'); expect(page.selected?.id).toBe(7); expect(page.drawerVisible).toBeTrue(); expect(page.readonly).toBeTrue();
  });
  it('opens a completed Attitude review from its authorised queue', async () => {
    params.attitudeAssessmentId = '9'; const page = await start(AttitudeAssessmentReviewsComponent);
    expect(page.filter).toBe('REVIEWED'); expect(page.selected?.id).toBe(9); expect(page.drawerVisible).toBeTrue();
  });
  it('opens the correct Individual plan review after loading the scoped queue', async () => {
    params = { ...params, tab: 'reviews', planId: '10' }; grants = ['CAN_REVIEW_INDIVIDUAL_KPI'];
    const page = await start(TeamReviewsComponent); expect(page.selected?.id).toBe(10); expect(page.periodId).toBe(2);
    expect(individual.get).toHaveBeenCalledOnceWith(10); expect(individual.create).not.toHaveBeenCalled();
  });
  it('keeps inaccessible Individual plans out of the drawer', async () => {
    params = { ...params, tab: 'reviews', planId: '999' }; grants = ['CAN_REVIEW_INDIVIDUAL_KPI'];
    const page = await start(TeamReviewsComponent); expect(page.target.unavailable).toBeTrue(); expect(page.selected).toBeNull();
    expect(individual.get).not.toHaveBeenCalled();
  });
  it('opens Department approval details only from the authorised plan list', async () => {
    params.planId = '10'; const page = await start(DepartmentKpiReviewComponent); expect(page.selected?.id).toBe(10);
    expect(department.get).toHaveBeenCalledOnceWith(10);
  });
  it('opens an assistance outcome without starting an assisted Draft', async () => {
    params.assistanceId = '8'; grants = ['CAN_REVIEW_INDIVIDUAL_KPI']; const page = await start(KpiAssistanceComponent);
    expect(page.filter).toBe('AUTHORIZED'); expect(page.selected?.id).toBe(8); expect(page.drawerVisible).toBeTrue(); expect(page.workspace).toBeNull();
  });
  it('locates a revision before handing off to the existing read-only loader', async () => {
    params.revisionId = '12'; const page = await start(KpiRevisionsComponent);
    expect(page.periodId).toBe(2); expect(page.filter).toBe('RETURNED');
    const workspace = jasmine.createSpyObj<KpiRevisionWorkspaceComponent>('workspace', ['open']); page.linkedWorkspace = workspace;
    await Promise.resolve();
    expect(workspace.open).toHaveBeenCalledOnceWith(12);
  });
  it('selects the linked Company version even when Department is the first available scope', async () => {
    params = { ...params, planVersionId: '41', checkpointId: '22' }; const page = await start(KpiCentralResultsComponent);
    expect(page.level).toBe('COMPANY'); expect(page.versionId).toBe(41); expect(page.checkpointId).toBe(22);
    expect(page.result).toBeNull(); expect(central.create).not.toHaveBeenCalled();
  });
  it('rejects a version not available to the logged-in result entrant', async () => {
    params.planVersionId = '999'; const page = await start(KpiCentralResultsComponent);
    expect(page.target.unavailable).toBeTrue(); expect(page.result).toBeNull(); expect(central.create).not.toHaveBeenCalled();
  });
  it('selects the period in grouped HR queue reminders without creating appraisals', async () => {
    grants = ['CAN_REVIEW_ANNUAL_APPRAISAL']; const page = await start(AnnualAppraisalsComponent, true);
    expect(page.periodId).toBe(2); expect(page.filter).toBe('PENDING_REVIEW');
  });
});
