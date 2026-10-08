import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of } from 'rxjs';
import { KpiReviewComponent } from './kpi-review.component';
import { AuthService } from '../../services/auth.service';
import { DepartmentKpiPlanService } from '../../services/department-kpi-plan.service';
import { KpiAssistanceService } from '../../services/kpi-assistance.service';

describe('Permission-driven KPI Review tabs', () => {
  let fixture: ComponentFixture<KpiReviewComponent>; let component: KpiReviewComponent;
  let permissions: string[]; let department: jasmine.SpyObj<DepartmentKpiPlanService>; let assistance: jasmine.SpyObj<KpiAssistanceService>;
  beforeEach(async () => {
    permissions = [];
    const auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole'], { userId: 'hr' }); auth.hasRole.and.callFake(value => permissions.includes(value));
    department = jasmine.createSpyObj<DepartmentKpiPlanService>('department', ['list']); department.list.and.returnValue(of([]));
    assistance = jasmine.createSpyObj<KpiAssistanceService>('assistance', ['list']); assistance.list.and.returnValue(of([]));
    TestBed.configureTestingModule({ imports: [KpiReviewComponent, TranslateModule.forRoot()], providers: [provideNoopAnimations(),
      { provide: AuthService, useValue: auth }, { provide: DepartmentKpiPlanService, useValue: department }, { provide: KpiAssistanceService, useValue: assistance },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }] });
    TestBed.overrideProvider(NzModalService, { useValue: jasmine.createSpyObj<NzModalService>('modal', ['confirm']) }); await TestBed.compileComponents();
  });
  function start() { fixture = TestBed.createComponent(KpiReviewComponent); component = fixture.componentInstance; fixture.detectChanges(); }
  it('opens the HR tab without calling the Department API for HR-only users', () => {
    permissions = ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start(); expect(component.tab).toBe('assistance');
    expect(assistance.list).toHaveBeenCalledTimes(1); expect(department.list).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs > button').length).toBe(1);
    component.selectTab('department'); expect(component.tab).toBe('assistance');
  });
  it('keeps Department approval separate from HR assistance permission', () => {
    permissions = ['CAN_APPROVE_DEPARTMENT_KPI']; start(); expect(component.tab).toBe('department'); expect(department.list).toHaveBeenCalledTimes(1);
    expect(assistance.list).not.toHaveBeenCalled(); component.selectTab('assistance'); expect(component.tab).toBe('department');
  });
  it('allows both tabs only when both permissions are held', () => {
    permissions = ['CAN_APPROVE_DEPARTMENT_KPI', 'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start();
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs > button').length).toBe(2);
    component.selectTab('assistance'); fixture.detectChanges(); expect(assistance.list).toHaveBeenCalledTimes(1);
    component.assistanceWorkspace!.drawerVisible = true; component.selectTab('department'); expect(component.tab).toBe('assistance');
  });
  it('does not infer either authority from Super Admin or HR Role names', () => {
    permissions = ['Super Admin', 'HR']; start(); expect(department.list).not.toHaveBeenCalled(); expect(assistance.list).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs > button').length).toBe(0);
  });
});
