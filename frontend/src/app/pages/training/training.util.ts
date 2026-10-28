import {
  TrainingDelivery, TrainingSession, TrainingSessionKind, TrainingSessionStatus, TrainingSessionTiming, TrainingType
} from '../../models/training-session.model';

export const KIND_LABELS: Record<TrainingSessionKind, string> = { TRAINING: 'Training', STAFF_SHARING: 'Staff Sharing' };
export const DELIVERY_LABELS: Record<TrainingDelivery, string> = { PHYSICAL: 'Physical', HYBRID: 'Hybrid', ONLINE: 'Online' };
export const TRAINING_TYPE_LABELS: Record<TrainingType, string> = {
  PRODUCT: 'Product Training', SKILL_BASED: 'Skill-Based Training', OTHER: 'Other'
};
export const STATUS_LABELS: Record<TrainingSessionStatus, string> = {
  SCHEDULED: 'Scheduled', CANCELLED: 'Cancelled', ARCHIVED: 'Archived'
};
export const TIMING_LABELS: Record<TrainingSessionTiming, string> = {
  UPCOMING: 'Upcoming', IN_PROGRESS: 'In progress', ENDED: 'Ended'
};

/** Shown beside the training type selector (FR-17-05). */
export const TRAINING_TYPE_NOTE = "Product Training and Skill-Based Training count towards attendees' training KPI; "
  + 'Other does not. Training hours are recorded for every type.';

/** The most seats a session may have, matching the server. */
export const MAX_CAPACITY = 1000;

/** Whether the session is classified by training type: Physical and Hybrid training only (FR-17-01). */
export function needsTrainingType(kind: TrainingSessionKind, delivery: TrainingDelivery): boolean {
  return kind === 'TRAINING' && delivery !== 'ONLINE';
}

/** "09:00" from "09:00:00". */
export function hhmm(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : '';
}

/** "09:00–12:00" */
export function timeRange(s: Pick<TrainingSession, 'startTime' | 'endTime'>): string {
  return `${hhmm(s.startTime)}–${hhmm(s.endTime)}`;
}

/** A calendar day (yyyy-MM-dd) as a Date at local midnight, with no time-zone shift. */
export function parseDay(day: string): Date {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** A Date as its calendar day, yyyy-MM-dd, in local time. */
export function dayKey(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Midnight at the start of the date's day. */
export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** The Monday of the date's week: the calendar's weeks run Monday to Sunday. */
export function startOfWeek(date: Date): Date {
  return addDays(date, -((date.getDay() + 6) % 7));
}

/**
 * A Google Maps search for the venue of a session held in person: its pinned location, or else the venue's text.
 * Works without a Maps key.
 */
export function mapsLink(s: TrainingSession): string | null {
  if (s.delivery === 'ONLINE') return null;
  if (s.latitude != null && s.longitude != null) {
    return `https://www.google.com/maps/search/?api=1&query=${s.latitude},${s.longitude}`;
  }
  return s.venue ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(s.venue)}` : null;
}

/** Whether the text is a single web address, such as an online meeting link. */
export function isLink(text: string | null | undefined): boolean {
  return !!text && /^https?:\/\/\S+$/i.test(text.trim());
}
