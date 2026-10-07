import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { PlusOutline, CloseCircleFill, DownOutline, CheckCircleFill, CloseCircleOutline, ExclamationCircleFill } from '@ant-design/icons-angular/icons';
import { AddRoleComponent } from './add-role.component';
import { RoleService } from '../../../../services/role.service';
import { OrgChartService } from '../../../../services/orgChart.service';
import { JobScopeService } from '../../../../services/jobScope.service';
import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';

describe('AddRoleComponent Employee Level', () => {
  let component: AddRoleComponent;
  let fixture: ComponentFixture<AddRoleComponent>;
  let roles: jasmine.SpyObj<RoleService>;

  beforeEach(async () => {
    roles = jasmine.createSpyObj<RoleService>('RoleService', ['getEmployeeLevels', 'createRole']);
    roles.getEmployeeLevels.and.returnValue(of([
      { id: 4, code: 'EXECUTIVE', name: 'Executive', displayOrder: 4,
        defaultCompanyKpiWeight: 15, defaultDepartmentKpiWeight: 25, defaultIndividualKpiWeight: 60 }
    ]));
    roles.createRole.and.returnValue(of({ message: 'Saved', createdRole: {
      id: 10, name: 'Executive', description: '', visible: true, deleted: false,
      createdBy: '', createdAt: '', updatedBy: '', updatedAt: '',
      orgChart: { id: 100, name: 'Sales', isRoot: false, deleted: false,
        createdBy: '', createdAt: '', updatedBy: '', updatedAt: '' },
      employeeLevelId: 4
    } }));
    await TestBed.configureTestingModule({
      imports: [AddRoleComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: NZ_ICONS, useValue: [PlusOutline, CloseCircleFill, DownOutline, CheckCircleFill, CloseCircleOutline, ExclamationCircleFill] },
        { provide: RoleService, useValue: roles },
        { provide: OrgChartService, useValue: { getAllOrgChart: () => of([{ id: 100, name: 'Sales' }]) } },
        { provide: JobScopeService, useValue: { getAllJobScopes: () => of([]) } },
        { provide: AuthService, useValue: { hasRole: () => true } },
        { provide: LoadingService, useValue: { show: () => {}, hide: () => {} } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AddRoleComponent);
    component = fixture.componentInstance;
    component.initialize();
    fixture.detectChanges();
  });

  it('loads Employee Level options without assuming a role-name mapping', () => {
    expect(roles.getEmployeeLevels).toHaveBeenCalled();
    expect(component.employeeLevels[0].name).toBe('Executive');
    expect(component.validateForm.controls.employeeLevel.value).toBeNull();
  });

  it('requires a level before submitting and sends the selected ID', () => {
    component.validateForm.controls.employeeLevel.reset();
    component.validateForm.patchValue({ department: 100, role: 'Executive' });
    component.submit();
    expect(roles.createRole).not.toHaveBeenCalled();
    component.validateForm.controls.employeeLevel.setValue(4);
    component.submit();
    expect(roles.createRole).toHaveBeenCalledWith(jasmine.objectContaining({ employeeLevelId: 4 }));
  });

  it('clears the selection when the drawer closes', () => {
    component.validateForm.controls.employeeLevel.setValue(4);
    component.close();
    expect(component.validateForm.controls.employeeLevel.value).toBeNull();
  });

});
