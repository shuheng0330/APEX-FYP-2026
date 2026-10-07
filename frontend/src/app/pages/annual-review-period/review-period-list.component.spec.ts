import { TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NzMessageService } from 'ng-zorro-antd/message';
import { ReviewPeriodListComponent } from './review-period-list.component';
import { AnnualReviewPeriodService } from '../../services/annual-review-period.service';
import { savedReview } from './review-period.fixtures.spec';

describe('Annual review period list', () => {
  let component: ReviewPeriodListComponent;
  let api: jasmine.SpyObj<AnnualReviewPeriodService>;
  let modal: jasmine.SpyObj<NzModalService>;
  beforeEach(() => {
    api = jasmine.createSpyObj<AnnualReviewPeriodService>('api', ['list', 'delete']);
    api.list.and.returnValue(of([savedReview(), { ...savedReview(), id: 30, name: 'Historical', status: 'CLOSED' }])); api.delete.and.returnValue(of(undefined));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [ReviewPeriodListComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), provideRouter([]), { provide: AnnualReviewPeriodService, useValue: api },
      { provide: NzModalService, useValue: modal }, { provide: NzMessageService, useValue: { success: () => {} } }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    component = TestBed.createComponent(ReviewPeriodListComponent).componentInstance; component.ngOnInit();
  });
  it('filters by name and lifecycle status', () => {
    component.query = '2028'; expect(component.filtered.length).toBe(1);
    component.status = 'CLOSED'; expect(component.filtered.length).toBe(0);
    component.query = ''; expect(component.filtered[0].name).toBe('Historical');
  });
  it('requires confirmation before deleting an editable period', () => {
    component.confirmDelete(savedReview()); expect(modal.confirm).toHaveBeenCalled(); expect(api.delete).not.toHaveBeenCalled();
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
    expect(api.delete).toHaveBeenCalledWith(29);
  });
  it('does not offer deletion for Open or Closed records', () => {
    component.confirmDelete({ ...savedReview(), status: 'OPEN' }); component.confirmDelete({ ...savedReview(), status: 'CLOSED' });
    expect(modal.confirm).not.toHaveBeenCalled(); expect(api.delete).not.toHaveBeenCalled();
  });
});
