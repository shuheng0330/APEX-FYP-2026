import { organisationReviewTabs, reviewTasks, teamReviewTabs } from './review-task-directory';
import { routes } from '../../app.routes';

describe('Review task directory', () => {
  const testUser = ['ROLE_USER', 'CAN_MANAGE_COMPANY_KPI', 'CAN_REVISE_COMPANY_KPI', 'CAN_APPROVE_DEPARTMENT_KPI',
    'CAN_REVIEW_INDIVIDUAL_KPI', 'CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_ATTITUDE_EVALUATION'];
  const alice = ['ROLE_USER', 'CAN_MANAGE_DEPARTMENT_KPI', 'CAN_REVIEW_INDIVIDUAL_KPI', 'CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_ATTITUDE_EVALUATION'];
  const david = ['ROLE_USER', 'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE', 'CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS',
    'CAN_MANAGE_KPI_AMENDMENTS', 'CAN_PREPARE_DEPARTMENT_KPI_REVISION', 'CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_ATTITUDE_EVALUATION'];
  const has = (permissions: string[]) => (permission: string) => permissions.includes(permission);
  it('shows all applicable groups and tasks for the three sample permission sets', () => {
    expect(teamReviewTabs(has(testUser))).toEqual(['reviews', 'assistance', 'revisions', 'assessments', 'attitude']);
    expect(organisationReviewTabs(has(testUser))).toEqual(['department', 'revisions']);
    expect(reviewTasks(has(testUser)).map(t => t.id)).not.toContain('AMENDMENTS');
    expect(reviewTasks(has(testUser)).map(t => t.id)).not.toContain('INDIVIDUAL_INITIATION');
    expect(teamReviewTabs(has(alice))).toEqual(teamReviewTabs(has(testUser)));
    expect(organisationReviewTabs(has(alice))).toEqual(['revisions']);
    expect(reviewTasks(has(alice)).map(t => t.id)).toContain('DEPARTMENT');
    expect(reviewTasks(has(alice)).map(t => t.id)).not.toContain('DEPARTMENT_APPROVAL');
    expect(organisationReviewTabs(has(david))).toEqual(['assistance', 'revisions']);
    expect(teamReviewTabs(has(david))).toEqual(['assessments', 'attitude']);
    expect(reviewTasks(has(david)).map(t => t.id)).toContain('INDIVIDUAL_INITIATION');
    expect(reviewTasks(has(david)).map(t => t.id)).not.toContain('INDIVIDUAL_REVISION');
  });
  it('does not infer business authority from role names or employee levels', () => {
    const permission = has(['Super Admin', 'HR', 'MD', 'Manager', 'Executive']);
    expect(reviewTasks(permission)).toEqual([]);
    expect(teamReviewTabs(permission)).toEqual([]);
    expect(organisationReviewTabs(permission)).toEqual([]);
  });
  it('keeps single and combined permissions consistent with existing route guards', () => {
    const all = [...new Set([...testUser, ...alice, ...david])];
    for (const grants of [...all.map(p => [p]), all]) {
      const permission = has(grants);
      for (const task of reviewTasks(permission)) {
        const route = routes.find(r => r.path === '')!.children!.find(r => '/' + r.path === task.path)!;
        expect(route).withContext(task.id).toBeDefined();
        expect((route.data!['requiredRoles'] as string[]).some(permission)).withContext(task.id).toBeTrue();
        if (task.path.endsWith('/kpi-review')) expect(organisationReviewTabs(permission)).toContain(task.tab as 'revisions');
        if (task.path.endsWith('/team-reviews')) expect(teamReviewTabs(permission)).toContain(task.tab as 'reviews');
      }
    }
    expect(reviewTasks(has(['CAN_REVISE_COMPANY_KPI'])).map(t => t.id)).toEqual(['COMPANY_REVISION']);
    expect(reviewTasks(has(all)).length).toBe(16);
  });
});
