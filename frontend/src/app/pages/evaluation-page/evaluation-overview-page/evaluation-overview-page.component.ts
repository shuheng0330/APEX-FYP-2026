import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { FormsModule } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTableFilterFn, NzTableFilterList, NzTableModule, NzTableSortFn, NzTableSortOrder } from 'ng-zorro-antd/table';
import { NzSkeletonComponent } from 'ng-zorro-antd/skeleton';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzStatisticModule } from 'ng-zorro-antd/statistic';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { EvaluationCycleDto, EvaluationCycleService } from '../../../services/evaluation-cycle.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { AppraisalRecordService } from '../../../services/appraisal-record.service';
import { StaffService } from '../../../services/staff.service';
import { AuthService } from '../../../services/auth.service';
import { StaffTemp } from '../../../models/staff-temp.model';
import { EvaluationDTO } from '../../../models/evaluation.model';
import { Role } from '../../../models/role.model';
import { NzDropdownMenuComponent } from 'ng-zorro-antd/dropdown';
import { NzInputDirective } from 'ng-zorro-antd/input';
import { AppraisalCategory, AppraisalRecordDto, AppraisalStatus } from '../../../models/appraisal-record.model';
import { OrgWideAverageTrendDto } from '../../../models/org-wide-evaluation.model';

interface StaffPerformance {
  staff: StaffTemp;
  latestScore?: number;
  averageScore?: number;
  trend?: 'up' | 'down' | 'stable';
}

interface PerformanceCategory {
  label: string;
  count: number;
  color: string;
}

interface AppraisalCategoryItem {
  label: 'P' | 'S';
  category: AppraisalCategory;
}

interface ItemData {
  name: string;
  role: Role;
  averageScore?: number;
  latestScore?: number;
}

interface ColumnItem {
  name: string;
  sortOrder: NzTableSortOrder | null;
  sortFn: NzTableSortFn<ItemData> | null;
  listOfFilter: NzTableFilterList;
  filterFn: NzTableFilterFn<ItemData> | null;
  filterMultiple: boolean;
  isSearchable: boolean;
  sortDirections: NzTableSortOrder[];
}

type TrendRangeYears = 1 | 3 | 5;


@Component({
  selector: 'evaluation-overview-page',
  standalone: true,
  templateUrl: './evaluation-overview-page.component.html',
  styleUrls: ['./evaluation-overview-page.component.scss'],
  imports: [
    CommonModule,
    TranslatePipe,
    FormsModule,
    NzButtonModule,
    NzCardModule,
    NzTableModule,
    NzSkeletonComponent,
    NzIconModule,
    NzStatisticModule,
    NzTagModule,
    BaseChartDirective,
    NzToolTipModule,
    NzDropdownMenuComponent,
    NzInputDirective,
    NzInputDirective,
    NzEmptyModule,
    NzAlertModule
  ],
  providers: [provideCharts(withDefaultRegisterables())]
})
export class EvaluationOverviewPageComponent implements OnInit {
  @ViewChild(BaseChartDirective) lineChart?: BaseChartDirective;
  @ViewChild(BaseChartDirective) donutChart?: BaseChartDirective;

  titleKey = 'PAGE.EVALUATION_OVERVIEW.TITLE';
  loading = true;
  userId: string | null = null;
  selectedDepartmentName: string | null = null;
  navigationContext: 'manager' | 'hr' = 'manager';

  // Summary Cards Data
  teamAverage: number = 0;
  bestPerformer?: StaffPerformance;
  worstPerformer?: StaffPerformance;
  staffEvaluatedCount: number = 0;
  totalStaffCount: number = 0;
  meetingExpectationsPercent: number = 0;

  // Staff and Evaluation Data
  staffList: StaffTemp[] = [];
  allEvaluations: EvaluationDTO[] = [];
  private summaryEvals: EvaluationDTO[] = [];
  staffPerformanceMap: Map<string, StaffPerformance> = new Map();
  currentCycle: EvaluationCycleDto | null = null;

  listOfColumnsAtRiskTable: ColumnItem[] = [];
  listOfColumnRankingTable: ColumnItem[] = [];
  listOfRole: NzTableFilterList = [];
  listOfDepartment: NzTableFilterList = [];
  listOfRoleRanking: NzTableFilterList = [];
  listOfDepartmentRanking: NzTableFilterList = [];
  visible = false;
  staffSearch: string = '';

  visibleRanking = false;
  staffSearchRanking: string = '';

  filteredAtRiskStaffList: StaffPerformance[] = [];
  filteredRankingStaffList: StaffPerformance[] = [];
  trendRangeOptions: TrendRangeYears[] = [1, 3, 5];
  selectedTrendRangeYears: TrendRangeYears = 5;
  private teamTrendPointChanges: (number | null)[] = [];
  private orgAverageTrend: OrgWideAverageTrendDto[] = [];
  appraisalByStaffId: Map<string, AppraisalRecordDto> = new Map();
  // Line Chart - Performance Trend
  public lineChartData: ChartConfiguration<'line'>['data'] = {
    labels: [],
    datasets: [{
      data: [],
      label: 'Team Average Score',
      fill: false,
      borderColor: '#1890ff',
      backgroundColor: '#1890ff',
      tension: 0.4
    }]
  };

  public lineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'top'
      },
      tooltip: {
        enabled: true,
        callbacks: {
          label: (context) => {
            const score = Number(context.parsed.y ?? 0);
            if (context.dataset.label === 'Org Average') {
              return `Org Average: ${score.toFixed(2)}%`;
            }
            const change = this.teamTrendPointChanges[context.dataIndex];
            const changeText = change === null || change === undefined
              ? 'No previous cycle'
              : `${change >= 0 ? '+' : ''}${change.toFixed(1)}% vs previous cycle`;
            return `Team Average Score: ${score.toFixed(2)}% (${changeText})`;
          }
        }
      }
    },
    scales: {
      y: {
        min: 0,
        max: 100,
        title: {
          display: true,
          text: 'Score (%)'
        }
      }
    }
  };

  // Donut Chart - Performance Distribution
  public donutChartData: ChartConfiguration<'doughnut'>['data'] = {
    labels: ['Needs Improvement', 'Satisfactory', 'Excellent'],
    datasets: [{
      data: [0, 0, 0],
      backgroundColor: ['#ff4d4f', '#faad14', '#52c41a'],
      borderWidth: 0
    }]
  };

  get hasDonutData(): boolean {
    const data = this.donutChartData.datasets[0].data;
    return data.some(d => d > 0);
  }

  public donutChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom'
      },
      tooltip: {
        enabled: true
      }
    }
  };

  // Tables Data
  topPerformers: StaffPerformance[] = [];
  bottomPerformers: StaffPerformance[] = [];
  fullTeamRanking: StaffPerformance[] = [];
  atRiskStaff: StaffPerformance[] = [];

  // Heatmap Data (simplified - showing competency averages)
  heatmapData: { competency: string; average: number }[] = [];

  constructor(
    private translate: TranslateService,
    private titleService: Title,
    private router: Router,
    private route: ActivatedRoute,
    private evaluationService: EvaluationService,
    private evaluationCycleService: EvaluationCycleService,
    private staffService: StaffService,
    private appraisalRecordService: AppraisalRecordService,
    private auth: AuthService
  ) { }

  ngOnInit(): void {
    this.userId = this.auth.userId;
    this.selectedDepartmentName = this.route.snapshot.queryParamMap.get('departmentName');
    this.navigationContext = this.route.snapshot.queryParamMap.get('context') === 'hr' ? 'hr' : 'manager';
    this.translate.get(this.titleKey).subscribe(t => this.titleService.setTitle(t));
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    const shouldLoadDepartmentView = !!this.selectedDepartmentName?.trim();
    const staffRequest = shouldLoadDepartmentView
      ? this.staffService.getAllStaffTempToBeReplaced()
      : this.staffService.getDirectDownLineByManagerId(this.userId!);
    const evaluationRequest = shouldLoadDepartmentView
      ? this.evaluationService.getAllEvaluations()
      : this.evaluationService.getEvaluationsByDirectDownLineId(this.userId!);
    const appraisalRequest = shouldLoadDepartmentView
      ? this.appraisalRecordService.getReviewRecords()
      : this.appraisalRecordService.getLatestTeamAppraisals();

    // Load direct downline for manager view, or all staff first for HR department drill-down.
    forkJoin({
      staff: staffRequest,
      evaluations: evaluationRequest,
      currentCycle: this.evaluationCycleService.getCurrentCycle(),
      appraisals: appraisalRequest,
      orgAverageTrend: this.evaluationService.getOrgAverageTrend(this.selectedTrendRangeYears)
    }).subscribe({
      next: ({ staff, evaluations, currentCycle, appraisals, orgAverageTrend }) => {
        const filteredData = this.applyDepartmentRouteFilter(staff, evaluations);
        this.staffList = filteredData.staff;
        this.allEvaluations = filteredData.evaluations;
        this.currentCycle = currentCycle;
        const visibleStaffIds = new Set(this.staffList.map(item => item.id));
        this.appraisalByStaffId = new Map(
          appraisals
            .filter(record => visibleStaffIds.has(record.staffId))
            .map(record => [record.staffId, record])
        );
        this.orgAverageTrend = orgAverageTrend;
        this.processData();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading data:', error);
        this.loading = false;
      }
    });
  }


  filterStaff(): void {
    const q = this.staffSearch.trim().toLowerCase();
    if (!q) {
      this.filteredAtRiskStaffList = [...this.atRiskStaff];
      return;
    }
    this.filteredAtRiskStaffList = this.atRiskStaff.filter((s) => {
      const name = s.staff.name?.toLowerCase() || '';
      return name.includes(q);
    });
  }

  filterStaffRanking(): void {
    const q = this.staffSearchRanking.trim().toLowerCase();
    if (!q) {
      this.filteredRankingStaffList = [...this.fullTeamRanking];
      return;
    }
    this.filteredRankingStaffList = this.fullTeamRanking.filter((s) => {
      const name = s.staff.name?.toLowerCase() || '';
      return name.includes(q);
    });
  }

  generateAtRiskTableColumns(): ColumnItem[] {
    return [
      // {
      //   name: 'Staff',
      //   sortOrder: null,
      //   sortFn: (a, b) => a.name.localeCompare(b.name),
      //   sortDirections: ['ascend', 'descend', null],
      //   filterMultiple: true,
      //   isSearchable: true,
      //   listOfFilter: [],
      //   filterFn: null,
      // },
      {
        name: 'Department',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfDepartment,
        filterFn: (dept: string, item: ItemData) => item.role.orgChart.name.includes(dept),
      },
      {
        name: 'Role',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfRole,
        filterFn: (role: string, item: ItemData) => item.role.name.includes(role),
      },
      {
        name: 'Average Score',
        sortOrder: null,
        sortFn: (a, b) => (b.averageScore ?? 0) - (a.averageScore ?? 0),
        sortDirections: ['ascend', 'descend'],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: [],
        filterFn: null,
      },
      {
        name: 'Latest Score',
        sortOrder: null,
        sortFn: (a, b) => (b.latestScore ?? 0) - (a.latestScore ?? 0),
        sortDirections: ['ascend', 'descend'],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: [],
        filterFn: null,
      }
    ];
  }

  generateRankingTableColumns(): ColumnItem[] {
    return [
      // {
      //   name: 'Staff',
      //   sortOrder: null,
      //   sortFn: (a, b) => a.name.localeCompare(b.name),
      //   sortDirections: ['ascend', 'descend', null],
      //   filterMultiple: true,
      //   isSearchable: true,
      //   listOfFilter: [],
      //   filterFn: null,
      // },
      {
        name: 'Department',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfDepartmentRanking,
        filterFn: (dept: string, item: ItemData) => item.role.orgChart.name.includes(dept),
      },
      {
        name: 'Role',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfRoleRanking,
        filterFn: (role: string, item: ItemData) => item.role.name.includes(role),
      },
      {
        name: 'Average Score',
        sortOrder: null,
        sortFn: (a, b) => (b.averageScore ?? 0) - (a.averageScore ?? 0),
        sortDirections: ['ascend', 'descend'],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: [],
        filterFn: null,
      },
      {
        name: 'Latest Score',
        sortOrder: null,
        sortFn: (a, b) => (b.latestScore ?? 0) - (a.latestScore ?? 0),
        sortDirections: ['ascend', 'descend'],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: [],
        filterFn: null,
      }
    ];
  }

  processData(): void {
    this.staffPerformanceMap.clear();

    // Full Team Ranking uses ALL historical evaluations so that Average Score
    // reflects the all-time mean and Trend compares the last two cycles.
    this.staffList.forEach(staff => {
      const staffEvals = this.allEvaluations
        .filter(e => e.staffId === staff.id)
        .sort((a, b) => this.getEvaluationDate(a).getTime() - this.getEvaluationDate(b).getTime());
      const scores = staffEvals.map(e => e.overallScore || 0).filter(s => s > 0);
      const performance: StaffPerformance = {
        staff,
        latestScore: scores.length > 0 ? scores[scores.length - 1] : undefined,
        averageScore: scores.length > 0 ? scores.reduce((a, b) => a + b, 0) / scores.length : undefined
      };
      if (scores.length >= 2) {
        const recent = scores.slice(-2);
        if (recent[1] > recent[0]) performance.trend = 'up';
        else if (recent[1] < recent[0]) performance.trend = 'down';
        else performance.trend = 'stable';
      }
      this.staffPerformanceMap.set(staff.id, performance);
    });

    // Summary cards and donut use cycle-specific evaluations:
    //   - Open cycle exists → show open cycle data (empty if no evals yet — correct for new cycle)
    //   - No open cycle    → fall back to last closed cycle so the page stays informative
    this.summaryEvals = this.currentCycle?.status === 'OPEN'
      ? this.allEvaluations.filter(e => e.evaluationCycleStatus === 'OPEN')
      : this.resolveLatestClosedCycleEvals();

    this.calculateSummaryMetrics();
    this.buildChartsData();
    this.buildTablesData();
    this.buildHeatmapData();
  }

  calculateSummaryMetrics(): void {
    const visibleStaffIds = new Set(this.staffList.map(s => s.id));
    const validEvals = this.summaryEvals
      .filter(e => visibleStaffIds.has(e.staffId))
      .filter(e => (e.overallScore ?? 0) > 0);

    this.teamAverage = 0;
    this.bestPerformer = undefined;
    this.worstPerformer = undefined;
    this.staffEvaluatedCount = 0;
    this.totalStaffCount = this.staffList.length;
    this.meetingExpectationsPercent = 0;

    if (validEvals.length === 0) return;

    const evaluatedIds = new Set(validEvals.map(e => e.staffId));
    this.staffEvaluatedCount = [...evaluatedIds].filter(id =>
      this.staffList.some(s => s.id === id)).length;

    this.teamAverage = validEvals.reduce((sum, e) => sum + (e.overallScore ?? 0), 0) / validEvals.length;

    const bestEval = validEvals.reduce((best, e) =>
      (e.overallScore ?? 0) > (best.overallScore ?? 0) ? e : best);
    this.bestPerformer = this.staffPerformanceMap.get(bestEval.staffId);

    const worstEval = validEvals.reduce((worst, e) =>
      (e.overallScore ?? 0) < (worst.overallScore ?? 0) ? e : worst);
    this.worstPerformer = this.staffPerformanceMap.get(worstEval.staffId);

    const meetingCount = validEvals.filter(e => (e.overallScore ?? 0) >= 60).length;
    this.meetingExpectationsPercent = (meetingCount / validEvals.length) * 100;
  }

  buildChartsData(): void {
    // Line Chart - Monthly average scores
    const monthlyAverages = this.calculateMonthlyAverages(this.selectedTrendRangeYears);
    const labels = monthlyAverages.map(m => m.month);
    const orgAverageByYear = new Map(this.orgAverageTrend.map(point => [point.year, point.averageScore]));
    this.lineChartData = {
      labels,
      datasets: [
        {
          data: monthlyAverages.map(m => m.average),
          label: 'Team Average Score',
          fill: false,
          borderColor: '#1890ff',
          backgroundColor: '#1890ff',
          tension: 0.4
        },
        {
          data: labels.map(label => orgAverageByYear.get(Number(label.slice(0, 4))) ?? null),
          label: 'Org Average',
          fill: false,
          borderColor: '#8c8c8c',
          backgroundColor: '#8c8c8c',
          borderDash: [6, 5],
          pointRadius: 2,
          pointHoverRadius: 4,
          tension: 0.25,
          spanGaps: true
        }
      ]
    };
    this.teamTrendPointChanges = monthlyAverages.map((point, index) => {
      if (index === 0) return null;
      return point.average - monthlyAverages[index - 1].average;
    });

    // Donut Chart - Performance distribution from the same cycle used by summary cards
    const validSummaryEvals = this.summaryEvals.filter(e => (e.overallScore ?? 0) > 0);
    const needsImprovement = validSummaryEvals.filter(e => (e.overallScore ?? 0) < 60).length;
    const satisfactory = validSummaryEvals.filter(e => (e.overallScore ?? 0) >= 60 && (e.overallScore ?? 0) < 80).length;
    const excellent = validSummaryEvals.filter(e => (e.overallScore ?? 0) >= 80).length;

    this.donutChartData.datasets[0].data = [needsImprovement, satisfactory, excellent];
    setTimeout(() => this.lineChart?.update(), 0);
  }

  calculateMonthlyAverages(rangeYears: TrendRangeYears): { month: string; average: number }[] {
    const monthlyData: Map<string, number[]> = new Map();

    this.allEvaluations.forEach(evals => {
      if ((evals.evaluationCycleEndDate || evals.createdAt) && evals.overallScore) {
        const date = this.getEvaluationDate(evals);
        const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;

        if (!monthlyData.has(monthKey)) {
          monthlyData.set(monthKey, []);
        }
        monthlyData.get(monthKey)!.push(evals.overallScore);
      }
    });

    const allMonthlyAverages = Array.from(monthlyData.entries())
      .map(([month, scores]) => ({
        month,
        average: scores.reduce((a, b) => a + b, 0) / scores.length
      }))
      .sort((a, b) => a.month.localeCompare(b.month));

    if (allMonthlyAverages.length === 0) {
      return [];
    }

    const latestMonth = allMonthlyAverages[allMonthlyAverages.length - 1].month;
    const latestDate = new Date(`${latestMonth}-01T00:00:00`);
    const startDate = new Date(latestDate);
    startDate.setFullYear(startDate.getFullYear() - rangeYears);

    return allMonthlyAverages.filter(item => new Date(`${item.month}-01T00:00:00`) >= startDate);
  }

  setTrendRange(years: TrendRangeYears): void {
    this.selectedTrendRangeYears = years;
    this.evaluationService.getOrgAverageTrend(years).subscribe({
      next: trend => {
        this.orgAverageTrend = trend;
        this.buildChartsData();
      },
      error: error => console.error('Error loading organisation average trend:', error)
    });
  }

  buildTablesData(): void {
    const performances = Array.from(this.staffPerformanceMap.values())
      .filter(p => p.averageScore !== undefined)
      .sort((a, b) => (b.averageScore || 0) - (a.averageScore || 0));

    // Top performers (top 5)
    this.topPerformers = performances.slice(0, 5);

    // Bottom performers (bottom 5)
    this.bottomPerformers = performances.slice(-5).reverse();

    // Full team ranking
    this.fullTeamRanking = performances;
    this.filteredRankingStaffList = [...this.fullTeamRanking];
    this.listOfRoleRanking = this.buildFilterList(this.filteredRankingStaffList.map(s => s.staff.role.name));
    this.listOfDepartmentRanking = this.buildFilterList(this.filteredRankingStaffList.map(s => s.staff.role.orgChart.name));
    this.listOfColumnRankingTable = this.generateRankingTableColumns()

    // At-risk staff (score < 60)
    this.atRiskStaff = performances.filter(p => (p.averageScore || 0) < 60);
    this.filteredAtRiskStaffList = [...this.atRiskStaff];
    this.listOfRole = this.buildFilterList(this.filteredAtRiskStaffList.map(s => s.staff.role.name));
    this.listOfDepartment = this.buildFilterList(this.filteredAtRiskStaffList.map(s => s.staff.role.orgChart.name));
    this.listOfColumnsAtRiskTable = this.generateAtRiskTableColumns();
  }

  buildHeatmapData(): void {
    // Aggregate competency ratings across all evaluations
    const competencyMap: Map<string, number[]> = new Map();
    let totalRatings = 0;

    this.allEvaluations.forEach(evals => {
      evals.ratings.forEach(rating => {
        const compName = rating.competencyName || `Competency ${rating.compId}`;
        if (!competencyMap.has(compName)) {
          competencyMap.set(compName, []);
        }
        competencyMap.get(compName)!.push(rating.rating);
        totalRatings++;
      });
    });

    this.heatmapData = Array.from(competencyMap.entries())
      .map(([competency, ratings]) => ({
        competency,
        average: ratings.reduce((a, b) => a + b, 0) / ratings.length
      }))
      .sort((a, b) => b.average - a.average)
      .slice(0, 10); // Top 10 competencies
  }

  navigateToEvaluation(): void {
    this.router.navigate(['/evaluation']);
  }

  navigateToStaffPerformance(staffId: string): void {
    const appraisalId = this.navigationContext === 'hr'
      ? this.appraisalByStaffId.get(staffId)?.id
      : undefined;
    this.router.navigate(['/performance', staffId], {
      queryParams: {
        context: this.navigationContext,
        ...(appraisalId ? { appraisalId } : {})
      }
    });
  }

  getPerformanceCategory(score: number): { label: string; color: string } {
    if (score >= 80) return { label: 'Excellent', color: 'green' };
    if (score >= 60) return { label: 'Satisfactory', color: 'orange' };
    return { label: 'Needs Improvement', color: 'red' };
  }

  getTrendIcon(trend?: 'up' | 'down' | 'stable'): string {
    switch (trend) {
      case 'up': return 'arrow-up';
      case 'down': return 'arrow-down';
      default: return 'minus';
    }
  }

  getTrendColor(trend?: 'up' | 'down' | 'stable'): string {
    switch (trend) {
      case 'up': return '#52c41a';
      case 'down': return '#ff4d4f';
      default: return '#999999';
    }
  }

  getHeatmapColor(score: number): string {
    if (score >= 8) return '#52c41a'; // Green for high scores
    if (score >= 6) return '#faad14'; // Orange for medium scores
    return '#ff4d4f'; // Red for low scores
  }

  getAppraisalStatus(staffId: string): AppraisalStatus | undefined {
    return this.appraisalByStaffId.get(staffId)?.status;
  }

  getAppraisalCategoryItems(staffId: string): AppraisalCategoryItem[] {
    const record = this.appraisalByStaffId.get(staffId);
    if (!record) return [];

    if (record.decisionType === 'PROMOTION') {
      return record.promotionEffectiveCategory
        ? [{ label: 'P', category: record.promotionEffectiveCategory }]
        : [];
    }

    if (record.decisionType === 'SALARY_INCREMENT') {
      return record.salaryEffectiveCategory
        ? [{ label: 'S', category: record.salaryEffectiveCategory }]
        : [];
    }

    const items: AppraisalCategoryItem[] = [];
    if (record.promotionEffectiveCategory) {
      items.push({ label: 'P', category: record.promotionEffectiveCategory });
    }
    if (record.salaryEffectiveCategory) {
      items.push({ label: 'S', category: record.salaryEffectiveCategory });
    }
    return items;
  }

  getAppraisalStatusColor(status?: AppraisalStatus): string {
    if (status === 'APPROVED') return 'green';
    if (status === 'PENDING_REVIEW') return 'orange';
    if (status === 'RETURNED') return 'red';
    return 'default';
  }

  getAppraisalCategoryColor(category?: AppraisalCategory): string {
    if (category === 'READY') return 'green';
    if (category === 'BORDERLINE') return 'orange';
    if (category === 'NEEDS_IMPROVEMENT') return 'red';
    return 'default';
  }

  formatAppraisalValue(value?: string): string {
    return value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()) : '—';
  }

  private resolveLatestClosedCycleEvals(): EvaluationDTO[] {
    const closed = this.allEvaluations.filter(e => e.evaluationCycleStatus === 'CLOSED');
    if (closed.length === 0) return [];
    const latestEndDate = closed.reduce((max, e) =>
      (e.evaluationCycleEndDate ?? '') > max ? (e.evaluationCycleEndDate ?? '') : max, '');
    return closed.filter(e => e.evaluationCycleEndDate === latestEndDate);
  }

  private buildFilterList(values: string[]): NzTableFilterList {
    return Array.from(new Set(values)).map(value => ({ text: value, value }));
  }

  private getEvaluationDate(evaluation: EvaluationDTO): Date {
    return new Date(evaluation.evaluationCycleEndDate || evaluation.createdAt!);
  }

  private applyDepartmentRouteFilter(
    staff: StaffTemp[],
    evaluations: EvaluationDTO[]
  ): { staff: StaffTemp[]; evaluations: EvaluationDTO[] } {
    const departmentName = this.selectedDepartmentName?.trim().toLowerCase();

    if (!departmentName) {
      return { staff, evaluations };
    }

    // TODO: Move this department filtering to a backend API for large datasets.
    const filteredStaff = staff.filter(item =>
      item.role?.orgChart?.name?.trim().toLowerCase() === departmentName
    );
    const filteredStaffIds = new Set(filteredStaff.map(item => item.id));

    return {
      staff: filteredStaff,
      evaluations: evaluations.filter(item => filteredStaffIds.has(item.staffId))
    };
  }

  get isEvaluationOpen(): boolean {
    return this.currentCycle?.status === 'OPEN';
  }

  get canManageEvaluation(): boolean {
    return this.auth.hasRole('CAN_MANAGE_EVALUATION');
  }
}

