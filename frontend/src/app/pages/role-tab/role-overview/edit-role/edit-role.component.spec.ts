import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { PlusOutline, CloseCircleFill, DownOutline, CheckCircleFill, CloseCircleOutline, ExclamationCircleFill } from '@ant-design/icons-angular/icons';
import { EditRoleComponent } from './edit-role.component';
import { RoleService } from '../../../../services/role.service';
import { OrgChartService } from '../../../../services/orgChart.service';
import { JobScopeService } from '../../../../services/jobScope.service';
import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';

describe('EditRoleComponent Employee Level', () => {
  let component: EditRoleComponent;
  let fixture: ComponentFixture<EditRoleComponent>;
  let roles: jasmine.SpyObj<RoleService>;

  beforeEach(async () => {
    roles = jasmine.createSpyObj<RoleService>('RoleService', ['getEmployeeLevels', 'updateRole']);
    roles.getEmployeeLevels.and.returnValue(of([
      { id: 4, code: 'EXECUTIVE', name: 'Executive', displayOrder: 4,
        defaultCompanyKpiWeight: 15, defaultDepartmentKpiWeight: 25, defaultIndividualKpiWeight: 60 }
    ]));
    roles.updateRole.and.returnValue(of({ message: 'Saved', updatedRole: {
      id: 10, name: 'Executive', description: '', visible: true, deleted: false,
      createdBy: '', createdAt: '', updatedBy: '', updatedAt: '',
      orgChart: { id: 100, name: 'Sales', isRoot: false, deleted: false,
        createdBy: '', createdAt: '', updatedBy: '', updatedAt: '' },
      employeeLevelId: 4
    } }));
    await TestBed.configureTestingModule({
      imports: [EditRoleComponent, TranslateModule.forRoot()],
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
    fixture = TestBed.createComponent(EditRoleComponent);
    component = fixture.componentInstance;
    component.initialize({ orgChartId: 100, orgChartName: 'Sales', orgChartDeleted: false,
        roleId: 10, roleName: 'Executive', visible: true, employeeLevelId: 4 });
    fixture.detectChanges();
  });

  it('loads Employee Level options without assuming a role-name mapping', () => {
    expect(roles.getEmployeeLevels).toHaveBeenCalled();
    expect(component.employeeLevels[0].name).toBe('Executive');
    expect(component.validateForm.controls.employeeLevel.value).toBe(4);
  });

  it('requires a level before submitting and sends the selected ID', () => {
    component.validateForm.controls.employeeLevel.reset();
    component.validateForm.patchValue({ department: 100, role: 'Executive' });
    component.submit();
    expect(roles.updateRole).not.toHaveBeenCalled();
    component.validateForm.controls.employeeLevel.setValue(4);
    component.submit();
    expect(roles.updateRole).toHaveBeenCalledWith(jasmine.objectContaining({ employeeLevelId: 4 }));
  });

  it('clears the selection when the drawer closes', () => {
    component.validateForm.controls.employeeLevel.setValue(4);
    component.close();
    expect(component.validateForm.controls.employeeLevel.value).toBeNull();
  });

  it('requires classification for an inherited unmapped Role', () => {
    component.uninitialize();
    component.initialize({ orgChartId: 100, orgChartName: 'Sales', orgChartDeleted: false,
      roleId: 10, roleName: 'Executive', visible: true });
    expect(component.validateForm.controls.employeeLevel.invalid).toBeTrue();
  });

});
