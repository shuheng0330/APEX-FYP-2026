import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzSpaceModule } from 'ng-zorro-antd/space';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzCardModule } from 'ng-zorro-antd/card';

import { AuthService } from '../../../../services/auth.service';
import { CompetencyAssignmentRequiredDataId, CompetencyAssignmentProposal, CompetencyAssignmentProposalOverviewData } from '../../../../models/competency-collaboration.model';
import { BehaviorSubject } from 'rxjs';
import { JobScope } from '../../../../models/jobScope.model';
@Component({
  selector: 'app-view-competency-assignment-collab',
  imports: [NzButtonModule, NzDrawerModule, NzSpaceModule, NzTableModule, CommonModule,
    ReactiveFormsModule, FormsModule, NzIconModule, TranslateModule,
    NzEmptyModule, NzAvatarModule, NzCardModule],
  templateUrl: './view-competency-assignment-collab.component.html',
  styleUrl: './view-competency-assignment-collab.component.scss'
})
export class ViewCompetencyAssignmentCollabComponent {
  readonly notFoundTitleKey: string = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.VIEW.NOT_FOUND.TITLE';

  competencyAssignmentPage: string = "/competencies";
  title: string = '';
  visible$ = new BehaviorSubject<boolean>(false);
  expandSet = new Set<CompetencyAssignmentRequiredDataId>();
  notFound: boolean = true;
  drawerWidth: string = '736px';

  data: CompetencyAssignmentProposalOverviewData | undefined;
  jobScopes: JobScope[] = [];
  competencies: CompetencyAssignmentProposal[] = [];

  constructor(private authService: AuthService, private router: Router, private translate: TranslateService) { }

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

  open(data?: CompetencyAssignmentProposalOverviewData): void {
    this.adjustDrawerWidth();

    if (data != undefined) {
      this.title = `${data.roleName}`;
      this.data = data;
      this.jobScopes = data.assignedJobScopes ?? [];
      this.competencies = data.assignedCompetencies;
      this.visible$.next(true);
      this.notFound = false;
    } else {
      const notFoundTitle = this.translate.instant(this.notFoundTitleKey);
      this.title = notFoundTitle;
      this.visible$.next(true);
      this.notFound = true;
    }
  }

  close(): void {
    this.title = '';
    this.data = undefined;
    this.visible$.next(false);
  }

  onExpandChange(id: CompetencyAssignmentRequiredDataId, checked: boolean): void {
    if (checked) {
      this.expandSet.add(id);
    } else {
      this.expandSet.delete(id);
    }
  }

  expandAll(): void {
    this.competencies.forEach(competency => {
      if (!this.expandSet.has(competency.id)) {
        this.expandSet.add(competency.id);
      }
    })
  }

  collapAll(): void {
    this.expandSet.clear();
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }

  notFoundClick(): void {
    this.close();
    this.router.navigate([this.competencyAssignmentPage], {
      queryParams: { tabIndex: 'assignmentOverview' }
    });
  }
}
