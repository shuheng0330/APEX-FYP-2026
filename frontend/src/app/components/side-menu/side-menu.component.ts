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
  children?: NavItem[];
  requiredRoles?: string[];
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
    { key: 'NAV.ORG_CHART', route: '/org-chart', requiredRoles: ['ROLE_USER'], icon: 'cluster' },
    { key: 'NAV.CAREER_PATHWAY', route: '/career-pathway', requiredRoles: ['ROLE_USER'], icon: 'node-index' },
    {
      key: 'NAV.ROLES_COMPETENCIES', icon: 'hdd',
      children: [
        { key: 'NAV.ROLES_COMPETENCIES', route: '/roles-competencies', requiredRoles: ['ROLE_USER'] },
        { key: 'NAV.ROLES', route: '/roles', requiredRoles: ['CAN_MANAGE_ROLE', 'CAN_MANAGE_STAFF'] },
        { key: 'NAV.COMPETENCIES', route: '/competencies', requiredRoles: ['CAN_MANAGE_ROLE', 'CAN_MANAGE_COMPETENCY', 'CAN_PROPOSE_ROLE_COMPETENCIES'] },
      ],
      requiredRoles: ['ROLE_USER']
    },
    {
      key: 'NAV.STAFFS', route: '/staff', icon: 'idcard',
      requiredRoles: ['ROLE_USER', 'CAN_VIEW_ACCESS_CONTROL', 'CAN_MANAGE_ACCESS_CONTROL']
    },
    {
      key: 'NAV.TRAINING', icon: 'aim',
      children: [
        { key: 'NAV.TRAINING_MANAGEMENT', route: '/training/management', requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING'] },
        { key: 'NAV.TRAINING_ENGAGEMENT', route: '/training/engagement', requiredRoles: ['ROLE_USER'] }
      ],
      requiredRoles: ['ROLE_USER']
    },
    {
      key: 'NAV.SOP_TRAINING', icon: 'robot',
      children: [
        { key: 'NAV.SOP_UPLOAD', route: '/sop/upload', requiredRoles: ['CAN_MANAGE_TRAINING'] },
        { key: 'NAV.SOP_REVIEW', route: '/sop/review', requiredRoles: ['CAN_MANAGE_TRAINING'] }
      ],
      requiredRoles: ['CAN_MANAGE_TRAINING']
    },
    {
      key: 'NAV.LEARNING', icon: 'alert',
      children: [
        { key: 'NAV.LEARNING_MANAGEMENT', route: '/learning/management', requiredRoles: ['CAN_MANAGE_LEARNING_MATERIAL'] },
        { key: 'NAV.LEARNING_ENGAGEMENT', route: '/learning/engagement', requiredRoles: ['ROLE_USER'] }
      ],
      requiredRoles: ['ROLE_USER']
    },
    {
      key: 'NAV.EVALUATION', icon: 'fund-projection-screen',
      children: [
        { key: 'NAV.EVALUATION_OVERVIEW', route: '/evaluation/overview', requiredRoles: ['CAN_MANAGE_EVALUATION'] },
        { key: 'NAV.ORG_WIDE_EVALUATION', route: '/evaluation/org-overview', requiredRoles: ['CAN_MANAGE_EVALUATION_CYCLE'] },
        { key: 'NAV.MY_EVALUATION', route: '/evaluation/my-evaluation', requiredRoles: ['ROLE_USER'] }
      ],
      requiredRoles: ['ROLE_USER']
    }
  ];

  constructor(private router: Router, public authService: AuthService) {

  }

  ngOnInit(): void {

  }

  onMouseEnter(): void {
    if (this.isMobile) return;

    if (this.isCollapsed) {
      this.isCollapsed = false;
      this.isCollapsedChange.emit(this.isCollapsed);
    }
  }

  onMouseLeave(): void {
    if (this.isMobile) return;

    if (!this.isCollapsed) {
      this.isCollapsed = true;
      this.isCollapsedChange.emit(this.isCollapsed);
    }
  }

  hasAccess(item: NavItem): boolean {
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

  openEvaluationDrawer(): void {
    this.isCollapsed = true;
    this.isCollapsedChange.emit(this.isCollapsed); // Collapse menu when opening drawer
    this.isCollapsedChange.emit(this.isCollapsed); // Collapse menu when opening drawer
    this.openEvaluation.emit();
  }
}
