import { CommonModule, formatNumber } from '@angular/common';
import { Component, Inject, Input, LOCALE_ID, OnChanges, ViewChild } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { OrganisationPerformance } from '../../models/organisation-performance.model';

type Distribution = OrganisationPerformance['distribution'];
type Bin = Distribution['bins'][number];

@Component({
  selector: 'app-organisation-score-distribution', standalone: true,
  imports: [CommonModule, TranslateModule, BaseChartDirective], providers: [provideCharts(withDefaultRegisterables())],
  template: `
    @if (hasResults) {
      <div class="distribution-chart"><canvas baseChart [data]="data" [options]="options" [type]="'bar'"
        role="img" tabindex="0" [attr.aria-label]="'ORG_PERFORMANCE.HISTOGRAM.ACCESSIBLE' | translate"
        aria-describedby="org-distribution-help org-distribution-selection"
        (focus)="select(0)" (keydown)="navigate($event)"></canvas></div>
      <p id="org-distribution-help" class="sr-only">{{ 'ORG_PERFORMANCE.HISTOGRAM.KEYBOARD_HELP' | translate }}</p>
      <p id="org-distribution-selection" class="sr-only" role="status" aria-live="polite">{{ selectedSummary }}</p>
      <table class="sr-only">
        <caption>{{ 'TEAM_PERFORMANCE.METRICS.' + distribution!.metric | translate }}</caption>
        <thead><tr><th>{{ 'ORG_PERFORMANCE.HISTOGRAM.X_AXIS' | translate }}</th><th>{{ 'ORG_PERFORMANCE.HISTOGRAM.Y_AXIS' | translate }}</th></tr></thead>
        <tbody>@for (bin of distribution!.bins; track bin.lowerInclusive) { <tr><th scope="row">{{ rangeLabel(bin) }}</th><td>{{ bin.employees }}</td></tr> }</tbody>
      </table>
    } @else { <p class="empty" role="status">{{ 'ORG_PERFORMANCE.HISTOGRAM.EMPTY' | translate }}</p> }
    @if (distribution) { <p class="help distribution-caption">{{ 'ORG_PERFORMANCE.HISTOGRAM.CAPTION' | translate:{ count: distribution.employeesWithResults, missing: distribution.missingResults } }}</p> }
  `,
  styleUrls: ['../annual-review-period/review-period.scss', './organisation-score-distribution.component.scss']
})
export class OrganisationScoreDistributionComponent implements OnChanges {
  @Input() distribution: Distribution | null = null;
  @ViewChild(BaseChartDirective) chart?: BaseChartDirective<'bar'>;
  data: ChartConfiguration<'bar'>['data'] = { labels: [], datasets: [] };
  options: ChartConfiguration<'bar'>['options'] = {};
  selectedSummary = ''; selectedIndex = -1;
  constructor(private translate: TranslateService, @Inject(LOCALE_ID) private locale: string) {}
  get hasResults() { return !!this.distribution?.bins.length && this.distribution.employeesWithResults > 0; }
  rangeLabel(bin: Bin) {
    if (bin.lowerInclusive === 0 && !bin.upperInclusive) return this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.BELOW', { upper: bin.upper });
    return this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.' + (bin.upperInclusive ? 'INCLUSIVE_RANGE' : 'RANGE'), { lower: bin.lowerInclusive, upper: bin.upper });
  }
  axisLabel(bin: Bin) {
    return bin.lowerInclusive === 0 && !bin.upperInclusive ? this.rangeLabel(bin)
      : this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.BAND', { lower: bin.lowerInclusive, upper: bin.upperInclusive ? bin.upper : bin.upper - 1 });
  }
  tooltipLines(index: number): string[] {
    const bin = this.distribution?.bins[index]; if (!bin) return [];
    const count = this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.COUNT', { count: bin.employees });
    const total = this.distribution!.employeesWithResults;
    return [count, ...(total > 0 ? [this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.PERCENT', { percent: formatNumber(bin.employees / total * 100, this.locale, '1.0-1') })] : [])];
  }
  ngOnChanges() {
    this.selectedSummary = ''; this.selectedIndex = -1;
    const bins = this.distribution?.bins ?? [];
    const color = this.distribution?.metric === 'KPI' ? '#2456a6' : this.distribution?.metric === 'ATTITUDE' ? '#087a6e' : '#7950c9';
    this.data = { labels: bins.map(bin => this.axisLabel(bin)),
      datasets: [{ label: this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.Y_AXIS'), data: bins.map(bin => bin.employees),
        backgroundColor: color, hoverBackgroundColor: color, borderRadius: 3, maxBarThickness: 110, categoryPercentage: 0.88, barPercentage: 1 }] };
    this.options = { responsive: true, maintainAspectRatio: false, indexAxis: 'x',
      interaction: { mode: 'index', axis: 'x', intersect: false },
      scales: {
        x: { title: { display: true, text: this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.X_AXIS') }, grid: { display: false }, ticks: { autoSkip: false, maxRotation: 0, font: context => ({ size: context.chart.width < 400 ? 10 : 11 }) } },
        y: { beginAtZero: true, suggestedMax: Math.max(1, ...bins.map(bin => bin.employees)),
          title: { display: true, text: this.translate.instant('ORG_PERFORMANCE.HISTOGRAM.Y_AXIS') }, ticks: { precision: 0 } }
      },
      onClick: (_, elements) => { if (elements.length) this.select(elements[0].index); },
      plugins: { legend: { display: false }, tooltip: { callbacks: {
        title: items => { const bin = bins[items[0]?.dataIndex]; return bin ? [this.translate.instant('TEAM_PERFORMANCE.METRICS.' + this.distribution!.metric), this.rangeLabel(bin)] : []; },
        label: item => this.tooltipLines(item.dataIndex)
      } } }
    };
  }
  select(index: number) {
    const bin = this.distribution?.bins[index]; if (!bin) return;
    this.selectedIndex = index;
    this.selectedSummary = [this.translate.instant('TEAM_PERFORMANCE.METRICS.' + this.distribution!.metric), this.rangeLabel(bin), ...this.tooltipLines(index)].join('. ');
    const chart = this.chart?.chart; if (!chart) return;
    const elements = [{ datasetIndex: 0, index }], point = chart.getDatasetMeta(0).data[index];
    chart.setActiveElements(elements); chart.tooltip?.setActiveElements(elements, point ? { x: point.x, y: point.y } : { x: 0, y: 0 }); chart.update('none');
  }
  navigate(event: KeyboardEvent) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || !this.hasResults) return;
    event.preventDefault(); const last = this.distribution!.bins.length - 1;
    this.select(event.key === 'Home' ? 0 : event.key === 'End' ? last : Math.max(0, Math.min(last, this.selectedIndex + (event.key === 'ArrowRight' ? 1 : -1))));
  }
}
