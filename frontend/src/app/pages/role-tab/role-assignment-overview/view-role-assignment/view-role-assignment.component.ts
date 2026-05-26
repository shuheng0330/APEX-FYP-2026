import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzGridModule } from 'ng-zorro-antd/grid';

import { AuthService } from '../../../../services/auth.service';
import { RoleAssignmentOverviewData } from '../../../../models/staff-role.model';
import { Router } from '@angular/router';

@Component({
  selector: 'app-view-role-assignment',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, TranslateModule, NzIconModule, NzTagModule,
    NzEmptyModule, NzCardModule, NzToolTipModule, NzGridModule
  ],
  templateUrl: './view-role-assignment.component.html',
  styleUrl: './view-role-assignment.component.scss'
})
export class ViewRoleAssignmentComponent {
  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];
  red = "#E6173F";

  data?: RoleAssignmentOverviewData;

  private readonly profilePage = '/profile';

  constructor(private authService: AuthService, private router: Router) { }

  viewProfile(staffId: string): void {
    this.router.navigate([this.profilePage], { queryParams: { staffId } });
  }

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

  initialize(row: RoleAssignmentOverviewData): void {
    this.adjustDrawerWidth();

    this.data = row;
    this.initialized$.next(true);
  }

  uninitialize(): void {

    this.initialized$.next(false);
  }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}



