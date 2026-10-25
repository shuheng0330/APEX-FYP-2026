import { AnnualAppraisal, AnnualAppraisalActivity, AnnualRecommendation, annualRecommendations } from '../../models/annual-appraisal.model';

interface RecordedRecommendation {
  recommendation: AnnualRecommendation;
  justification: string;
}

export type AppraisalReturnComparison = { available: false } | {
  available: true;
  reason: string;
  before: RecordedRecommendation;
  after: RecordedRecommendation;
  recommendationChanged: boolean;
  justificationChanged: boolean;
};

export function appraisalReturnComparison(appraisal: AnnualAppraisal | null): AppraisalReturnComparison | null {
  if (appraisal?.status !== 'PENDING_REVIEW' || !appraisal.activity.some(event => event.action === 'RESUBMITTED')) return null;
  const unavailable: AppraisalReturnComparison = { available: false };
  const ids = appraisal.activity.map(event => event.id);
  if (ids.some(id => !Number.isSafeInteger(id) || id <= 0) || new Set(ids).size !== ids.length) return unavailable;

  // Event IDs preserve backend record order, including events with identical timestamps.
  const transitions = [...appraisal.activity].sort((a, b) => a.id - b.id)
    .filter(event => event.action !== 'DRAFT_CREATED' && event.action !== 'DRAFT_SAVED');
  const submissions = transitions.filter(event => event.action === 'SUBMITTED' || event.action === 'RESUBMITTED');
  const resubmitted = submissions[submissions.length - 1];
  if (resubmitted?.action !== 'RESUBMITTED') return null;
  const returned = transitions[transitions.length - 2];
  if (transitions[transitions.length - 1] !== resubmitted || returned?.action !== 'RETURNED'
    || typeof returned.reason !== 'string' || !returned.reason.trim()) return unavailable;

  const before = recordedValues(returned), after = recordedValues(resubmitted);
  if (!before || !after || after.recommendation !== appraisal.recommendation || after.justification !== appraisal.justification) return unavailable;
  return { available: true, reason: returned.reason, before, after,
    recommendationChanged: before.recommendation !== after.recommendation,
    justificationChanged: before.justification !== after.justification };
}

function recordedValues(event: AnnualAppraisalActivity): RecordedRecommendation | null {
  if (event.recommendation === null || !annualRecommendations.includes(event.recommendation)
    || typeof event.justification !== 'string' || !event.justification.trim()) return null;
  return { recommendation: event.recommendation, justification: event.justification };
}
