import { ParamMap } from '@angular/router';

export const performanceViewPermissions = ['CAN_VIEW_TEAM_PERFORMANCE', 'CAN_VIEW_DEPARTMENT_PERFORMANCE', 'CAN_VIEW_ORGANISATION_PERFORMANCE'];

export function performanceId(params: ParamMap, name: string): number | null {
  const values = params.getAll(name);
  if (!values.length) return null;
  if (values.length !== 1 || !/^[1-9]\d*$/.test(values[0]) || !Number.isSafeInteger(Number(values[0]))) throw new Error('Invalid selection');
  return Number(values[0]);
}
export function performanceEmployeeId(value: string | null): value is string {
  return !!value && /^[a-f\d]{8}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{12}$/i.test(value);
}
export function performanceReturnUrl(value: string | null): string {
  if (!value || !value.startsWith('/') || /[\\\s#]/.test(value) || value.startsWith('//')) return '/my-performance/dashboard';
  const path = value.split('?')[0];
  return /^\/(?:team-performance\/dashboard|team-performance\/team-appraisals(?:\/[1-9]\d*\/[1-9]\d*)?|appraisals\/review(?:\/[1-9]\d*)?)$/.test(path)
    ? value : '/my-performance/dashboard';
}
