export const ATTITUDE_CONFIGURATION_PERMISSION = 'CAN_MANAGE_ATTITUDE_CONFIGURATION';
export type AttitudeFormat = 'MANAGER' | 'SALES' | 'OTHERS';
export interface AttitudeCriterion {
  id?: number;
  name: string | null;
  description: string | null;
  criterionType: 'SHARED_CORE_VALUE' | 'FORMAT_SPECIFIC';
  evaluationFormat: AttitudeFormat | null;
  active: boolean;
  displayOrder?: number;
}
export interface AttitudeRating { point: number; label: string | null; description: string | null; }
export interface AttitudeRoleMapping { roleId: number; roleName?: string; departmentName?: string | null; evaluationFormat: AttitudeFormat; }
export interface AttitudeConfigurationRequest {
  name: string | null;
  criteria: AttitudeCriterion[];
  ratingDefinitions: AttitudeRating[];
  roleMappings: AttitudeRoleMapping[];
}
export interface AttitudeConfiguration extends AttitudeConfigurationRequest {
  id: number;
  status: 'DRAFT' | 'PUBLISHED';
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
  publishedAt: string | null;
  publishedBy: string | null;
}
export interface AttitudeRoleOption { roleId: number; roleName: string; departmentName?: string | null; evaluationFormat: AttitudeFormat | null; }
export interface AttitudeReviewPeriodOption { id: number; name: string; status: 'OPEN'; }
export interface AttitudeConfigurationOptions { formats: AttitudeFormat[]; roles: AttitudeRoleOption[]; reviewPeriods: AttitudeReviewPeriodOption[]; }
export interface AttitudePeriodConfiguration {
  reviewPeriodId: number;
  reviewPeriodName: string;
  reviewPeriodStatus: 'DRAFT' | 'UPCOMING' | 'OPEN' | 'CLOSED';
  configuration: AttitudeConfiguration | null;
  unmappedRoleNames: string[];
  canBindInitially: boolean;
}
export interface AttitudeValidationIssue { section: 'details' | 'ratings' | 'criteria' | 'roles'; key: string; }

export function attitudeTextError(value: string | null | undefined, required: boolean, limit: number): string | null {
  if (required && !value?.trim()) return 'ATTITUDE_SETUP.REQUIRED_FIELD';
  return (value?.trim().length ?? 0) > limit ? 'ATTITUDE_SETUP.FIELD_LIMIT' : null;
}

export function emptyAttitudeConfiguration(): AttitudeConfigurationRequest {
  return { name: '', criteria: [], roleMappings: [],
    ratingDefinitions: [5, 4, 3, 2, 1].map(point => ({ point, label: '', description: '' })) };
}

// Mirror publication readiness, without requiring every eligible Role to be mapped.
export function attitudePublicationIssues(form: AttitudeConfigurationRequest, options: AttitudeConfigurationOptions): AttitudeValidationIssue[] {
  const issues: AttitudeValidationIssue[] = [];
  if (!form.name?.trim()) issues.push({ section: 'details', key: 'NAME_REQUIRED' });
  if (form.name && form.name.length > 255) issues.push({ section: 'details', key: 'NAME_LIMIT' });
  if ([1, 2, 3, 4, 5].some(point => !form.ratingDefinitions.some(r => r.point === point && r.label?.trim() && r.description?.trim()))) {
    issues.push({ section: 'ratings', key: 'RATINGS_REQUIRED' });
  }
  if (form.ratingDefinitions.some(r => (r.label?.length ?? 0) > 255 || (r.description?.length ?? 0) > 10000)) {
    issues.push({ section: 'ratings', key: 'TEXT_LIMIT' });
  }
  if (form.criteria.some(c => c.active && (!c.name?.trim() || !c.description?.trim()))) {
    issues.push({ section: 'criteria', key: 'CRITERIA_REQUIRED' });
  }
  if (form.criteria.some(c => (c.name?.length ?? 0) > 255 || (c.description?.length ?? 0) > 10000)) {
    issues.push({ section: 'criteria', key: 'TEXT_LIMIT' });
  }
  if (options.formats.some(format => !form.criteria.some(c => c.active &&
    (c.criterionType === 'SHARED_CORE_VALUE' || c.evaluationFormat === format)))) {
    issues.push({ section: 'criteria', key: 'FORMATS_REQUIRED' });
  }
  if (form.roleMappings.some(m => !options.roles.some(r => r.roleId === m.roleId))) {
    issues.push({ section: 'roles', key: 'INELIGIBLE_ROLE' });
  }
  return issues;
}
