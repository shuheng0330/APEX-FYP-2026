import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { of } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { RoleService } from '../../../services/role.service';
import { RoleOverview } from '../../../models/role.model';
import { RoleOverviewComponent } from './role-overview.component';

describe('Role editor configuration deep links', () => {
  const role: RoleOverview = { roleId: 7, roleName: 'Executive', orgChartId: 10, orgChartName: 'Sales',
    visible: true, orgChartDeleted: false, description: '', assignedJobScopes: [] };
  async function open(value: string, permitted = true) {
    await TestBed.configureTestingModule({ imports: [RoleOverviewComponent, TranslateModule.forRoot()], providers: [
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ editRoleId: value }) } } },
      { provide: RoleService, useValue: { getRoleOverview: () => of([role]) } },
      { provide: AuthService, useValue: { hasRole: () => permitted } },
      { provide: LoadingService, useValue: {} }, { provide: NzModalService, useValue: {} }
    ] }).overrideComponent(RoleOverviewComponent, { set: { imports: [], template: '' } }).compileComponents();
    const fixture = TestBed.createComponent(RoleOverviewComponent);
    const edit = spyOn(fixture.componentInstance, 'editRole');
    fixture.detectChanges(); await fixture.whenStable();
    return { page: fixture.componentInstance, edit };
  }
  it('opens the requested authorised Role once and does not reset edits on list refresh', async () => {
    const { page, edit } = await open('7');
    expect(edit).toHaveBeenCalledOnceWith(role); page.loadList(); expect(edit.calls.count()).toBe(1);
  });
  for (const id of ['bad', '-7', '9007199254740992', '99']) {
    it(`rejects invalid or unavailable Role ${id}`, async () => {
      const { page, edit } = await open(id); expect(edit).not.toHaveBeenCalled(); expect(page.deepLinkError).toBeTrue();
    });
  }
  it('does not grant edit authority through a URL', async () => {
    const { page, edit } = await open('7', false); expect(edit).not.toHaveBeenCalled(); expect(page.deepLinkError).toBeTrue();
  });
});
