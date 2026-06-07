import { Routes } from '@angular/router';

import { OrgchartPage } from './pages/orgchart-page/orgchart-page';
import { TrainingManagementPageComponent } from './pages/training/training-management/training-management-page';
import { EvaluationPageComponent } from './pages/evaluation-page/evaluation-page.component';
import { RoleTabComponent } from './pages/role-tab/role-tab.component';
import { CompetencyTabComponent } from './pages/competency-tab/competency-tab.component';
import { RolesCompetenciesTabComponent } from './pages/roles-competencies-tab/roles-competencies-tab.component';
import { CareerPathwayTabComponent } from './pages/career-pathway-tab/career-pathway-tab.component';
import { TrainingEngagementPageComponent } from './pages/training/training-engagement/training-engagement-page.component';
import { PerformanceDashboardPage } from './pages/evaluation-page/performance-dashboard/performance-dashboard.page';
import { EvaluationOverviewPageComponent } from './pages/evaluation-page/evaluation-overview-page/evaluation-overview-page.component';
import { OrgWideEvaluationComponent } from './pages/evaluation-page/org-wide-evaluation/org-wide-evaluation.component';
import { LoginComponent } from './pages/login-page/login/login.component';
import { FirstTimeLoginComponent } from './pages/login-page/first-time-login/first-time-login.component';
import { ForgotPasswordComponent } from './pages/login-page/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './pages/login-page/reset-password/reset-password.component';
import { StaffTabComponent } from './pages/staff-tab/staff-tab.component';
import { LearningManagementComponent } from './pages/learning/learning-management/learning-management.component';
import { AuthGuard } from './guards/auth.guard';
import { LoginGuard } from './guards/login.guard';
import { LearningMaterialPreviewComponent } from './components/learning-material/learning-material-preview/learning-material-preview.component';
import { LearningEngagementPageComponent } from './pages/learning/learning-engagement-page/learning-engagement-page.component';
import { LearningCategoryPageComponent } from './pages/learning/learning-category-page/learning-category-page.component';
import { StaffProfileComponent } from './pages/staff-profile/staff-profile.component';
import { PermissionGuard } from './guards/permission.guard';
import { SopUploadComponent } from './pages/sop/sop-upload/sop-upload.component';
import { SopReviewComponent } from './pages/sop/sop-review/sop-review.component';

export const routes: Routes = [
  // Guest-only routes (login, forgot, reset)
  { path: 'login', component: LoginComponent, canActivate: [LoginGuard] },
  { path: 'first-time-login', component: FirstTimeLoginComponent, canActivate: [LoginGuard] },
  { path: 'forgot-password', component: ForgotPasswordComponent, canActivate: [LoginGuard] },
  { path: 'reset-password', component: ResetPasswordComponent, canActivate: [LoginGuard] },

  // Authenticated-only routes
  {
    path: '',
    canActivate: [AuthGuard],
    children: [
      {
        path: 'org-chart', component: OrgchartPage,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      {
        path: 'career-pathway', component: CareerPathwayTabComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      {
        path: 'roles', component: RoleTabComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_ROLE', 'CAN_MANAGE_STAFF'] },
      },
      {
        path: 'competencies', component: CompetencyTabComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_COMPETENCY', 'CAN_MANAGE_ROLE', 'CAN_PROPOSE_ROLE_COMPETENCIES'] },
      },
      {
        path: 'roles-competencies', component: RolesCompetenciesTabComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      {
        path: 'training/management', component: TrainingManagementPageComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING'] },
      },
      { path: 'training/engagement', component: TrainingEngagementPageComponent },
      {
        path: 'sop/upload', component: SopUploadComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING'] },
      },
      {
        path: 'sop/review', component: SopReviewComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING'] },
      },
      {
        path: 'sop/review/:id', component: SopReviewComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING'] },
      },
      {
        path: 'learning/management', component: LearningManagementComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_LEARNING_MATERIAL'] },
      },
      { path: 'learning/engagement', component: LearningEngagementPageComponent },
      { path: 'learning/engagement/:category', component: LearningCategoryPageComponent },
      {
        path: 'learning-materials/:id/preview',
        component: LearningMaterialPreviewComponent,
        data: { viewMode: 'user' }
      },
      {
        path: 'learning-management/:id/preview',
        component: LearningMaterialPreviewComponent,
        data: { viewMode: 'admin' }
      },
      {
        path: 'evaluation', component: EvaluationPageComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_EVALUATION'] },
      },
      {
        path: 'evaluation/overview', component: EvaluationOverviewPageComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_EVALUATION', 'CAN_MANAGE_EVALUATION_CYCLE'] },
      },
      {
        path: 'evaluation/org-overview', component: OrgWideEvaluationComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_EVALUATION_CYCLE'] },
      },
      {
        path: 'evaluation/my-evaluation', component: PerformanceDashboardPage,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      { path: 'performance/:staffId', component: PerformanceDashboardPage },
      {
        path: 'profile', component: StaffProfileComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      {
        path: 'staff', component: StaffTabComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      {
        path: 'profile/forgot-password', component: ForgotPasswordComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      {
        path: 'profile/reset-password', component: ResetPasswordComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      { path: '**', redirectTo: '/org-chart' }
    ]
  }
];

