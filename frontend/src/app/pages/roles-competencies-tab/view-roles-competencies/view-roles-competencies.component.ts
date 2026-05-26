import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzSpaceModule } from 'ng-zorro-antd/space';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';

import { AuthService } from '../../../services/auth.service';
import { RoleCompetencyService } from '../../../services/role-competency.service';

import { RoleDetailsData } from '../../../models/role.model';
import { Router } from '@angular/router';

interface RoleCompetencyId {
  roleId: number,
  competencyId: number
}

@Component({
  selector: 'app-view-roles-competencies',
  imports: [NzButtonModule, NzDrawerModule, NzSpaceModule, NzTableModule, CommonModule,
    NzIconModule, TranslateModule, NzEmptyModule, NzTagModule, NzGridModule, NzCardModule, NzAvatarModule
  ],
  templateUrl: './view-roles-competencies.component.html',
  styleUrl: './view-roles-competencies.component.scss'
})
export class ViewRolesCompetenciesComponent {
  private readonly profilePage = '/profile';
  visible$ = new BehaviorSubject<boolean>(false);
  expandSet = new Set<RoleCompetencyId>();
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];

  data?: RoleDetailsData;
  drawerWidth: string = '736px';

  constructor(private authService: AuthService, private router: Router,
    private roleCompetencyService: RoleCompetencyService) { }

  @HostListener('window:resize', ['$event'])
  onResize(event: any) {
    this.adjustDrawerWidth();
  }

  adjustDrawerWidth() {
    if (window.innerWidth <= 768) {
      this.drawerWidth = '100%';
    } else {
      this.drawerWidth = '736px';
    }
  }

  initialize(roleId: number, data?: RoleDetailsData) {
    this.adjustDrawerWidth();
    if (data == undefined) {
      this.roleCompetencyService.getRoleDetails(roleId).subscribe({
        next: (res) => {
          this.data = res;
          this.data.competencies = this.data.competencies?.sort((a, b) => b.weightage - a.weightage);
          this.visible$.next(true);
        }
      });
    } else {
      this.data = data;
      this.visible$.next(true);
    }
  }

  close(): void {
    this.data = undefined;
    this.visible$.next(false);
  }

  onExpandChange(id: RoleCompetencyId, checked: boolean): void {
    if (checked) {
      this.expandSet.add(id);
    } else {
      this.expandSet.delete(id);
    }
  }

  expandAll(): void {
    this.data?.competencies?.forEach(competency => {
      if (!this.expandSet.has(competency.id)) {
        this.expandSet.add(competency.id);
      }
    })
  }

  collapAll(): void {
    this.expandSet.clear();
  }

  viewProfile(staffId: string): void {
    this.close();
    this.router.navigate([this.profilePage], { queryParams: { staffId } });
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
