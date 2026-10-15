import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { NzModalService } from 'ng-zorro-antd/modal';
import { of } from 'rxjs';
import { KpiCentralResultsComponent } from './kpi-central-results.component';
import { KpiCentralResultService } from '../../services/kpi-central-result.service';
import { StaffService } from '../../services/staff.service';
import { AuthorityService } from '../../services/authority.service';
import { AuthService } from '../../services/auth.service';
import { CentralResult, CentralResultItem } from '../../models/kpi-central-result.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';

describe('Central KPI results', () => {
  let fixture: ComponentFixture<KpiCentralResultsComponent>;
  let page: KpiCentralResultsComponent;
  let api: jasmine.SpyObj<KpiCentralResultService>;
  let modal: jasmine.SpyObj<NzModalService>;
  let staffApi: jasmine.SpyObj<StaffService>;
  let authorityApi: jasmine.SpyObj<AuthorityService>;
  let auth: jasmine.SpyObj<AuthService>;
  const period: KpiPeriodContext = { id: 1, name: '2025 Review', status: 'OPEN', startDate: '2025-01-01',
    endDate: '2025-12-31', kpiSetupDeadline: '2025-01-31' };
  const item = (point: number | null = null, achievement: string | null = null): CentralResultItem => ({
    id: 7, kpi: { id: 17, name: 'Revenue', description: null, perspective: 'Financial', kra: 'Growth',
      target: '100 units', measurementUnit: 'Units', weightage: 100,
      scoringDefinitions: { 1: 'Very low', 2: 'Low', 3: 'On target', 4: 'Above target', 5: 'Excellent' } },
    actualAchievement: achievement, point, comment: null, evidence: []
  });
  const result = (status: CentralResult['status'] = 'DRAFT', point: number | null = null,
    achievement: string | null = null): CentralResult => ({
    id: 4, reviewPeriodId: 1, reviewPeriodName: '2025 Review', reviewPeriodStatus: 'OPEN', checkpointId: 8,
    reviewFrequency: 'MONTHLY', planVersionId: 2, planVersionNumber: 1, departmentId: null, departmentName: null,
    level: 'COMPANY', editionNumber: 1, status, previousEditionId: null, workingCorrectionId: null,
    correctionReason: null, createdAt: '2025-02-01T00:00:00Z', updatedAt: '2025-02-01T00:00:00Z',
    createdBy: 'owner', updatedBy: 'owner', publishedAt: status === 'PUBLISHED' ? '2025-02-02T00:00:00Z' : null,
    publishedBy: null, lockVersion: 1, items: [item(point, achievement)],
    publicationBlockers: point == null ? ['Select a Result Point for Revenue', 'Enter Actual Achievement for Revenue'] : [],
    allowedActions: status === 'DRAFT' ? ['UPDATE_DRAFT', 'UPLOAD_EVIDENCE'] : ['CREATE_CORRECTION']
  });

  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiCentralResultService>('api', ['periods', 'optionsForPeriod', 'checkpoints', 'list',
      'get', 'create', 'update', 'publish', 'correct', 'history', 'upload', 'download', 'removeEvidence',
      'delegates', 'assignDelegate', 'revokeDelegate']);
    api.periods.and.returnValue(of([period]));
    api.optionsForPeriod.and.returnValue(of([{ planVersionId: 2, versionNumber: 1, level: 'COMPANY',
      departmentId: null, departmentName: null, currentVersion: true }]));
    api.checkpoints.and.returnValue(of([{ id: 8, reviewFrequency: 'MONTHLY', sequenceNumber: 1,
      startDate: '2025-01-01', endDate: '2025-01-31', selfAssessmentDeadline: '2025-02-05',
      superiorAssessmentDeadline: '2025-02-10' }]));
    api.list.and.returnValue(of([]));
    api.create.and.callFake(() => of(result()));
    api.update.and.callFake((_id, request) => of({ ...result('DRAFT', request.items[0].point,
      request.items[0].actualAchievement), lockVersion: 2, publicationBlockers: [], allowedActions: ['UPDATE_DRAFT', 'PUBLISH'] }));
    api.publish.and.returnValue(of({ ...result('PUBLISHED', 3, '103 units'), lockVersion: 3 }));
    api.correct.and.returnValue(of({ ...result('DRAFT', 3, '103 units'), id: 5, editionNumber: 2,
      previousEditionId: 4, correctionReason: 'Correct the actual figure', lockVersion: 1,
      publicationBlockers: [], allowedActions: ['UPDATE_DRAFT', 'PUBLISH'] }));
    api.history.and.returnValue(of([]));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']);
    auth.hasRole.and.returnValue(false);
    staffApi = jasmine.createSpyObj<StaffService>('staff', ['getStaffOverview']);
    authorityApi = jasmine.createSpyObj<AuthorityService>('authority', ['getStaffAccessControlOverview']);
    TestBed.configureTestingModule({ imports: [KpiCentralResultsComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), provideRouter([]), { provide: KpiCentralResultService, useValue: api },
      { provide: StaffService, useValue: staffApi }, { provide: AuthorityService, useValue: authorityApi },
      { provide: AuthService, useValue: auth }, { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    await TestBed.compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { KPI_SCORING_GUIDE: { RATING: {
      3: { LABEL: 'Partially meets expectation' }
    } } });
    translate.use('en');
    fixture = TestBed.createComponent(KpiCentralResultsComponent);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads scoped options and checkpoints without creating a record', () => {
    expect(api.periods).toHaveBeenCalled();
    expect(api.optionsForPeriod).toHaveBeenCalledOnceWith(1);
    expect(api.checkpoints).toHaveBeenCalledOnceWith(2);
    expect(api.create).not.toHaveBeenCalled();
    expect(page.canStart).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('KPI_RESULTS.START_DRAFT');
  });

  it('saves an incomplete Draft but blocks publication and shows missing answers', () => {
    page.startDraft();
    expect(api.create).toHaveBeenCalledOnceWith({ planVersionId: 2, checkpointId: 8, expectedLockVersion: undefined, items: [] });
    expect(page.result?.status).toBe('DRAFT');
    expect(page.canPublish).toBeFalse();
    page.saveDraft();
    expect(api.update).toHaveBeenCalled();
    expect(page.publicationReasons).toEqual(['KPI_RESULTS.ACHIEVEMENT_MISSING', 'KPI_RESULTS.POINT_MISSING']);
    page.confirmPublish();
    expect(api.publish).not.toHaveBeenCalled();
    expect(page.showValidation).toBeTrue();
  });

  it('persists changed answers before publishing and makes them read-only', () => {
    page.startDraft();
    page.items[0].actualAchievement = '103 units'; page.items[0].point = 3;
    expect(page.dirty).toBeTrue(); expect(page.canPublish).toBeTrue();
    page.publish();
    expect(api.update).toHaveBeenCalled();
    expect(api.publish).toHaveBeenCalledOnceWith(4, 2);
    expect(page.result?.status).toBe('PUBLISHED');
    expect(page.editable).toBeFalse();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.result-item textarea').length).toBe(0);
  });

  it('saves pending answers before evidence upload and advances the lock version', () => {
    page.startDraft();
    page.items[0].actualAchievement = '103 units'; page.items[0].point = 3;
    const evidence = { id: 21, itemId: 7, originalFilename: 'proof.pdf', contentType: 'application/pdf',
      sizeBytes: 5, uploadedBy: 'owner', uploadedAt: '2025-02-02T00:00:00Z' };
    api.upload.and.returnValue(of({ evidence, resultLockVersion: 3 }));
    const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' });
    const input = { files: [file], value: 'proof.pdf' } as unknown as HTMLInputElement;
    page.attachEvidence(page.items[0], { target: input } as unknown as Event);
    expect(api.update).toHaveBeenCalled();
    expect(api.upload).toHaveBeenCalledOnceWith(7, 2, file);
    expect(page.result?.lockVersion).toBe(3);
    expect(page.items[0].evidence).toEqual([evidence]);
    expect(page.dirty).toBeFalse();
  });

  it('keeps the old edition visible until a correction is published, then selects the new edition', () => {
    api.list.and.returnValue(of([result('PUBLISHED', 3, '103 units')])); page.load();
    page.openCorrection(); page.correctionReason = 'Correct the actual figure'; page.createCorrection();
    expect(page.result?.id).toBe(5);
    expect(page.results.find(r => r.id === 4)?.status).toBe('PUBLISHED');
    api.update.and.callFake(() => of({ ...page.result!, allowedActions: ['UPDATE_DRAFT', 'PUBLISH'], lockVersion: 2 }));
    api.publish.and.returnValue(of({ ...page.result!, status: 'PUBLISHED', allowedActions: ['CREATE_CORRECTION'], lockVersion: 3 }));
    page.publish();
    expect(page.results.find(r => r.id === 4)?.status).toBe('SUPERSEDED');
    expect(page.result?.id).toBe(5);
    api.list.and.returnValue(of(page.results)); page.load();
    expect(page.result?.id).toBe(5);
  });

  it('protects unsaved answers when switching context', () => {
    page.startDraft(); page.items[0].comment = 'Keep this';
    page.changeLevel('DEPARTMENT');
    expect(page.level).toBe('COMPANY');
    page.refresh();
    expect(modal.confirm).toHaveBeenCalled();
    expect(page.items[0].comment).toBe('Keep this');
    expect(api.periods).toHaveBeenCalledTimes(1);
  });

  it('keeps a Closed-period result read-only', () => {
    api.periods.and.returnValue(of([{ ...period, status: 'CLOSED' }]));
    api.list.and.returnValue(of([{ ...result('DRAFT'), allowedActions: [], reviewPeriodStatus: 'CLOSED' }]));
    page.load(); fixture.detectChanges();
    expect(page.editable).toBeFalse(); expect(page.canStart).toBeFalse();
    expect(fixture.nativeElement.querySelector('.footer-actions')).toBeNull();
  });

  it('shows the shared rating label instead of the KPI threshold after point selection', () => {
    page.startDraft(); page.items[0].point = 3;
    fixture.detectChanges();
    const label = fixture.nativeElement.querySelector('.selected-point-label');
    expect(label.textContent).toContain('Partially meets expectation');
    expect(label.textContent).not.toContain('On target');
  });

  it('shows verified entrant permission states and links to the existing Access Control tab', () => {
    auth.hasRole.and.callFake(role => ['CAN_MANAGE_COMPANY_KPI', 'CAN_VIEW_ACCESS_CONTROL'].includes(role));
    api.delegates.and.returnValue(of([
      { id: 1, reviewPeriodId: 1, staffId: 'a', assignedBy: 'md', assignedAt: '2025-01-01', revokedBy: null, revokedAt: null },
      { id: 2, reviewPeriodId: 1, staffId: 'b', assignedBy: 'md', assignedAt: '2025-01-01', revokedBy: null, revokedAt: null }
    ]));
    staffApi.getStaffOverview.and.returnValue(of([
      { staffId: 'a', staffName: 'Alex', staffEmail: 'alex@example.test', roleId: 10, accountStatus: true },
      { staffId: 'b', staffName: 'Blair', staffEmail: 'blair@example.test', roleId: 11, accountStatus: true }
    ]));
    authorityApi.getStaffAccessControlOverview.and.returnValue(of({
      permissions: [{ id: 27, name: 'CAN_ENTER_COMPANY_KPI_RESULTS', label: 'Enter Company KPI Results', description: '' }],
      departments: [{ orgChartId: 1, orgChartName: 'UAT', orgChartDeleted: false, roles: [
        { roleId: 10, roleName: 'Entrant', authorityMap: { 27: true } },
        { roleId: 11, roleName: 'Viewer', authorityMap: { 27: false } }
      ] }]
    }));
    page.load(); fixture.detectChanges();
    expect(page.entrantPermission('a')).toBe('GRANTED');
    expect(page.entrantPermission('b')).toBe('MISSING');
    const link = fixture.nativeElement.querySelector('.permission-help a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/staff?tab=access-control');
    expect(fixture.nativeElement.querySelectorAll('.permission-status').length).toBe(2);
  });

  it('links to Access Control without inferring entrant permission for a restricted viewer', () => {
    auth.hasRole.and.callFake(role => role === 'CAN_MANAGE_COMPANY_KPI');
    api.delegates.and.returnValue(of([{ id: 1, reviewPeriodId: 1, staffId: 'a', assignedBy: 'md',
      assignedAt: '2025-01-01', revokedBy: null, revokedAt: null }]));
    staffApi.getStaffOverview.and.returnValue(of([{ staffId: 'a', staffName: 'Alex', staffEmail: 'alex@example.test',
      roleId: 10, accountStatus: true }]));
    page.load(); fixture.detectChanges();
    expect(authorityApi.getStaffAccessControlOverview).not.toHaveBeenCalled();
    expect(page.entrantPermission('a')).toBe('UNKNOWN');
    const link = fixture.nativeElement.querySelector('.permission-help a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/staff?tab=access-control');
  });

  it('separates actual achievement from the result point in history', async () => {
    api.list.and.returnValue(of([result('PUBLISHED', 3, '103 units')]));
    api.history.and.returnValue(of([result('PUBLISHED', 3, '103 units')]));
    page.load(); page.openHistory(); page.historyDetail = page.history[0];
    fixture.detectChanges(); await fixture.whenStable();
    const values = document.body.querySelectorAll('.history-values div');
    expect(values.length).toBe(2);
    expect(values[0].textContent).toContain('103 units');
    expect(values[1].textContent).toContain('Partially meets expectation');
    expect(values[0].textContent).not.toContain('Partially meets expectation');
  });
});
