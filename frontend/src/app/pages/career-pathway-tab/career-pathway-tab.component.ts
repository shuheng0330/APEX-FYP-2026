
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { TranslateService } from '@ngx-translate/core';
import { TranslateModule } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { AuthService } from '../../services/auth.service';

import { CareerPathwayOverviewComponent } from './career-pathway-overview/career-pathway-overview.component';
import { CareerPathwayAssignmentOverviewComponent } from './career-pathway-assignment-overview/career-pathway-assignment-overview.component';
import { MyCareerPathwayComponent } from './my-career-pathway/my-career-pathway.component';

@Component({
  selector: 'app-career-pathway-tab',
  imports: [CareerPathwayOverviewComponent, CareerPathwayAssignmentOverviewComponent,
    MyCareerPathwayComponent, NzTabsModule, TranslateModule, CommonModule],
  templateUrl: './career-pathway-tab.component.html',
  styleUrl: './career-pathway-tab.component.scss'
})

export class CareerPathwayTabComponent implements OnInit {
  careerPathwayTitleKey = "PAGE.CAREER_PATHWAY.TITLE";

  selectedTabIndex: number = 0;

  constructor(private translateService: TranslateService,
    private titleService: Title, private authService: AuthService) { }

  ngOnInit(): void {
    this.titleService.setTitle(this.translateService.instant(this.careerPathwayTitleKey));
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}

