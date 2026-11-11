import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AccessControlOverviewComponent } from './access-control-overview.component';
import { AuthorityService } from '../../../services/authority.service';
import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { OrgChartService } from '../../../services/orgChart.service';
import { RoleService } from '../../../services/role.service';
import { NzModalService } from 'ng-zorro-antd/modal';

describe('AccessControlOverviewComponent permission presentation', () => {
  const api = { getStaffAccessControlOverview: jasmine.createSpy().and.returnValue(of({permissions: [], departments: []})),
    grantAccess: jasmine.createSpy().and.returnValue(of(undefined)), editGrantedAccess: jasmine.createSpy().and.returnValue(of(undefined)) };
  const auth = { hasRole: jasmine.createSpy().and.returnValue(true), refresh: jasmine.createSpy().and.returnValue(of(null)) };
  const modal = { confirm: jasmine.createSpy() };
  beforeEach(async () => {
    api.grantAccess.calls.reset(); api.editGrantedAccess.calls.reset(); auth.refresh.calls.reset(); modal.confirm.calls.reset();
    await TestBed.configureTestingModule({imports: [AccessControlOverviewComponent, TranslateModule.forRoot()], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), {provide: AuthorityService, useValue: api}, {provide: AuthService, useValue: auth},
      {provide: LoadingService, useValue: {show: jasmine.createSpy(), hide: jasmine.createSpy()}},
      {provide: OrgChartService, useValue: {getAllOrgChart: () => of([])}},
      {provide: RoleService, useValue: {getAllRoles: () => of([])}}, {provide: NzModalService, useValue: modal}
    ]}).compileComponents();
  });
  it('creates without changing grants', () => {
    const fixture = TestBed.createComponent(AccessControlOverviewComponent); fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    expect(api.grantAccess).not.toHaveBeenCalled(); expect(api.editGrantedAccess).not.toHaveBeenCalled();
  });
  it('hides legacy tags and permission search but keeps original grants and catalogue for editing', () => {
    api.getStaffAccessControlOverview.and.returnValue(of({permissions: [
      {id: 12, name: 'CAN_MANAGE_EVALUATION', label: 'Legacy evaluation', description: 'Legacy'},
      {id: 13, name: 'CAN_MANAGE_EVALUATION_CYCLE', label: 'Legacy cycle', description: 'Legacy'},
      {id: 24, name: 'CAN_ENTER_COMPANY_KPI_RESULTS', label: 'Enter Company KPI Results', description: 'Delegated entry'}],
      departments: [{orgChartId: 2, orgChartName: 'Test', orgChartDeleted: false,
        roles: [{roleId: 3, roleName: 'Test Role', authorityMap: {12: true, 13: true, 24: true}}]}]}));
    const fixture = TestBed.createComponent(AccessControlOverviewComponent); fixture.detectChanges();
    const page = fixture.componentInstance;
    expect(page.translatedAuthorityList.length).toBe(3); expect(page.displayedAuthorities).toEqual([]);
    expect(fixture.nativeElement.querySelectorAll('.sub-info nz-tag').length).toBe(0);
    page.searchField = 3; page.searchValue = 'evaluation'; page.search(); expect(page.listOfDisplayData).toEqual([]);
    page.searchValue = 'Company'; page.search(); expect(page.listOfDisplayData).toEqual([]);
    page.resetSearch(); expect(page.listOfDisplayData.length).toBe(1);
    page.editPermission(3);
    expect(page.editAccessControlComponent.selectedAuthorities).toEqual(['12', '13', '24']);
    expect(page.editAccessControlComponent.translatedAuthorityList.length).toBe(3);
    page.editAccessControlComponent.close(); fixture.detectChanges();
    api.getStaffAccessControlOverview.and.returnValue(of({permissions: [], departments: []}));
  });
  it('uses readable labels for overview tags and permission search', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {ACCESS_PERMISSIONS: {ITEMS: {ROLE_USER: {
      LABEL: 'Basic Employee Access', DESCRIPTION: 'Access employee self-service.'}}}}); translate.use('en');
    api.getStaffAccessControlOverview.and.returnValue(of({permissions: [{id: 1, name: 'ROLE_USER', label: 'auth.role.user', description: 'raw'}],
      departments: [{orgChartId: 2, orgChartName: 'Test', orgChartDeleted: false,
        roles: [{roleId: 3, roleName: 'Test Role', authorityMap: {1: true}}]}]}));
    const fixture = TestBed.createComponent(AccessControlOverviewComponent); fixture.detectChanges();
    const page = fixture.componentInstance;
    expect(page.translatedAuthorityList[0].label).toBe('Basic Employee Access');
    page.searchField = 3; page.searchValue = 'basic employee'; page.search();
    expect(page.listOfDisplayData.length).toBe(1);
    expect(page.listOfDisplayData[0].authorityMap).toEqual({1: true});
    expect(api.editGrantedAccess).not.toHaveBeenCalled();
    api.getStaffAccessControlOverview.and.returnValue(of({permissions: [], departments: []}));
  });

});
