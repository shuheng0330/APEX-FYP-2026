import { Component, OnInit } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { NzTabsModule } from 'ng-zorro-antd/tabs';

import { AuthService } from '../../services/auth.service';

import { RoleOverviewComponent } from './role-overview/role-overview.component';
import { RoleAssignmentOverviewComponent } from './role-assignment-overview/role-assignment-overview.component';

@Component({
  selector: 'app-role-tab',
  imports: [CommonModule, NzTabsModule, TranslateModule, RoleOverviewComponent,
    RoleAssignmentOverviewComponent
  ],
  templateUrl: './role-tab.component.html',
  styleUrl: './role-tab.component.scss'
})

export class RoleTabComponent implements OnInit {
  roleTitleKey = "PAGE.ROLE.TITLE";

  selectedTabIndex: number = 0;

  constructor(private translateService: TranslateService, private router: Router,
    private titleService: Title, private authService: AuthService) { }

  ngOnInit(): void {
    this.titleService.setTitle(this.translateService.instant(this.roleTitleKey));
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }

}

