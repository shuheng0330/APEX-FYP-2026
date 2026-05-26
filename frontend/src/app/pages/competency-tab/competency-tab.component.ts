import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

import { NzTabsModule } from 'ng-zorro-antd/tabs';

import { AuthService } from '../../services/auth.service';

import { CompetencyOverviewComponent } from './competency-overview/competency-overview.component';
import { CompetencyAssignmentOverviewComponent } from './competency-assignment-overview/competency-assignment-overview.component';
import { CompetencyDefinitionCollabComponent } from './competency-definition-collab/competency-definition-collab.component';
import { CompetencyAssignmentCollabComponent } from './competency-assignment-collab/competency-assignment-collab.component';

@Component({
  selector: 'app-competency-tab',
  imports: [CompetencyOverviewComponent, NzTabsModule, TranslateModule, CommonModule,
    CompetencyAssignmentOverviewComponent, CompetencyDefinitionCollabComponent, CompetencyAssignmentCollabComponent],
  templateUrl: './competency-tab.component.html',
  styleUrl: './competency-tab.component.scss'
})

export class CompetencyTabComponent implements OnInit {
  competenciesTitleKey = "PAGE.COMPETENCY.TITLE";

  selectedTabIndex: number = 0;

  get switchTab(): boolean {
    return true;
  }

  constructor(private translateService: TranslateService, private titleService: Title,
    private authService: AuthService, private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.translateService.get(this.competenciesTitleKey).subscribe((title) => {
      this.titleService.setTitle(title);
    })

    this.route.queryParamMap.subscribe(params => {
      const tabIndexParam = params.get('tabIndex');
      if (tabIndexParam !== null) {
        if (tabIndexParam === 'competencyOverview') {
          this.selectedTabIndex = 0
        } else if (tabIndexParam === 'assignmentOverview') {
          if (this.hasAccess(['CAN_MANAGE_COMPETENCY']) && this.hasAccess(['CAN_MANAGE_ROLE'])) {
            this.selectedTabIndex = 1
          }
        }
        else if (tabIndexParam === 'definition') {
          if (this.hasAccess(['CAN_MANAGE_COMPETENCY']) && this.hasAccess(['CAN_MANAGE_ROLE'])) {
            this.selectedTabIndex = 2
          } else {
            this.selectedTabIndex = 1
          }
        } else if (tabIndexParam === 'assignment') {
          if (this.hasAccess(['CAN_MANAGE_COMPETENCY']) && this.hasAccess(['CAN_MANAGE_ROLE'])) {
            this.selectedTabIndex = 3
          } else {
            this.selectedTabIndex = 2
          }
        }
      }
    });
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }

}
