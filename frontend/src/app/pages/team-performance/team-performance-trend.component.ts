import { CommonModule, formatDate } from '@angular/common';
import { Component, Inject, Input, LOCALE_ID, OnChanges, ViewChild } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { TeamMetric, TeamPeriod, TeamTrendPoint } from '../../models/team-performance.model';

export interface PerformanceTrendSeries { name: string; points: TeamTrendPoint[]; }

@Component({
  selector: 'app-team-performance-trend', standalone: true,
  imports: [CommonModule, TranslateModule, BaseChartDirective], providers: [provideCharts(withDefaultRegisterables())],
  template: `
    @if (hasResults) {
      <div class="annual-trend-chart"><canvas baseChart [data]="data" [options]="options" [type]="'line'"
        role="img" tabindex="0" [attr.aria-label]="accessibleLabel | translate"
        [attr.aria-describedby]="chartId + '-help ' + chartId + '-selection'" (focus)="select(firstIndex)" (keydown)="navigate($event)"></canvas></div>
      <p [id]="chartId + '-help'" class="sr-only">{{ 'ANNUAL_TREND.KEYBOARD_HELP' | translate }}</p>
      <p [id]="chartId + '-selection'" class="sr-only" role="status" aria-live="polite">{{ selectedSummary }}</p>
    } @else { <p class="empty">{{ 'TEAM_PERFORMANCE.NO_TREND' | translate }}</p> }
  `,
  styleUrls: ['../annual-review-period/review-period.scss', '../annual-appraisal/annual-performance-trend.component.scss']
})
export class TeamPerformanceTrendComponent implements OnChanges {
  @Input() points: TeamTrendPoint[] = [];
  @Input() period: TeamPeriod | null = null;
  @Input() metric: TeamMetric = 'FINAL';
  @Input() years = 5;
  @Input() chartId = 'team-trend';
  @Input() accessibleLabel = 'TEAM_PERFORMANCE.TREND_ACCESSIBLE';
  @Input() series: PerformanceTrendSeries[] | null = null;
  @Input() periodAxis = false;
  @ViewChild(BaseChartDirective) chart?: BaseChartDirective<'line'>;
  slots: (TeamTrendPoint | null)[] = [];
  data: ChartConfiguration<'line'>['data'] = { labels: [], datasets: [] };
  options: ChartConfiguration<'line'>['options'] = {};
  selectedSummary = ''; selectedIndex = -1;
  constructor(private translate: TranslateService, @Inject(LOCALE_ID) private locale: string) {}
  average(point: TeamTrendPoint) { return this.metric === 'KPI' ? point.summary.kpi : this.metric === 'ATTITUDE' ? point.summary.attitude : point.summary.finalScore; }
  get lines(): PerformanceTrendSeries[] { return this.series ?? [{ name: this.translate.instant('TEAM_PERFORMANCE.METRICS.' + this.metric), points: this.points }]; }
  pointAt(index: number, line: PerformanceTrendSeries) { return line.points.find(p => p.period.id === this.slots[index]?.period.id); }
  get hasResults() { return this.slots.some((_, i) => this.lines.some(line => { const p = this.pointAt(i, line); return p && this.average(p).score !== null; })); }
  get firstIndex() { return this.slots.findIndex(p => p !== null); }
  ngOnChanges() {
    this.slots = []; this.selectedSummary = ''; this.selectedIndex = -1;
    const anchor = Number(this.period?.endDate.slice(0, 4));
    const unique = new Map(this.lines.flatMap(line => line.points).map(p => [p.period.id, p]));
    const records = [...unique.values()].filter(p => p.period.endDate <= (this.period?.endDate ?? '')
      && Number(p.period.endDate.slice(0, 4)) >= anchor - this.years + 1)
      .sort((a, b) => a.period.endDate.localeCompare(b.period.endDate) || a.period.id - b.period.id);
    const labels: (string | string[])[] = [];
    if (records.length) for (let year = Number(records[0].period.endDate.slice(0, 4)); year <= anchor; year++) {
      const entries = records.filter(p => Number(p.period.endDate.slice(0, 4)) === year);
      if (!entries.length) { this.slots.push(null); labels.push(String(year)); }
      entries.forEach((p, i) => {
        this.slots.push(p);
        const sameDate = entries.filter(entry => entry.period.endDate === p.period.endDate).length > 1;
        labels.push(this.periodAxis ? [formatDate(p.period.endDate, 'dd MMM yyyy', this.locale), ...(sameDate ? [p.period.name.length > 24 ? p.period.name.slice(0, 24) + '...' : p.period.name] : [])]
          : entries.length > 1 ? `${year} (${i + 1})` : String(year));
      });
    }
    const color = this.metric === 'KPI' ? '#2456a6' : this.metric === 'ATTITUDE' ? '#0d9488' : '#8b5cf6';
    const colors = ['#2456a6', '#087a6e', '#7950c9', '#b66a00', '#ad315c', '#246b83'];
    this.data = { labels, datasets: this.lines.map((line, i) => ({ label: line.name,
      data: this.slots.map((_, index) => { const p = this.pointAt(index, line); return p ? this.average(p).score : null; }),
      borderColor: this.series ? colors[i % colors.length] : color, backgroundColor: this.series ? colors[i % colors.length] : color,
      borderDash: i >= colors.length ? [5, 3] : [], borderWidth: 2, pointRadius: 4, spanGaps: false, tension: 0 })) };
    this.options = { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', axis: 'x', intersect: false },
      scales: { y: { min: 0, max: 100 }, ...(this.periodAxis ? { x: { ticks: { maxRotation: 0, autoSkip: true, maxTicksLimit: 6, font: { size: 11 } } } } : {}) }, onClick: (_, elements) => { if (elements.length) this.select(elements[0].index); },
      plugins: { legend: { display: !!this.series, position: 'bottom', ...(this.periodAxis ? { labels: { boxWidth: 12, boxHeight: 3, padding: 12 } } : {}) }, tooltip: { callbacks: {
        title: items => this.title(items[0]?.dataIndex),
        label: item => item.dataset.label + (this.periodAxis && this.series ? ' - ' + this.translate.instant('TEAM_PERFORMANCE.METRICS.' + this.metric) : '') + ': ' + (item.parsed.y === null ? this.translate.instant('ANNUAL_APPRAISAL.PENDING') : item.parsed.y.toFixed(2) + '%'),
        afterBody: items => this.footer(items[0]?.dataIndex)
      } } } };
  }
  title(index: number) {
    const p = this.slots[index]?.period;
    if (!p) return [];
    // Wrap long period names to keep chart tooltips usable on small screens.
    const name = p.name.match(/.{1,32}(?:\s|$)|.{1,32}/g) ?? [p.name];
    return [...name.map(s => s.trim()), formatDate(p.startDate, 'dd MMM yyyy', this.locale) + ' - ' + formatDate(p.endDate, 'dd MMM yyyy', this.locale)];
  }
  footer(index: number) {
    const p = this.slots[index];
    return p ? [this.translate.instant(p.period.id === this.period?.id ? 'ANNUAL_TREND.CURRENT' : 'ANNUAL_TREND.HISTORICAL'),
      ...this.lines.flatMap(line => { const point = this.pointAt(index, line); return point ? [(this.series ? line.name + ': ' : '') + this.translate.instant('TEAM_PERFORMANCE.CONTRIBUTORS', { count: this.average(point).contributingEmployees })] : []; }),
      ...(p.period.name.includes('[UAT SIMULATED]') ? [this.translate.instant('ANNUAL_TREND.SIMULATED')] : [])] : [];
  }
  select(index: number) {
    const p = this.slots[index]; if (!p) return;
    this.selectedIndex = index;
    const scores = this.lines.map(line => { const point = this.pointAt(index, line); return point ? this.average(point).score : null; });
    this.selectedSummary = [...this.title(index), this.translate.instant('TEAM_PERFORMANCE.METRICS.' + this.metric),
      ...scores.map((score, i) => this.lines[i].name + ': ' + (score === null ? this.translate.instant('ANNUAL_APPRAISAL.PENDING') : score.toFixed(2) + '%')), ...this.footer(index)].join('. ');
    const chart = this.chart?.chart; if (!chart) return;
    const elements = scores.flatMap((score, datasetIndex) => score === null ? [] : [{ datasetIndex, index }]);
    const point = chart.getDatasetMeta(elements[0]?.datasetIndex ?? 0).data[index];
    chart.setActiveElements(elements); chart.tooltip?.setActiveElements(elements, point ? { x: point.x, y: point.y } : { x: 0, y: 0 }); chart.update('none');
  }
  navigate(event: KeyboardEvent) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    const indices = this.slots.flatMap((p, i) => p ? [i] : []); if (!indices.length) return;
    event.preventDefault(); const position = Math.max(0, indices.indexOf(this.selectedIndex));
    this.select(event.key === 'Home' ? indices[0] : event.key === 'End' ? indices[indices.length - 1]
      : indices[Math.max(0, Math.min(indices.length - 1, position + (event.key === 'ArrowRight' ? 1 : -1)))]);
  }
}
