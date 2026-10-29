export const organisationPerformancePath = '/appraisals/organisation-performance';

export function organisationReturnUrl(value: string | null): string | null {
  if (!value || /[\\\s#]/.test(value)) return null;
  return value.split('?')[0] === organisationPerformancePath ? value : null;
}
