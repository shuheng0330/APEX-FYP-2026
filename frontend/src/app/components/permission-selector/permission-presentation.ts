import { TranslateService } from '@ngx-translate/core';
import { TranslatedAuthority } from '../../models/access-control.model';

export const permissionGroups = ["BASIC","STAFF","ACCESS","TRAINING","PERIOD","PLANNING","ASSESSMENT","APPRAISAL","ANALYTICS","OTHER"] as const;
export type PermissionGroup = typeof permissionGroups[number];
export function isHiddenPermission(permission: TranslatedAuthority): boolean {
  return permission.name === 'CAN_MANAGE_EVALUATION' || permission.name === 'CAN_MANAGE_EVALUATION_CYCLE'
    || permission.name === 'CAN_ENTER_COMPANY_KPI_RESULTS';
}
export const permissionCatalogue: Record<string, PermissionGroup> = {
  ROLE_USER: 'BASIC',
  CAN_VIEW_STAFF: 'STAFF',
  CAN_MANAGE_STAFF: 'STAFF',
  CAN_MANAGE_ORG_CHART: 'STAFF',
  CAN_MANAGE_ROLE: 'ACCESS',
  CAN_VIEW_ACCESS_CONTROL: 'ACCESS',
  CAN_MANAGE_ACCESS_CONTROL: 'ACCESS',
  CAN_MANAGE_COMPETENCY: 'TRAINING',
  CAN_MANAGE_TRAINING: 'TRAINING',
  CAN_ASSIGN_TRAINING: 'TRAINING',
  CAN_MANAGE_LEARNING_MATERIAL: 'TRAINING',
  CAN_MANAGE_EVALUATION: 'TRAINING',
  CAN_MANAGE_EVALUATION_CYCLE: 'TRAINING',
  CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD: 'PERIOD',
  CAN_MANAGE_COMPANY_KPI: 'PLANNING',
  CAN_MANAGE_DEPARTMENT_KPI: 'PLANNING',
  CAN_APPROVE_DEPARTMENT_KPI: 'PLANNING',
  CAN_REVIEW_INDIVIDUAL_KPI: 'PLANNING',
  CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE: 'PLANNING',
  CAN_MANAGE_KPI_AMENDMENTS: 'PLANNING',
  CAN_REVISE_COMPANY_KPI: 'PLANNING',
  CAN_PREPARE_DEPARTMENT_KPI_REVISION: 'PLANNING',
  CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS: 'PLANNING',
  CAN_ENTER_COMPANY_KPI_RESULTS: 'ASSESSMENT',
  CAN_REVIEW_KPI_ASSESSMENT: 'ASSESSMENT',
  CAN_MANAGE_ATTITUDE_CONFIGURATION: 'ASSESSMENT',
  CAN_REVIEW_ATTITUDE_EVALUATION: 'ASSESSMENT',
  CAN_SUBMIT_ANNUAL_APPRAISAL: 'APPRAISAL',
  CAN_REVIEW_ANNUAL_APPRAISAL: 'APPRAISAL',
  CAN_VIEW_TEAM_PERFORMANCE: 'ANALYTICS',
  CAN_VIEW_DEPARTMENT_PERFORMANCE: 'ANALYTICS',
  CAN_VIEW_ORGANISATION_PERFORMANCE: 'ANALYTICS',
};

export function permissionPresentation(permission: TranslatedAuthority, translate: TranslateService): TranslatedAuthority {
  if (permissionCatalogue[permission.name]) {
    return { ...permission, label: translate.instant('ACCESS_PERMISSIONS.ITEMS.' + permission.name + '.LABEL'),
      description: translate.instant('ACCESS_PERMISSIONS.ITEMS.' + permission.name + '.DESCRIPTION') };
  }
  const label = permission.label && !permission.label.startsWith('auth.') && permission.label !== permission.name
    ? permission.label : permission.name.replace(/^CAN_/, '').replace(/_/g, ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase());
  const description = permission.description && !permission.description.startsWith('auth.')
    ? permission.description : translate.instant('ACCESS_PERMISSIONS.UNKNOWN');
  return { ...permission, label, description };
}
