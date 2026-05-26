import { Component, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { TranslateModule } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, catchError, of } from 'rxjs';

import { FileUploadComponent } from '../../components/file-buffer/file-upload/file-upload.component';
import { ViewRolesCompetenciesComponent } from '../roles-competencies-tab/view-roles-competencies/view-roles-competencies.component';

import { AuthService } from '../../services/auth.service';
import { OrgChartService } from '../../services/orgChart.service';

import { NzCardModule } from 'ng-zorro-antd/card';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzImageModule } from 'ng-zorro-antd/image';
import {
  NgxInteractiveOrgChart,
  OrgChartNode,
} from 'ngx-interactive-org-chart';

import { OrgChartDepartmentNodeDto, OrgChartGraphDto, OrgChartGraphNodeDto } from '../../models/orgChart.model';
import { Role } from '../../models/role.model';
import { LoadingService } from '../../services/loading.service';
import { StaffProfileService } from '../../services/staff-profile.service';

enum TypeEnum {
  Department = 'D',
  Person = 'P',
}

@Component({
  selector: 'app-orgchart-page',
  imports: [CommonModule, TranslateModule, FileUploadComponent, NzImageModule,
    NzCardModule, NzIconModule, NzButtonModule, NgxInteractiveOrgChart,
    ViewRolesCompetenciesComponent
  ],
  templateUrl: './orgchart-page.html',
  styleUrl: './orgchart-page.scss'
})
export class OrgchartPage {
  @ViewChild(FileUploadComponent) fileUploadComponent!: FileUploadComponent;
  @ViewChild(ViewRolesCompetenciesComponent) viewRolesCompetenciesComponent!: ViewRolesCompetenciesComponent;
  @ViewChildren('chart') charts!: QueryList<any>;
  isAllExpanded$ = new BehaviorSubject<boolean>(true);

  selectedFiles: File[] = [];

  readonly titleKey: string = "PAGE.ORG_CHART.TITLE";
  readonly maxFileSizeMB: number = 5;
  readonly acceptedFileType: string = '.xlsx'
  readonly acceptedFileExtension: string = 'xlsx'
  private readonly errorTitleKey = 'NOTIFICATION.ERR.TITLE';
  private readonly errorMessageKey = 'NOTIFICATION.ERR.MESSAGE';
  private readonly invalidFileTypeErrorTitleKey = 'COMPONENT.FILE.ERROR.INVALID.TITLE';
  private readonly invalidFileTypeErrorMessageKey = 'COMPONENT.FILE.ERROR.INVALID.MSG';
  private readonly invalidFileSizeErrorTitleKey = 'COMPONENT.FILE.ERROR.LARGE.TITLE';
  private readonly invalidFileSizeErrorMessageKey = 'COMPONENT.FILE.ERROR.LARGE.MSG';

  protected readonly orgChartData: OrgChartNode<OrgChartGraphNodeDto>[] = [];
  profileImageFallBackUrl: string = 'assets/avatar.png';
  profileImageUrl: string = this.profileImageFallBackUrl;

  protected readonly dataTypeEnum = TypeEnum;

  protected response: OrgChartGraphDto[] = [];
  ngZone: any;

  constructor(private translate: TranslateService, private titleService: Title,
    private authService: AuthService, private orgChartService: OrgChartService,
    private loadingService: LoadingService, private staffProfileService: StaffProfileService
  ) {
    this.titleService.setTitle(this.translate.instant(this.titleKey));
    this.loadOrgChart();
  }

  ngAfterViewInit() {
    const elements = document.querySelectorAll('.department-node *');
    elements.forEach(el => (el as HTMLElement).style.pointerEvents = 'all');
  }

  private loadOrgChart() {
    this.orgChartService.show().subscribe({
      next: (data) => {
        this.response = data;
        this.mapAllData();
      }
    });
  }

  private mapAllData(): void {
    this.response.forEach(node => {
      const data = this.mapDataToOrgChartNode(node);
      this.orgChartData.push(data);
    })
  }

  private mapDataToOrgChartNode(node: OrgChartGraphDto): OrgChartNode<OrgChartGraphNodeDto> {
    const data = node.data!;
    if (data?.departmentNode) {
      (data.departmentNode as any).computedRoles = this.getRoles(data.departmentNode);
    }

    if (data?.personNode) {
      if (data.personNode.staffId) {
        this.staffProfileService.getProfilePicture(data.personNode.staffId).pipe(
          catchError(() => of(null))
        ).subscribe({
          next: (profilePic) => {
            if (profilePic && profilePic.body) {
              node.data!.personNode!.profileUrl = URL.createObjectURL(profilePic.body);
            } else {
              node.data!.personNode!.profileUrl = this.profileImageFallBackUrl;
            }
          }
        })
      }
    }

    return {
      id: node.id,
      name: node.name || node.data?.personNode?.staffName || node.data?.departmentNode?.departmentName || '',
      data: node.data!,
      children: node.children?.map((child) => this.mapDataToOrgChartNode(child)) || [],
      style: {
        background: node.data?.type === 'D' ? '#e0e6f6' : '#fde0e3',
        '--collapse-button-border-color': node.data?.type === 'D' ? '#e0e6f6' : '#fde0e3',
        '--collapse-button-color': node.data?.type === 'D' ? '#15469C' : '#E6173F',
        '--collapse-button-hover-color': node.data?.type === 'D' ? '#e0e6f6' : '#fde0e3',
        '--collapse-button-hover-background': node.data?.type === 'D' ? '#1890ff' : '#ff4a54',
        '--node-outline-color': node.data?.type === 'D' ? '#e0e6f6' : '#fde0e3',
        '--node-active-outline-color': node.data?.type === 'D' ? '#15469C' : '#E6173F'
      },
    };
  }

  openFileSelector(): void {
    this.fileUploadComponent.open();
  }

  importData(): void {
    if (this.fileUploadComponent.selectedFiles.length == 1) {
      const file = this.fileUploadComponent.selectedFiles.at(0)!;

      const maxSizeBytes = this.maxFileSizeMB * 1024 * 1024;
      const validExtensions = [this.acceptedFileExtension];

      const fileName = file.name.toLowerCase();
      const fileExtension = fileName.split('.').pop()!;
      const isValidExtension = validExtensions.includes(fileExtension);
      const isValidType =
        file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
        file.type === 'application/octet-stream' ||
        file.type === ''; // some browsers leave it blank

      if (file.size > maxSizeBytes) {
        this.fileUploadComponent.errorTitle = this.translate.instant(this.invalidFileSizeErrorTitleKey);
        this.fileUploadComponent.errorMessage = this.translate.instant(this.invalidFileSizeErrorMessageKey);
        return;
      }
      if (!isValidExtension || !isValidType) {
        this.fileUploadComponent.errorTitle = this.translate.instant(this.invalidFileTypeErrorTitleKey);
        this.fileUploadComponent.errorMessage = this.translate.instant(this.invalidFileTypeErrorMessageKey);
        return;
      }


      this.loadingService.show();
      this.fileUploadComponent.isUploading = true;
      this.orgChartService.import(file).subscribe({
        next: () => {
          this.loadOrgChart();
          this.fileUploadComponent.close();
        },
        error: (error: HttpErrorResponse) => {
          let errorTitle = this.translate.instant(this.errorTitleKey);
          let errorMessage = this.translate.instant(this.errorMessageKey);

          if (error.error?.message) {
            errorMessage = error.error?.message;
          }

          if (error.error?.title) {
            errorTitle = error.error?.title;
          }

          this.fileUploadComponent.errorMessage = errorMessage;
          this.fileUploadComponent.errorTitle = errorTitle;
          this.fileUploadComponent.isUploading = false;
          this.loadingService.hide();
        },
        complete: () => {
          this.fileUploadComponent.isUploading = false;
          this.loadingService.hide();
        }
      });
    }
  }

  getRoles(departmentNode: OrgChartDepartmentNodeDto): { roleId: number; roleName: string; staffCount: number, visibility: boolean }[] {
    if (!departmentNode || !departmentNode.roles) return [];

    return Object.entries(departmentNode.roles).map(([key, value]) => ({
      roleId: Number(key),
      roleName: (value as Role).name,
      staffCount: departmentNode.roleStaffNumberMap?.[Number(key)] ?? 0,
      visibility: (value as Role).visible ?? false
    }));
  }

  viewRoleDetails(roleId: number): void {
    this.viewRolesCompetenciesComponent.initialize(roleId, undefined);
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }

  export(): void {
    if (this.hasAccess(['CAN_MANAGE_ORG_CHART'])) {
      this.orgChartService.export().subscribe((response) => {
        const blob = response.body!;
        const contentDisposition = response.headers.get('content-disposition');
        const filename = contentDisposition?.split('filename=')[1]?.replace(/"/g, '') || 'download.xlsx';

        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        window.URL.revokeObjectURL(url);
      });
    }
  }

  zoomInAll(): void {
    this.charts.forEach(chart => chart.zoomIn({ by: 10, relative: true }));
  }

  zoomOutAll(): void {
    this.charts.forEach(chart => chart.zoomOut({ by: 10, relative: true }));
  }

  resetAll(): void {
    this.charts.forEach(chart => chart.resetPanAndZoom(50));
  }

  toggleCollapseAll(): void {
    this.isAllExpanded$.next(!this.isAllExpanded$.value);
    this.charts.forEach(chart => chart.toggleCollapseAll());
  }
}
