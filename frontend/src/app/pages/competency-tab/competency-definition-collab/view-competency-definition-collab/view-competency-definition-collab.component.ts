import { Component, HostListener, } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { Router } from '@angular/router';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzSpaceModule } from 'ng-zorro-antd/space';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';

import { AuthService } from '../../../../services/auth.service';

import { CompetencyCollaborationOverviewData } from '../../../../models/competency-collaboration.model';
import { CompTag } from '../../../../models/comp-tag.model';

@Component({
  selector: 'app-view-competency-definition-collab',
  imports: [NzButtonModule, NzDrawerModule, NzSpaceModule, NzTableModule, CommonModule,
    ReactiveFormsModule, FormsModule, NzIconModule, TranslateModule,
    NzEmptyModule, NzTagModule, NzGridModule, NzAvatarModule],
  templateUrl: './view-competency-definition-collab.component.html',
  styleUrl: './view-competency-definition-collab.component.scss'
})
export class ViewCompetencyDefinitionCollabComponent {
  readonly competencyOverviewPage: string = '/competencies';
  readonly notFoundTitleKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.VIEW.NOT_FOUND.TITLE';

  title: string = '';
  visible$ = new BehaviorSubject<boolean>(false);
  notFound: boolean = true;
  drawerWidth: string = '736px';

  data: CompetencyCollaborationOverviewData | undefined;
  tags: CompTag[] = [];
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];

  constructor(private authService: AuthService, private router: Router,
    private translate: TranslateService) { }

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

  open(data?: CompetencyCollaborationOverviewData): void {
    this.adjustDrawerWidth();
    if (data != undefined) {
      this.title = `${data.competencyName}`;
      this.data = data;
      this.tags = data.assignedCompTags ?? [];
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

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }

  notFoundClick(): void {
    this.close();
    this.router.navigate([this.competencyOverviewPage], {
      queryParams: { tabIndex: 'competencyOverview' }
    });
  }
}

