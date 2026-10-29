import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { TranslateService } from '@ngx-translate/core';
import { TranslateModule } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { AuthService } from '../../services/auth.service';

import { GeneralInformationComponent } from './general-information/general-information.component';
import { MyCareerPathwayComponent } from '../career-pathway-tab/my-career-pathway/my-career-pathway.component';
import { SelfDeclaredSkillComponent } from './self-declared-skill/self-declared-skill.component';
import { CertGalleryComponent } from './cert-gallery/cert-gallery.component';
import { TrainingRecordComponent } from '../training/training-record/training-record.component';

@Component({
  selector: 'app-staff-profile',
  imports: [TranslateModule, CommonModule, NzTabsModule, GeneralInformationComponent,
    SelfDeclaredSkillComponent, MyCareerPathwayComponent, CertGalleryComponent, TrainingRecordComponent],
  templateUrl: './staff-profile.component.html',
  styleUrl: './staff-profile.component.scss'
})
export class StaffProfileComponent implements OnInit {
  staffTitleKey = "PAGE.PROFILE.TITLE";

  selectedTabIndex: number = 0;
  /** The person whose profile this is, when it isn't one's own: their training record shows (FR-18-04). */
  profileStaffId: string | null = null;

  constructor(private translateService: TranslateService, private router: Router,
    private titleService: Title, private authService: AuthService, private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.titleService.setTitle(this.translateService.instant(this.staffTitleKey));

    this.route.queryParams.subscribe(params => {
      const staffId = params['staffId'];
      this.profileStaffId = staffId || null;
      if (staffId) {
        this.selectedTabIndex = 0;
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

