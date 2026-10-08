import { of } from 'rxjs';
import { NzMessageService } from 'ng-zorro-antd/message';
import { ManagerAppraisalPanelComponent } from './manager-appraisal-panel.component';
import { AppraisalRecordService } from '../../../../services/appraisal-record.service';
import { EvaluationCycleService } from '../../../../services/evaluation-cycle.service';
import { AppraisalRecordDto } from '../../../../models/appraisal-record.model';

describe('Manager appraisal revision validation', () => {
  let component: ManagerAppraisalPanelComponent;
  let api: jasmine.SpyObj<AppraisalRecordService>;
  let record: AppraisalRecordDto;
  beforeEach(() => {
    api = jasmine.createSpyObj<AppraisalRecordService>('appraisals', ['getByStaff', 'saveDraft', 'submit']);
    const cycles = jasmine.createSpyObj<EvaluationCycleService>('cycles', ['getHistory']);
    const messages = jasmine.createSpyObj<NzMessageService>('messages', ['success', 'error']);
    record = { id: 'record', staffId: 'employee', evaluationCycleId: 1, reviewPeriodYears: 1,
      decisionType: 'PROMOTION', managerComment: 'Original recommendation', status: 'RETURNED',
      promotionSystemCategory: 'READY', promotionManagerCategory: 'READY', revisionRequired: true,
      hrReturnReason: 'Explain the recommendation' };
    api.getByStaff.and.returnValue(of([record]));
    api.saveDraft.and.returnValue(of({ ...record, status: 'DRAFT' }));
    api.submit.and.returnValue(of({ ...record, revisionRequired: false, status: 'PENDING_REVIEW' }));
    cycles.getHistory.and.returnValue(of([{ id: 1, startDate: '2027-01-01', endDate: '2027-12-31', status: 'CLOSED' }]));
    component = new ManagerAppraisalPanelComponent(api, cycles, messages);
    component.staffId = 'employee'; component.loadPanelData();
  });
  it('blocks an unchanged returned recommendation and a no-op Draft save', () => {
    expect(component.canSubmit).toBeFalse(); component.submitToHr();
    expect(api.saveDraft).not.toHaveBeenCalled(); expect(api.submit).not.toHaveBeenCalled();
    component.managerComment = ' Original recommendation ';
    expect(component.canSubmit).toBeFalse(); component.saveDraft();
    expect(component.status).toBe('DRAFT'); expect(component.canSubmit).toBeFalse();
    component.loadPanelData(); expect(component.canSubmit).toBeFalse();
  });
  it('saves a changed recommendation before resubmitting', () => {
    component.managerComment = 'A clarified recommendation';
    api.saveDraft.and.callFake(request => of({ ...request, id: 'record', revisionRequired: false, status: 'DRAFT' }));
    expect(component.canSubmit).toBeTrue(); component.submitToHr();
    expect(api.saveDraft.calls.mostRecent().args[0].managerComment).toBe('A clarified recommendation');
    expect(api.submit).toHaveBeenCalledOnceWith('record'); expect(component.isReadOnly).toBeTrue();
  });
  it('does not count recalculated readiness or system categories as an edit', () => {
    component.promotionCard.readinessScore = 90;
    component.promotionCard.systemCategory = 'BORDERLINE'; component.promotionCard.managerCategory = 'BORDERLINE';
    expect(component.canSubmit).toBeFalse();
    component.promotionCard.overrideEnabled = true; component.promotionCard.managerCategory = 'READY';
    component.promotionCard.overrideReason = 'Relevant additional evidence'; expect(component.canSubmit).toBeTrue();
  });
  it('leaves ordinary Draft and first-time submission available', () => {
    api.getByStaff.and.returnValue(of([{ ...record, status: 'DRAFT', revisionRequired: false }]));
    component.loadPanelData(); expect(component.canSubmit).toBeTrue();
    api.getByStaff.and.returnValue(of([])); component.loadPanelData();
    component.decisionType = 'PROMOTION'; component.managerComment = 'First recommendation';
    expect(component.canSubmit).toBeTrue();
  });
});
