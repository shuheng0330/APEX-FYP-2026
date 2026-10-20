import { CommonModule, formatDate } from '@angular/common';
import { Component, Inject, Input, LOCALE_ID, OnChanges, ViewChild } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { AnnualAppraisalSnapshot, AnnualPerformanceHistory } from '../../models/annual-appraisal.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';

interface AnnualTrendPoint {
  periodId: number; periodName: string; startDate: string; endDate: string; year: number;
  kpi: number | null; attitude: number | null; final: number | null;
  kpiWeight: number | null; attitudeWeight: number | null; current: boolean;
  comparison: AnnualPerformanceHistory['comparisonStatus'];
}

@Component({
  selector: 'app-annual-performance-trend', standalone: true,
  imports: [CommonModule, TranslateModule, BaseChartDirective], providers: [provideCharts(withDefaultRegisterables())],
  template: `
    <section class="card" aria-labelledby="annual-trend-title">
      <div class="card-head trend-heading"><div><h3 id="annual-trend-title">{{ 'ANNUAL_TREND.TITLE' | translate }}</h3>
        <p class="help">{{ 'ANNUAL_TREND.HELP' | translate:{ name: employeeName } }}</p></div>
        <div class="actions">@for (range of [3, 5]; track range) {
          <button type="button" class="range-button" [class.active]="years === range" [attr.aria-pressed]="years === range" (click)="setRange(range)">{{ 'ANNUAL_TREND.YEARS' | translate:{ count: range } }}</button>
        }</div>
      </div>
      <div class="card-body">
        @if (loading) { <p class="help" role="status">{{ 'ANNUAL_TREND.LOADING' | translate }}</p> }
        @else if (error) { <p class="help" role="alert">{{ 'ANNUAL_TREND.UNAVAILABLE' | translate }}</p> }
        @else if (points.length) {
          <div class="annual-trend-chart"><canvas baseChart [data]="data" [options]="options" [type]="'line'"
            role="img" tabindex="0" [attr.aria-label]="'ANNUAL_TREND.ACCESSIBLE' | translate"
            aria-describedby="annual-trend-instructions annual-trend-selection"
            (focus)="selectPoint(firstPointIndex)" (keydown)="navigateChart($event)" (blur)="clearSelection()"></canvas></div>
          <p id="annual-trend-instructions" class="sr-only">{{ 'ANNUAL_TREND.KEYBOARD_HELP' | translate }}</p>
          <p id="annual-trend-selection" class="sr-only" role="status" aria-live="polite">{{ selectedSummary }}</p>
          @if (points.length === 1) { <p class="help">{{ 'ANNUAL_TREND.ONE_PERIOD' | translate }}</p> }
        } @else { <p class="help">{{ 'ANNUAL_TREND.EMPTY' | translate }}</p> }
      </div>
    </section>
  `,
  styleUrls: ['../annual-review-period/review-period.scss', './annual-performance-trend.component.scss']
})
export class AnnualPerformanceTrendComponent implements OnChanges {
  @Input() history: AnnualPerformanceHistory[] = [];
  @Input() current: AnnualAppraisalSnapshot | null = null;
  @Input() currentPeriod: KpiPeriodContext | null = null;
  @Input() employeeId = '';
  @Input() employeeName = '';
  @Input() loading = false;
  @Input() error = false;
  @ViewChild(BaseChartDirective) chart?: BaseChartDirective<'line'>;
  years = 5;
  points: AnnualTrendPoint[] = [];
  private slots: (AnnualTrendPoint | null)[] = [];
  private selectedIndex: number | null = null;
  selectedSummary = '';
  data: ChartConfiguration<'line'>['data'] = { labels: [], datasets: [] };
  options: ChartConfiguration<'line'>['options'] = {};
  constructor(private translate: TranslateService, @Inject(LOCALE_ID) private locale: string) {}
  ngOnChanges() { this.rebuild(); }
  setRange(years: number) { this.years = years; this.rebuild(); }
  get firstPointIndex() { return this.slots.findIndex(p => !!p); }

  private rebuild() {
    this.selectedIndex = null; this.selectedSummary = '';
    const byPeriod = new Map<number, AnnualTrendPoint>();
    const period = this.currentPeriod;
    const currentDate = period?.endDate;
    const employee = this.employeeId || this.current?.employeeId;
    for (const row of this.history) {
      if (!employee || row.employeeId !== employee || row.reviewPeriodId === period?.id
        || !/^\d{4}-\d{2}-\d{2}$/.test(row.endDate) || (currentDate && row.endDate > currentDate)) continue;
      byPeriod.set(row.reviewPeriodId, { periodId: row.reviewPeriodId, periodName: row.periodName,
        startDate: row.startDate, endDate: row.endDate, year: Number(row.endDate.slice(0, 4)),
        kpi: row.annualKpiScore, attitude: row.attitudeScore, final: row.finalScore,
        kpiWeight: row.kpiWeight, attitudeWeight: row.attitudeWeight, current: false, comparison: row.comparisonStatus });
    }
    if (this.current && this.current.employeeId === employee && this.current.finalScore !== null
      && period && this.current.reviewPeriodId === period.id && period.startDate && currentDate) {
      byPeriod.set(period.id, { periodId: period.id, periodName: period.name ?? '', startDate: period.startDate,
        endDate: currentDate, year: Number(currentDate.slice(0, 4)), kpi: this.current.annualKpiScore,
        attitude: this.current.attitudeScore, final: this.current.finalScore, kpiWeight: this.current.kpiWeight,
        attitudeWeight: this.current.attitudeWeight, current: true, comparison: 'NOT_APPLICABLE' });
    }
    const anchor = Number(currentDate?.slice(0, 4)) || Math.max(0, ...[...byPeriod.values()].map(p => p.year));
    this.points = [...byPeriod.values()].filter(p => p.year >= anchor - this.years + 1 && p.year <= anchor)
      .sort((a, b) => a.endDate.localeCompare(b.endDate) || a.periodId - b.periodId);
    this.slots = [];
    const labels: string[] = [];
    if (this.points.length) {
      for (let year = Math.min(...this.points.map(p => p.year)); year <= anchor; year++) {
        const records = this.points.filter(p => p.year === year);
        if (!records.length) { labels.push(String(year)); this.slots.push(null); }
        else records.forEach((point, index) => {
          labels.push(records.length > 1 ? year + ' (' + (index + 1) + ')' : String(year)); this.slots.push(point);
        });
      }
    }
    const series = [
      { key: 'kpi' as const, label: 'ANNUAL_APPRAISAL.ANNUAL_KPI', color: '#2456a6' },
      { key: 'attitude' as const, label: 'ANNUAL_APPRAISAL.ATTITUDE_SCORE', color: '#0d9488' },
      { key: 'final' as const, label: 'ANNUAL_APPRAISAL.FINAL_SCORE', color: '#8b5cf6' }
    ];
    this.data = { labels, datasets: series.map(series => ({ label: this.translate.instant(series.label),
      data: this.slots.map(point => point ? point[series.key] : null), borderColor: series.color, backgroundColor: series.color,
      pointRadius: 4, borderWidth: 2, spanGaps: false, tension: 0 })) };
    this.options = { responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', axis: 'x', intersect: false }, scales: { y: { min: 0, max: 100 } },
      plugins: { legend: { position: 'bottom' }, tooltip: { mode: 'index', axis: 'x', intersect: false, callbacks: {
        title: items => this.tooltipTitle(items[0]?.dataIndex),
        beforeBody: items => this.context(this.slots[items[0]?.dataIndex]),
        label: item => (item.dataset.label ?? '') + ': ' + this.percentage(item.parsed.y),
        afterBody: items => this.tooltipFooter(items[0]?.dataIndex)
      } } } };
  }

  tooltipTitle(index: number): string[] {
    const point = this.slots[index];
    return point ? [String(point.year), ...this.wrap(point.periodName),
      formatDate(point.startDate, 'dd MMM yyyy', this.locale) + ' - ' + formatDate(point.endDate, 'dd MMM yyyy', this.locale)] : [];
  }
  private context(point: AnnualTrendPoint | null | undefined): string[] {
    return point ? [this.translate.instant(point.current ? 'ANNUAL_TREND.CURRENT' : 'ANNUAL_TREND.HISTORICAL'),
      ...(point.periodName.includes('[UAT SIMULATED]') ? [this.translate.instant('ANNUAL_TREND.SIMULATED')] : [])] : [];
  }
  tooltipFooter(index: number): string[] {
    const point = this.slots[index];
    return point ? [this.translate.instant('ANNUAL_TREND.COMPOSITION', { kpi: point.kpiWeight ?? '-', attitude: point.attitudeWeight ?? '-' }),
      ...(point.comparison === 'DIFFERENT' ? this.wrap(this.translate.instant('ANNUAL_TREND.UPDATED')) :
        point.comparison === 'CURRENT_RESULTS_UNAVAILABLE' ? this.wrap(this.translate.instant('ANNUAL_TREND.RECORDED')) : [])] : [];
  }
  private percentage(value: number | null) {
    return value === null ? this.translate.instant('ANNUAL_APPRAISAL.PENDING') : value.toFixed(2) + '%';
  }
  private wrap(text: string): string[] {
    const length = Math.max(20, Math.min(65, Math.floor((this.chart?.chart?.width ?? 400) / 8)));
    const lines: string[] = [];
    while (text.length > length) {
      const space = text.lastIndexOf(' ', length);
      const end = space > 0 ? space : length;
      lines.push(text.slice(0, end)); text = text.slice(end).trimStart();
    }
    return [...lines, text];
  }
  selectPoint(index: number) {
    const point = this.slots[index];
    if (!point) return;
    this.selectedIndex = index;
    this.selectedSummary = [...this.tooltipTitle(index), ...this.context(point),
      this.translate.instant('ANNUAL_APPRAISAL.ANNUAL_KPI') + ': ' + this.percentage(point.kpi),
      this.translate.instant('ANNUAL_APPRAISAL.ATTITUDE_SCORE') + ': ' + this.percentage(point.attitude),
      this.translate.instant('ANNUAL_APPRAISAL.FINAL_SCORE') + ': ' + this.percentage(point.final),
      ...this.tooltipFooter(index)].join('. ');
    const chart = this.chart?.chart;
    if (!chart) return;
    const elements = chart.data.datasets.flatMap((_, datasetIndex) =>
      chart.isDatasetVisible(datasetIndex) && chart.data.datasets[datasetIndex].data[index] != null ? [{ datasetIndex, index }] : []);
    chart.setActiveElements(elements);
    const first = elements[0];
    const element = first ? chart.getDatasetMeta(first.datasetIndex).data[index] : null;
    const position = element ? { x: element.x, y: element.y } : { x: 0, y: 0 };
    chart.tooltip?.setActiveElements(elements, position); chart.update('none');
  }
  navigateChart(event: KeyboardEvent) {
    const indices = this.slots.flatMap((point, index) => point ? [index] : []);
    if (!indices.length || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const position = Math.max(0, indices.indexOf(this.selectedIndex ?? indices[0]));
    this.selectPoint(event.key === 'Home' ? indices[0] : event.key === 'End' ? indices.at(-1)! :
      indices[Math.max(0, Math.min(indices.length - 1, position + (event.key === 'ArrowRight' ? 1 : -1)))]);
  }
  clearSelection() {
    this.selectedIndex = null; this.selectedSummary = '';
    const chart = this.chart?.chart;
    chart?.setActiveElements([]); chart?.tooltip?.setActiveElements([], { x: 0, y: 0 }); chart?.update('none');
  }
}
