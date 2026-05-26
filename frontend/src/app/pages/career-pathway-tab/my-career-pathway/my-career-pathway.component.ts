import { Component, NgZone, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject, forkJoin } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';

import {
  NgxInteractiveOrgChart,
  OrgChartNode,
} from 'ngx-interactive-org-chart';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { CareerPathwayOverview, CareerPathwayOverviewGraphData } from '../../../models/careerPathway.model';

import { CareerPathwayService } from '../../../services/careerPathway.service';

import { ViewRolesCompetenciesComponent } from '../../roles-competencies-tab/view-roles-competencies/view-roles-competencies.component';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-my-career-pathway',
  imports: [NzButtonModule, CommonModule, NzCardModule, TranslateModule,
    NzIconModule, NzEmptyModule, NgxInteractiveOrgChart, NzTagModule, ViewRolesCompetenciesComponent],
  templateUrl: './my-career-pathway.component.html',
  styleUrl: './my-career-pathway.component.scss'
})
export class MyCareerPathwayComponent implements OnInit {
  @ViewChild(NgxInteractiveOrgChart) orgChart!: NgxInteractiveOrgChart<OrgChartNode>;
  @ViewChild(ViewRolesCompetenciesComponent) viewRolesCompetenciesComponent!: ViewRolesCompetenciesComponent;

  assignedCareerPathway?: CareerPathwayOverview;
  orgChartData?: OrgChartNode;
  selectedStaffId?: string;

  orgChartDataReady = false;
  isAllExpanded$ = new BehaviorSubject<boolean>(true);
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];

  constructor(private careerPathwayService: CareerPathwayService, private ngZone: NgZone,
    private route: ActivatedRoute, private authService: AuthService
  ) { }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const staffId = params['staffId'];
      if (staffId) {
        this.selectedStaffId = staffId;
      } else {
        this.selectedStaffId = this.authService.userId!;
      }
    });
    this.loadCareerPathway();
  }

  loadCareerPathway() {
    if (this.selectedStaffId) {
      forkJoin({
        my: this.careerPathwayService.my(this.selectedStaffId!)
      }).subscribe({
        next: ({ my }) => {
          this.assignedCareerPathway = my;
          this.assignedCareerPathway.graph = this.mapOrgChartNodeStyle(this.assignedCareerPathway.graph!)
          this.orgChartData = this.assignedCareerPathway.graph;

          this.ngZone.run(() => {
            this.orgChartDataReady = true;
          });
        }
      })
    }
  }

  private mapOrgChartNodeStyle(node: OrgChartNode): OrgChartNode {
    const data = node.data! as CareerPathwayOverviewGraphData;
    return {
      id: node.id,
      name: node.name,
      data: data,
      children: node.children?.map((child) => this.mapOrgChartNodeStyle(child)) || [],
      style: {
        background: data.hasVisited === 0 ? '#DDF4E7' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--collapse-button-border-color': data.hasVisited === 0 ? '#B6CEB4' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--collapse-button-color': data.hasVisited === 0 ? '#043915' : data.hasVisited === -1 ? '#15469C' : '#0C2B4E',
        '--collapse-button-hover-color': data.hasVisited === 0 ? '#043915' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--collapse-button-hover-background': data.hasVisited === 0 ? '#A8FBD3' : data.hasVisited === -1 ? '#1890ff' : '#1890ff',
        '--node-outline-color': data.hasVisited === 0 ? '#DDF4E7' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--node-active-outline-color': data.hasVisited === 0 ? '#043915' : data.hasVisited === -1 ? '#15469C' : '#000000'
      }
    }
  }

  toggleCollapse(): void {
    this.isAllExpanded$.next(!this.isAllExpanded$.value);
  }

  viewRoleDetails(roleId: number): void {
    this.viewRolesCompetenciesComponent.initialize(roleId, undefined);
  }

}
