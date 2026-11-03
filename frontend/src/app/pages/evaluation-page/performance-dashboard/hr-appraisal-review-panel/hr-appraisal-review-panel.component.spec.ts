import { of } from 'rxjs';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalService } from 'ng-zorro-antd/modal';
import { HrAppraisalReviewPanelComponent } from './hr-appraisal-review-panel.component';
import { AppraisalRecordService } from '../../../../services/appraisal-record.service';

describe('HR appraisal action validation', () => {
  let component: HrAppraisalReviewPanelComponent;
  let api: jasmine.SpyObj<AppraisalRecordService>;
  beforeEach(() => {
    api = jasmine.createSpyObj<AppraisalRecordService>('appraisals', ['returnForRevision', 'overrideAndApprove']);
    const modal = jasmine.createSpyObj<NzModalService>('modal', ['create']);
    const message = jasmine.createSpyObj<NzMessageService>('message', ['success', 'error']);
    component = new HrAppraisalReviewPanelComponent(api, modal, message);
    component.record = { id: 'record', staffId: 'employee', evaluationCycleId: 1, reviewPeriodYears: 1,
      decisionType: 'PROMOTION', status: 'PENDING_REVIEW', promotionManagerCategory: 'READY' };
    api.returnForRevision.and.returnValue(of({ ...component.record, status: 'RETURNED' }));
    api.overrideAndApprove.and.returnValue(of({ ...component.record, status: 'APPROVED' }));
  });
  it('reveals inline reason validation on Return without sending invalid data', () => {
    component.openReturnModal(); expect(component.validationVisible).toBeFalse();
    component.returnReason = '   '; component.submitReturn();
    expect(component.validationVisible).toBeTrue(); expect(api.returnForRevision).not.toHaveBeenCalled();
    component.returnReason = ' Explain the recommendation '; component.submitReturn();
    expect(api.returnForRevision).toHaveBeenCalledOnceWith('record', { hrReturnReason: 'Explain the recommendation' });
  });
  it('highlights missing override fields and the existing different-category rule', () => {
    component.openOverrideModal(); expect(component.overrideCategoryError('promotion')).toBeNull();
    component.submitOverride(); expect(api.overrideAndApprove).not.toHaveBeenCalled();
    expect(component.overrideCategoryError('promotion')).toBe('FORM_VALIDATION.CATEGORY_REQUIRED');
    expect(component.overrideReasonError('promotion')).toBeTrue();
    expect(component.overrideReasonError('salary')).toBeFalse();
    component.promotionOverrideCategory = 'READY';
    expect(component.overrideCategoryError('promotion')).toBe('FORM_VALIDATION.CATEGORY_DIFFERENT');
    component.promotionOverrideCategory = 'BORDERLINE'; component.promotionOverrideReason = 'Additional context';
    expect(component.overrideCategoryError('promotion')).toBeNull(); component.submitOverride();
    expect(api.overrideAndApprove).toHaveBeenCalled();
  });
});
