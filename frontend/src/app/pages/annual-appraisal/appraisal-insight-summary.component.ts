import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { InsightSummary } from '../../models/appraisal-insight.model';

@Component({
  selector: 'app-appraisal-insight-summary', standalone: true, imports: [CommonModule, TranslateModule],
  template: `
    @if (summary) {
      <div class="summary-sections">
      @for (section of sections; track section.field) {
        <section class="summary-section" [class.summary-lead]="section.field === 'performanceSummary'"
          [class.summary-full]="section.field === 'performanceSummary' || section.field === 'performanceTrend'">
          <h4>{{ 'AI_INSIGHT.' + section.label | translate }}</h4>
          @if (section.field === 'keyStrengths' || section.field === 'areasForImprovement') {
            <ul>@for (statement of summary[section.field]; track $index) { <li><ng-container [ngTemplateOutlet]="text" [ngTemplateOutletContext]="{ $implicit: statement.text }" /></li> }</ul>
          } @else {
            @for (statement of summary[section.field]; track $index) { <p><ng-container [ngTemplateOutlet]="text" [ngTemplateOutletContext]="{ $implicit: statement.text }" /></p> }
          }
        </section>
      }
      <section class="summary-section summary-full"><h4>{{ 'AI_INSIGHT.CONSIDERATIONS' | translate }}</h4>
        <div class="consideration-grid">
          <div><h5>{{ 'AI_INSIGHT.PROMOTION' | translate }}</h5><p><ng-container [ngTemplateOutlet]="text" [ngTemplateOutletContext]="{ $implicit: summary.appraisalConsiderations.promotionDiscussion.text }" /></p></div>
          <div><h5>{{ 'AI_INSIGHT.SALARY' | translate }}</h5><p><ng-container [ngTemplateOutlet]="text" [ngTemplateOutletContext]="{ $implicit: summary.appraisalConsiderations.salaryIncrementDiscussion.text }" /></p></div>
        </div>
      </section>
      <section class="summary-section summary-full"><h4>{{ 'AI_INSIGHT.GAPS' | translate }}</h4>
        <ul>@for (statement of summary.informationGaps; track $index) { <li><ng-container [ngTemplateOutlet]="text" [ngTemplateOutletContext]="{ $implicit: statement.text }" /></li> }</ul>
      </section>
      </div>
    }
    <ng-template #text let-value>@for (part of parts(value); track $index) { @if (part.emphasis) {<strong>{{ part.text }}</strong>} @else {<span>{{ part.text }}</span>} }</ng-template>
  `,
  styles: [`
    :host { display:block; min-width:0; color:var(--tbm-text); }
    .summary-sections { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px 32px; }
    .summary-section { min-width:0; padding-top:20px; border-top:1px solid var(--tbm-border); }
    .summary-full { grid-column:1 / -1; }
    .summary-lead { padding-top:0; border:0; }
    h4 { font-size:16px; font-weight:700; margin:0 0 10px; color:var(--tbm-text); }
    h5 { font-size:14px; font-weight:700; margin:0 0 6px; color:var(--tbm-text); }
    p, li { font-size:14px; white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.7; }
    p { margin:0; max-width:100ch; }
    p + p, li + li { margin-top:8px; }
    ul { margin:0; padding-left:20px; }
    .consideration-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px 32px; }
    .consideration-grid > div { min-width:0; border-left:2px solid var(--tbm-border); padding-left:14px; }
    @media(max-width:600px) { .summary-sections, .consideration-grid { grid-template-columns:minmax(0,1fr); gap:18px; } }
  `]
})
export class AppraisalInsightSummaryComponent {
  @Input() summary: InsightSummary | null = null;
  @Input() emphasizeNumbers = false;
  parts(text: string): { text: string; emphasis: boolean }[] {
    if (!this.emphasizeNumbers) return [{ text, emphasis: false }];
    const parts: { text: string; emphasis: boolean }[] = [];
    let position = 0;
    for (const match of text.matchAll(/\b\d+(?:\.\d+)?(?:\s*%|\s*\/\s*(?:100|5)\b|\s+out of\s+(?:100|5)\b)/g)) {
      if (match.index > position) parts.push({ text: text.slice(position, match.index), emphasis: false });
      parts.push({ text: match[0], emphasis: true }); position = match.index + match[0].length;
    }
    if (position < text.length) parts.push({ text: text.slice(position), emphasis: false });
    return parts;
  }
  readonly sections = [
    { field: 'performanceSummary', label: 'SUMMARY' }, { field: 'keyStrengths', label: 'STRENGTHS' },
    { field: 'areasForImprovement', label: 'IMPROVEMENTS' }, { field: 'performanceTrend', label: 'TREND' }
  ] as const;
}
