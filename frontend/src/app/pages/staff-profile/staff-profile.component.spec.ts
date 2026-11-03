import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { TrainingRecordComponent } from '../training/training-record/training-record.component';
import { GeneralInformationComponent } from './general-information/general-information.component';

import { StaffProfileComponent } from './staff-profile.component';

@Component({ selector: 'app-general-information', template: '' })
class GeneralInformationStubComponent {}

@Component({ selector: 'app-training-record', template: '' })
class TrainingRecordStubComponent {
  @Input() staffId: string | null = null;
}

describe('StaffProfileComponent', () => {
  let component: StaffProfileComponent;
  let fixture: ComponentFixture<StaffProfileComponent>;
  let queryParams: BehaviorSubject<Params>;

  beforeEach(async () => {
    queryParams = new BehaviorSubject<Params>({});
    await TestBed.configureTestingModule({
      imports: [StaffProfileComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParams } },
        { provide: AuthService, useValue: { hasRole: () => false } }
      ]
    })
    .overrideComponent(StaffProfileComponent, {
      remove: { imports: [GeneralInformationComponent, TrainingRecordComponent] },
      add: { imports: [GeneralInformationStubComponent, TrainingRecordStubComponent] }
    })
    .compileComponents();

    fixture = TestBed.createComponent(StaffProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('includes Training History without restoring the retired appraisal panel', () => {
    expect(fixture.nativeElement.textContent).toContain('PAGE.PROFILE.TRAINING_HISTORY.TITLE');
    expect(fixture.nativeElement.querySelector('app-appraisal-score')).toBeNull();
  });

  it('preserves the selected staff context and clears it when returning to My Profile', () => {
    queryParams.next({ staffId: 'employee-123' });
    expect(component.profileStaffId).toBe('employee-123');
    expect(component.selectedTabIndex).toBe(0);
    queryParams.next({});
    expect(component.profileStaffId).toBeNull();
  });
});
