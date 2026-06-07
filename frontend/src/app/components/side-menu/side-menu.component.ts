import { Component, EventEmitter, HostBinding, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { RouterModule } from '@angular/router';
import { Router } from '@angular/router';

import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMenuModule } from 'ng-zorro-antd/menu';


import { AuthService } from '../../services/auth.service';

interface NavItem {
  key: string;
  route?: string;
  icon?: string;
  action?: 'evaluation-cycle';
  children?: NavItem[];
  requiredRoles?: string[];
  excludedRoles?: string[];
}

@Component({
  selector: 'app-side-menu',
  imports: [CommonModule, TranslateModule, NzIconModule, NzMenuModule,
    RouterModule],
  templateUrl: './side-menu.component.html',
  styleUrl: './side-menu.component.scss'
})
export class SideMenuComponent {
  @Input() isMobile = false;
  @Input() isCollapsed = true;
  @Output() isCollapsedChange = new EventEmitter<boolean>();
  @Output() openEvaluation = new EventEmitter<void>();

  @HostBinding('class.collapsed') get collapsedClass() {
    return this.isCollapsed;
  }

  navItems: NavItem[] = [
    {
      key: 'NAV.ORGANISATION_MANAGEMENT', icon: 'apartment',
      children: [
        { key: 'NAV.ORG_CHART', route: '/org-chart', requiredRoles: ['ROLE_USER'] },
        { key: 'NAV.STAFFS', route: '/staff', requiredRoles: ['ROLE_USER', 'CAN_VIEW_ACCESS_CONTROL', 'CAN_MANAGE_ACCESS_CONTROL'] },
        { key: 'NAV.ROLES', route: '/roles', requiredRoles: ['CAN_MANAGE_ROLE', 'CAN_MANAGE_STAFF'] },
        { key: 'NAV.COMPETENCIES', route: '/competencies', requiredRoles: ['CAN_MANAGE_ROLE', 'CAN_MANAGE_COMPETENCY', 'CAN_PROPOSE_ROLE_COMPETENCIES'] },
        { key: 'NAV.ROLES_COMPETENCIES', route: '/roles-competencies', requiredRoles: ['ROLE_USER'] },
      ],
      requiredRoles: ['ROLE_USER']
    },
    {
      key: 'NAV.PERFORMANCE_APPRAISAL', icon: 'fund-projection-screen',
      children: [
        {
          key: 'NAV.TEAM_PERFORMANCE',
          route: '/evaluation/overview',
          requiredRoles: ['CAN_MANAGE_EVALUATION'],
          excludedRoles: ['CAN_MANAGE_EVALUATION_CYCLE']
        },
        { key: 'NAV.ORG_WIDE_PERFORMANCE', route: '/evaluation/org-overview', requiredRoles: ['CAN_MANAGE_EVALUATION_CYCLE'] },
        { key: 'NAV.MY_PERFORMANCE', route: '/evaluation/my-evaluation', requiredRoles: ['ROLE_USER'] }
      ],
      requiredRoles: ['ROLE_USER']
    },
    {
      key: 'NAV.TRAINING_AUTOMATION', icon: 'solution',
      children: [
        { key: 'NAV.SOP_UPLOAD', route: '/sop/upload', requiredRoles: ['CAN_MANAGE_TRAINING'] },
        { key: 'NAV.SOP_REVIEW', route: '/sop/review', requiredRoles: ['CAN_MANAGE_TRAINING'] }
      ],
      requiredRoles: ['CAN_MANAGE_TRAINING']
    }
  ];

  constructor(private router: Router, public authService: AuthService) {

  }

  ngOnInit(): void {

  }

  hasAccess(item: NavItem): boolean {
    if (item.excludedRoles?.some(role => this.authService.hasRole(role))) {
      return false;
    }

    if (!item.requiredRoles || item.requiredRoles.length === 0) {
      return true;
    }
    return item.requiredRoles.some(role => this.authService.hasRole(role));
  }

  onProfileClick(): void {
    this.isCollapsed = true;
    this.isCollapsedChange.emit(this.isCollapsed);
    this.router.navigate(['/profile']);
  }

  onLogout(): void {
    this.isCollapsed = true;
    this.isCollapsedChange.emit(this.isCollapsed);
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login']);
      },
      error: err => {
        this.router.navigate(['/login']);
      }
    });
  }


  toggleCollapsed(): void {
    this.isCollapsed = !this.isCollapsed;
    this.isCollapsedChange.emit(this.isCollapsed);
  }

  onNavClick(item: NavItem): void {
    if (item.action === 'evaluation-cycle') {
      this.openEvaluationDrawer();
      return;
    }

    if (this.isMobile) {
      this.isCollapsed = true;
      this.isCollapsedChange.emit(this.isCollapsed);
    }
  }

  openEvaluationDrawer(): void {
    if (this.isMobile) {
      this.isCollapsed = true;
      this.isCollapsedChange.emit(this.isCollapsed);
    }
    this.openEvaluation.emit();
  }
}
