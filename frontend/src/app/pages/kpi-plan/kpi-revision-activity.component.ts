import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { KpiRevision } from '../../models/kpi-revision.model';

interface ActivityDetail {
  label: string;
  actor: string | null;
  at: string | null;
  reason?: string | null;
}

@Component({
  selector: 'app-kpi-revision-activity', standalone: true,
  imports: [CommonModule, TranslateModule],
  styleUrl: './kpi-revision-activity.component.scss',
  template: `
    @if (events.length) {
      <ol class="activity-timeline" [attr.aria-label]="'KPI_REVISIONS.DECISIONS' | translate">
        @for (event of events; track $index) {
          <li>
            <div class="event-heading">
              <strong>{{ 'KPI_REVISIONS.DECISION.' + event.action | translate }}</strong>
              <time [attr.datetime]="event.at">{{ event.at | date:'dd MMM yyyy, h:mm a' }}</time>
            </div>
            <span class="event-actor">{{ actorName(event.actor) }}</span>
            @if (event.reason) { <p class="event-reason">{{ event.reason }}</p> }
          </li>
        }
      </ol>
    }
    @if (additionalDetails.length) {
      <div class="additional-details">
        <h4>{{ 'KPI_REVISIONS.ADDITIONAL_DETAILS' | translate }}</h4>
        <dl>
          @for (detail of additionalDetails; track detail.label) {
            <div>
              <dt>{{ detail.label | translate }}</dt>
              <dd>
                @if (detail.actor) { <span>{{ actorName(detail.actor) }}</span> }
                @if (detail.at) { <time [attr.datetime]="detail.at">{{ detail.at | date:'dd MMM yyyy, h:mm a' }}</time> }
                @if (detail.reason) { <span class="event-reason">{{ detail.reason }}</span> }
              </dd>
            </div>
          }
        </dl>
      </div>
    }
    @if (!events.length && !additionalDetails.length) {
      <p class="activity-empty">{{ 'KPI_REVISIONS.NO_ACTIVITY' | translate }}</p>
    }
  `
})
export class KpiRevisionActivityComponent implements OnChanges {
  @Input({ required: true }) revision!: KpiRevision;
  @Input() returnFeedbackVisible = false;
  events: KpiRevision['decisionHistory'] = [];
  additionalDetails: ActivityDetail[] = [];

  ngOnChanges() {
    const version = this.revision;
    this.events = [...version.decisionHistory].sort((a, b) => Date.parse(a.at) - Date.parse(b.at));
    this.additionalDetails = [];
    const add = (label: string, actions: string[], actor: string | null, at: string | null, reason?: string | null) => {
      if (!actor && !at && !reason) return;
      const recorded = this.events.some(event => actions.includes(event.action)
        && (!actor || event.actor === actor) && (!at || this.sameDisplayedTime(event.at, at))
        && (!reason || event.reason === reason));
      if (!recorded) this.additionalDetails.push({ label, actor, at, reason });
    };
    add('KPI_REVISIONS.REQUESTER', ['CREATED'], version.requestedBy, version.requestedBy ? version.createdAt : null);
    if (version.createdBy !== version.requestedBy) {
      add('KPI_REVISIONS.CREATED_BY', ['CREATED'], version.createdBy, version.createdAt);
    }
    add('KPI_REVISIONS.PREPARER', ['PREPARED', 'CREATED'], version.preparedBy, version.preparedAt);
    add('KPI_REVISIONS.SUBMITTED_ON', ['SUBMITTED'], null, version.submittedAt);
    add('KPI_REVISIONS.APPROVER', ['MD_APPROVED', 'SUPERIOR_APPROVED'], version.approvedBy, version.approvedAt);
    add('KPI_REVISIONS.CONFIRMER', ['CONFIRMED', 'SUPERIOR_APPROVED'], version.confirmedBy, version.confirmedAt);
    add('KPI_REVISIONS.CANCELLED_BY', ['CANCELLED'], version.cancelledBy, version.cancelledAt);
    if (version.effectiveAt && !this.events.some(event => event.to === 'EFFECTIVE' && this.sameDisplayedTime(event.at, version.effectiveAt!))) {
      this.additionalDetails.push({ label: 'KPI_REVISIONS.EFFECTIVE_ON', actor: null, at: version.effectiveAt });
    }
    if (!this.returnFeedbackVisible) {
      add('DEPARTMENT_KPI.RETURN_REASON', ['RETURNED'], version.latestReturnedBy, version.latestReturnedAt, version.latestReturnReason);
    }
    // Metadata is shown as facts only, never converted into invented timeline events.
    if (version.revisionSuperiorId) {
      this.additionalDetails.push({ label: 'INDIVIDUAL_KPI.SUPERIOR', actor: version.revisionSuperiorId, at: null });
    }
    if (version.updatedAt && !this.events.some(event => event.actor === version.updatedBy && this.sameDisplayedTime(event.at, version.updatedAt))) {
      this.additionalDetails.push({ label: 'KPI_REVISIONS.LAST_UPDATED', actor: version.updatedBy, at: version.updatedAt });
    }
  }

  actorName(actor: string) { return this.revision.actorNames?.[actor] || '-'; }

  private sameDisplayedTime(a: string, b: string) {
    // Audit fields and events can be recorded moments apart; the UI displays minute precision.
    return Math.floor(Date.parse(a) / 60000) === Math.floor(Date.parse(b) / 60000);
  }
}
