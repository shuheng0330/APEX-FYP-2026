import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';

import { StaffTabComponent } from './staff-tab.component';

describe('StaffTabComponent', () => {
  let component: StaffTabComponent;
  let fixture: ComponentFixture<StaffTabComponent>;
  let auth: jasmine.SpyObj<AuthService>;
  const tab = 'access-control';

  beforeEach(async () => {
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']);
    auth.hasRole.and.returnValue(false);
    await TestBed.configureTestingModule({
      imports: [StaffTabComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ tab }) } } }]
    })
      .overrideComponent(StaffTabComponent, { set: { template: '', imports: [] } })
      .compileComponents();

    fixture = TestBed.createComponent(StaffTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('opens Access Control for an authorised deep link', () => {
    auth.hasRole.and.callFake(role => role === 'CAN_VIEW_ACCESS_CONTROL');
    component.ngOnInit();
    expect(component.selectedTabIndex).toBe(1);
  });

  it('does not open Access Control for an unauthorised deep link', () => {
    expect(component.selectedTabIndex).toBe(0);
    expect(component.accessControlUnavailable).toBeTrue();
  });
});
