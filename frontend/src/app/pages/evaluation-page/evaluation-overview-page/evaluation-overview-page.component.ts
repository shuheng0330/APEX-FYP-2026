import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
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
import { StaffService } from '../../../services/staff.service';
import { AuthService } from '../../../services/auth.service';
import { StaffTemp } from '../../../models/staff-temp.model';
import { EvaluationDTO } from '../../../models/evaluation.model';
import { Role } from '../../../models/role.model';
import { NzDropdownMenuComponent } from 'ng-zorro-antd/dropdown';
import { NzInputDirective } from 'ng-zorro-antd/input';

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
        enabled: true
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
    private evaluationService: EvaluationService,
    private evaluationCycleService: EvaluationCycleService,
    private staffService: StaffService,
    private auth: AuthService
  ) { }

  ngOnInit(): void {
    this.userId = this.auth.userId;
    this.translate.get(this.titleKey).subscribe(t => this.titleService.setTitle(t));
    this.loadData();
  }

  loadData(): void {
    this.loading = true;

    // Load staff list and evaluations in parallel
    forkJoin({
      staff: this.staffService.getDirectDownLineByManagerId(this.userId!),
      evaluations: this.evaluationService.getEvaluationsByDirectDownLineId(this.userId!),
      currentCycle: this.evaluationCycleService.getCurrentCycle()
    }).subscribe({
      next: ({ staff, evaluations, currentCycle }) => {
        this.staffList = staff;
        this.allEvaluations = evaluations;
        this.currentCycle = currentCycle;
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
    // Build staff performance map
    this.staffPerformanceMap.clear();

    this.staffList.forEach(staff => {
      const staffEvals = this.allEvaluations.filter(e => e.staffId === staff.id);
      const scores = staffEvals.map(e => e.overallScore || 0).filter(s => s > 0);
      const performance: StaffPerformance = {
        staff,
        latestScore: scores.length > 0 ? scores[scores.length - 1] : undefined,
        averageScore: scores.length > 0 ? scores.reduce((a, b) => a + b, 0) / scores.length : undefined
      };

      // Determine trend (simplified - compare last 2 scores)
      if (scores.length >= 2) {
        const recent = scores.slice(-2);
        if (recent[1] > recent[0]) performance.trend = 'up';
        else if (recent[1] < recent[0]) performance.trend = 'down';
        else performance.trend = 'stable';
      }

      this.staffPerformanceMap.set(staff.id, performance);
    });

    // Calculate summary metrics
    this.calculateSummaryMetrics();

    // Build charts data
    this.buildChartsData();

    // Build tables data
    this.buildTablesData();

    // Build heatmap data
    this.buildHeatmapData();
  }

  calculateSummaryMetrics(): void {
    const performances = Array.from(this.staffPerformanceMap.values());
    const validPerformances = performances.filter(p => p.averageScore !== undefined);

    // Team average
    if (validPerformances.length > 0) {
      this.teamAverage = validPerformances.reduce((sum, p) => sum + (p.averageScore || 0), 0) / validPerformances.length;
    }

    // Best and worst performers
    if (validPerformances.length > 0) {
      this.bestPerformer = validPerformances.reduce((best, current) =>
        (current.averageScore || 0) > (best.averageScore || 0) ? current : best
      );
      this.worstPerformer = validPerformances.reduce((worst, current) =>
        (current.averageScore || 0) < (worst.averageScore || 0) ? current : worst
      );
    }

    // Staff evaluated count
    this.staffEvaluatedCount = validPerformances.length;
    this.totalStaffCount = this.staffList.length;

    // Meeting expectations (score >= 60)
    const meetingExpectations = validPerformances.filter(p => (p.averageScore || 0) >= 60).length;
    this.meetingExpectationsPercent = this.staffEvaluatedCount > 0
      ? (meetingExpectations / this.staffEvaluatedCount) * 100
      : 0;
  }

  buildChartsData(): void {
    // Line Chart - Monthly average scores
    const monthlyAverages = this.calculateMonthlyAverages();
    this.lineChartData.labels = monthlyAverages.map(m => m.month);
    this.lineChartData.datasets[0].data = monthlyAverages.map(m => m.average);

    // Donut Chart - Performance distribution
    const needsImprovement = Array.from(this.staffPerformanceMap.values())
      .filter(p => p.averageScore !== undefined && p.averageScore < 60).length;
    const satisfactory = Array.from(this.staffPerformanceMap.values())
      .filter(p => p.averageScore !== undefined && p.averageScore >= 60 && p.averageScore < 80).length;
    const excellent = Array.from(this.staffPerformanceMap.values())
      .filter(p => p.averageScore !== undefined && p.averageScore >= 80).length;

    this.donutChartData.datasets[0].data = [needsImprovement, satisfactory, excellent];
  }

  calculateMonthlyAverages(): { month: string; average: number }[] {
    const monthlyData: Map<string, number[]> = new Map();

    this.allEvaluations.forEach(evals => {
      if (evals.createdAt && evals.overallScore) {
        const date = new Date(evals.createdAt);
        const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;

        if (!monthlyData.has(monthKey)) {
          monthlyData.set(monthKey, []);
        }
        monthlyData.get(monthKey)!.push(evals.overallScore);
      }
    });

    return Array.from(monthlyData.entries())
      .map(([month, scores]) => ({
        month,
        average: scores.reduce((a, b) => a + b, 0) / scores.length
      }))
      .sort((a, b) => a.month.localeCompare(b.month))
      .slice(-6); // Last 6 months
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
    this.router.navigate(['/performance', staffId]);
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

  private buildFilterList(values: string[]): NzTableFilterList {
    return Array.from(new Set(values)).map(value => ({ text: value, value }));
  }

  get isEvaluationOpen(): boolean {
    return this.currentCycle?.status === 'OPEN';
  }
}

