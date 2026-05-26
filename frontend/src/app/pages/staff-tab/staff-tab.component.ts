import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { TranslateService } from '@ngx-translate/core';
import { TranslateModule } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { AuthService } from '../../services/auth.service';

import { AccessControlOverviewComponent } from './access-control-overview/access-control-overview.component';
import { StaffOverviewComponent } from './staff-overview/staff-overview.component';

@Component({
  selector: 'app-staff-tab',
  imports: [AccessControlOverviewComponent, NzTabsModule, TranslateModule,
    CommonModule, StaffOverviewComponent],
  templateUrl: './staff-tab.component.html',
  styleUrl: './staff-tab.component.scss'
})

export class StaffTabComponent implements OnInit {
  staffTitleKey = "PAGE.STAFF.TITLE";

  selectedTabIndex: number = 0;

  constructor(private translateService: TranslateService, private router: Router,
    private titleService: Title, private authService: AuthService) { }

  ngOnInit(): void {
    this.titleService.setTitle(this.translateService.instant(this.staffTitleKey));
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }

}

