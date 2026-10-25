import { Routes } from '@angular/router';

import { OrgchartPage } from './pages/orgchart-page/orgchart-page';
import { TrainingManagementPageComponent } from './pages/training/training-management/training-management-page';
import { RoleTabComponent } from './pages/role-tab/role-tab.component';
import { CompetencyTabComponent } from './pages/competency-tab/competency-tab.component';
import { RolesCompetenciesTabComponent } from './pages/roles-competencies-tab/roles-competencies-tab.component';
import { CareerPathwayTabComponent } from './pages/career-pathway-tab/career-pathway-tab.component';
import { TrainingEngagementPageComponent } from './pages/training/training-engagement/training-engagement-page.component';
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
import { SopQuizReviewComponent } from './pages/sop/sop-quiz-review/sop-quiz-review.component';
import { SopAssignComponent } from './pages/sop/sop-assign/sop-assign.component';
import { SopScoreboardComponent } from './pages/sop/sop-scoreboard/sop-scoreboard.component';
import { TrainerAccessComponent } from './pages/sop/trainer-access/trainer-access.component';
import { MyLearningComponent } from './pages/learning-portal/my-learning/my-learning.component';
import { notificationSelectionChanged } from './services/notification-navigation';
import { CoursePlayerComponent } from './pages/learning-portal/course-player/course-player.component';

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
        path: 'team-performance/team-appraisals/:reviewPeriodId/:participantId', canActivate: [PermissionGuard],
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        runGuardsAndResolvers: 'always',
        data: { requiredRoles: ['CAN_SUBMIT_ANNUAL_APPRAISAL'], hrReview: false, notificationWorkspace: true },
        loadComponent: () => import('./pages/annual-appraisal/annual-appraisal-detail.component').then(m => m.AnnualAppraisalDetailComponent)
      },
      {
        path: 'appraisals/review/:appraisalId', canActivate: [PermissionGuard],
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        runGuardsAndResolvers: 'always',
        data: { requiredRoles: ['CAN_REVIEW_ANNUAL_APPRAISAL'], hrReview: true, notificationWorkspace: true },
        loadComponent: () => import('./pages/annual-appraisal/annual-appraisal-detail.component').then(m => m.AnnualAppraisalDetailComponent)
      },
      {
        path: 'team-performance/team-appraisals', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_SUBMIT_ANNUAL_APPRAISAL'], hrReview: false, notificationWorkspace: true },
        loadComponent: () => import('./pages/annual-appraisal/annual-appraisals.component').then(m => m.AnnualAppraisalsComponent)
      },
      {
        path: 'appraisals/review', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_REVIEW_ANNUAL_APPRAISAL'], hrReview: true, notificationWorkspace: true },
        loadComponent: () => import('./pages/annual-appraisal/annual-appraisals.component').then(m => m.AnnualAppraisalsComponent)
      },
      {
        path: 'my-performance/my-assessments', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['ROLE_USER'], notificationWorkspace: true },
        loadComponent: () => import('./pages/kpi-assessment/my-assessments.component').then(m => m.MyAssessmentsComponent)
      },
      {
        path: 'my-performance/my-kpi-plan', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['ROLE_USER'], notificationWorkspace: true },
        loadComponent: () => import('./pages/kpi-plan/my-kpi-plan.component').then(m => m.MyKpiPlanComponent)
      },
      {
        path: 'team-performance/team-reviews', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_REVIEW_INDIVIDUAL_KPI', 'CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_ATTITUDE_EVALUATION'], notificationWorkspace: true },
        loadComponent: () => import('./pages/kpi-plan/team-reviews.component').then(m => m.TeamReviewsComponent)
      },
      {
        path: 'kpi-management/company-kpis', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_MANAGE_COMPANY_KPI'], notificationWorkspace: true },
        loadComponent: () => import('./pages/kpi-plan/company-kpi-plan.component').then(m => m.CompanyKpiPlanComponent)
      },
      {
        path: 'kpi-management/department-kpis', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_MANAGE_DEPARTMENT_KPI'], notificationWorkspace: true },
        loadComponent: () => import('./pages/kpi-plan/department-kpi-plan.component').then(m => m.DepartmentKpiPlanComponent)
      },
      {
        path: 'kpi-management/kpi-results', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_MANAGE_COMPANY_KPI', 'CAN_ENTER_COMPANY_KPI_RESULTS', 'CAN_MANAGE_DEPARTMENT_KPI'], notificationWorkspace: true },
        loadComponent: () => import('./pages/kpi-plan/kpi-central-results.component').then(m => m.KpiCentralResultsComponent)
      },
      {
        path: 'kpi-management/kpi-review', canActivate: [PermissionGuard],
        runGuardsAndResolvers: notificationSelectionChanged,
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_APPROVE_DEPARTMENT_KPI', 'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE', 'CAN_MANAGE_KPI_AMENDMENTS', 'CAN_REVISE_COMPANY_KPI', 'CAN_PREPARE_DEPARTMENT_KPI_REVISION', 'CAN_MANAGE_DEPARTMENT_KPI', 'CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS'], notificationWorkspace: true },
        loadComponent: () => import('./pages/kpi-plan/kpi-review.component').then(m => m.KpiReviewComponent)
      },
      { path: 'kpi-administration/company-kpis', pathMatch: 'full', redirectTo: 'kpi-management/company-kpis' },
      {
        path: 'kpi-administration/attitude-evaluation-setup', canActivate: [PermissionGuard],
        canDeactivate: [(component: { canLeave: () => boolean | Promise<boolean> }) => component.canLeave()],
        data: { requiredRoles: ['CAN_MANAGE_ATTITUDE_CONFIGURATION'] },
        loadComponent: () => import('./pages/attitude-configuration/attitude-configuration.component').then(m => m.AttitudeConfigurationComponent)
      },
      {
        path: 'kpi-administration/review-periods',
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD'] },
        children: [
          { path: '', pathMatch: 'full', loadComponent: () => import('./pages/annual-review-period/review-period-list.component').then(m => m.ReviewPeriodListComponent) },
          { path: 'new', loadComponent: () => import('./pages/annual-review-period/review-period-form.component').then(m => m.ReviewPeriodFormComponent) },
          { path: ':id/edit', loadComponent: () => import('./pages/annual-review-period/review-period-form.component').then(m => m.ReviewPeriodFormComponent) },
          { path: ':id', data: { viewOnly: true }, loadComponent: () => import('./pages/annual-review-period/review-period-form.component').then(m => m.ReviewPeriodFormComponent) }
        ]
      },
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
        data: { requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING', 'CAN_MANAGE_ACCESS_CONTROL'] },
      },
      {
        path: 'sop/review/:id', component: SopReviewComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING', 'CAN_MANAGE_ACCESS_CONTROL'] },
      },
      {
        path: 'sop/quiz-review', component: SopQuizReviewComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING', 'CAN_MANAGE_ACCESS_CONTROL'] },
      },
      {
        path: 'sop/quiz-review/:id', component: SopQuizReviewComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING', 'CAN_MANAGE_ACCESS_CONTROL'] },
      },
      {
        path: 'sop/assign', component: SopAssignComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING'] },
      },
      {
        path: 'sop/scoreboard', component: SopScoreboardComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_TRAINING', 'CAN_ASSIGN_TRAINING'] },
      },
      {
        path: 'sop/trainer-access', component: TrainerAccessComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['CAN_MANAGE_ACCESS_CONTROL'] },
      },
      {
        path: 'my-learning', component: MyLearningComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
      },
      {
        path: 'my-learning/course/:id', component: CoursePlayerComponent,
        canActivate: [PermissionGuard],
        data: { requiredRoles: ['ROLE_USER'] },
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

