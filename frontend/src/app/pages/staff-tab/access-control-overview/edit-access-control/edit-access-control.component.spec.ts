import { TestBed } from '@angular/core/testing';
import { getDebugNode } from '@angular/core';
import { PermissionSelectorComponent } from '../../../../components/permission-selector/permission-selector.component';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { EditAccessControlComponent } from './edit-access-control.component';
import { AuthorityService } from '../../../../services/authority.service';
import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';
import { OrgChartService } from '../../../../services/orgChart.service';
import { RoleService } from '../../../../services/role.service';
import { NzModalService } from 'ng-zorro-antd/modal';

describe('EditAccessControlComponent permission presentation', () => {
  const api = { getStaffAccessControlOverview: jasmine.createSpy().and.returnValue(of({permissions: [], departments: []})),
    grantAccess: jasmine.createSpy().and.returnValue(of(undefined)), editGrantedAccess: jasmine.createSpy().and.returnValue(of(undefined)) };
  const auth = { hasRole: jasmine.createSpy().and.returnValue(true), refresh: jasmine.createSpy().and.returnValue(of(null)) };
  const modal = { confirm: jasmine.createSpy() };
  beforeEach(async () => {
    api.grantAccess.calls.reset(); api.editGrantedAccess.calls.reset(); auth.refresh.calls.reset(); modal.confirm.calls.reset();
    await TestBed.configureTestingModule({imports: [EditAccessControlComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), {provide: AuthorityService, useValue: api}, {provide: AuthService, useValue: auth},
      {provide: LoadingService, useValue: {show: jasmine.createSpy(), hide: jasmine.createSpy()}},
      {provide: OrgChartService, useValue: {getAllOrgChart: () => of([])}},
      {provide: RoleService, useValue: {getAllRoles: () => of([])}}, {provide: NzModalService, useValue: modal}
    ]}).compileComponents();
  });
  it('creates without changing grants', () => {
    const fixture = TestBed.createComponent(EditAccessControlComponent); fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    expect(api.grantAccess).not.toHaveBeenCalled(); expect(api.editGrantedAccess).not.toHaveBeenCalled();
  });

  it('preserves existing validation and exact ID payloads', () => {
    const page = TestBed.createComponent(EditAccessControlComponent).componentInstance;
    page.submit(); expect(api.editGrantedAccess).not.toHaveBeenCalled();
    page.validateForm.setValue({department: 2, role: 3, authorities: ['1', '2']});
    page.submit();
    expect(api.editGrantedAccess).toHaveBeenCalledWith(2, 3, ['1', '2']);
    expect(auth.refresh).toHaveBeenCalledOnceWith(false);
  });
  it('cancellation closes and resets without saving', () => {
    const page = TestBed.createComponent(EditAccessControlComponent).componentInstance;
    page.validateForm.setValue({department: 2, role: 3, authorities: ['1']});
    page.close(); expect(page.visible$.value).toBeFalse();
    expect(api.editGrantedAccess).not.toHaveBeenCalled();
  });
  for (const hidden of [[], ['12'], ['13'], ['12', '13'], ['24'], ['12', '13', '24']]) {
    it('preserves hidden grants in the exact Edit payload: ' + JSON.stringify(hidden), async () => {
      const fixture = TestBed.createComponent(EditAccessControlComponent); const page = fixture.componentInstance;
      page.translatedAuthorityList = [
        {id: 1, name: 'ROLE_USER', label: 'User', description: 'Basic'},
        {id: 12, name: 'CAN_MANAGE_EVALUATION', label: 'Old evaluation', description: 'Legacy'},
        {id: 13, name: 'CAN_MANAGE_EVALUATION_CYCLE', label: 'Old cycle', description: 'Legacy'},
        {id: 24, name: 'CAN_ENTER_COMPANY_KPI_RESULTS', label: 'Enter Company KPI Results', description: 'Delegated entry'}];
      page.selectedOrgChartId = 2; page.selectedRoleId = 3; page.selectedAuthorities = [...hidden];
      page.initialize(); page.open(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
      const selector = getDebugNode(document.querySelector('app-permission-selector')!)!.componentInstance as PermissionSelectorComponent;
      selector.query = 'no match'; selector.setExpanded('TRAINING', false); selector.query = '';
      selector.toggle(page.translatedAuthorityList[0], true);
      expect(page.validateForm.controls.authorities.value).toEqual([...hidden, '1']);
      page.submit(); expect(api.editGrantedAccess).toHaveBeenCalledWith(2, 3, [...hidden, '1']);
      fixture.detectChanges();
    });
  }
  it('allows unchanged hidden-only grants through existing validation and preserves them', () => {
    const page = TestBed.createComponent(EditAccessControlComponent).componentInstance;
    page.validateForm.setValue({department: 2, role: 3, authorities: ['12', '13']});
    expect(page.validateForm.valid).toBeTrue(); page.submit();
    expect(api.editGrantedAccess).toHaveBeenCalledWith(2, 3, ['12', '13']);
  });
  it('renders the shared selector in its existing drawer', async () => {
    const fixture = TestBed.createComponent(EditAccessControlComponent); const page = fixture.componentInstance;
    page.translatedAuthorityList = [{id: 1, name: 'ROLE_USER', label: 'auth.role.user', description: 'basic'}];
    page.visible$.next(true); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(document.querySelector('app-permission-selector')).not.toBeNull();
    page.close(); fixture.detectChanges();
  });
});
