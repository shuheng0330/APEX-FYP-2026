import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { forkJoin } from 'rxjs';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSkeletonComponent } from 'ng-zorro-antd/skeleton';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzAlertModule } from 'ng-zorro-antd/alert';

import { EvaluationService } from '../../../services/evaluation.service';
import { AppraisalRecordService } from '../../../services/appraisal-record.service';
import {
  AppraisalCategory,
  AppraisalDecisionType,
  AppraisalRecordDto,
  AppraisalStatus
} from '../../../models/appraisal-record.model';
import {
  OrgWideCompetencyAverageDto,
  OrgWideCompetencyBreakdownDto,
  OrgWideDepartmentRankingDto,
  OrgWideDepartmentTrendDto,
  OrgWideScoreDistributionDto,
  OrgWideSummaryDto
} from '../../../models/org-wide-evaluation.model';

type TrendYears = 3 | 5;

interface WeakestCompetencyInsight {
  departmentName: string;
  competencyName: string;
  averageRating: number;
  benchmark: number;
  gap: number;
}

@Component({
  selector: 'app-org-wide-evaluation',
  standalone: true,
  templateUrl: './org-wide-evaluation.component.html',
  styleUrls: ['./org-wide-evaluation.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    NzAlertModule,
    NzButtonModule,
    NzCardModule,
    NzEmptyModule,
    NzIconModule,
    NzSelectModule,
    NzSkeletonComponent,
    NzTableModule,
    NzTagModule,
    NzToolTipModule,
    BaseChartDirective
  ],
  providers: [provideCharts(withDefaultRegisterables())]
})
export class OrgWideEvaluationComponent implements OnInit {
  @ViewChild('appraisalCandidatesSection') appraisalCandidatesSection?: ElementRef<HTMLElement>;
  @ViewChildren(BaseChartDirective) charts?: QueryList<BaseChartDirective>;

  loading = true;
  competencyLoading = false;
  trendLoading = false;
  selectedDepartment: string | null = null;
  selectedTrendYears: TrendYears = 5;
  trendOptions: TrendYears[] = [3, 5];
  selectedCandidateDepartment: string | null = null;
  selectedCandidateDecisionType: AppraisalDecisionType | null = null;
  selectedCandidateStatus: AppraisalStatus | null = null;
  selectedCandidateCategory: AppraisalCategory | null = null;

  summary: OrgWideSummaryDto = {
    totalStaffEvaluated: 0,
    totalStaff: 0,
    orgAverageScore: 0,
    topDepartment: null,
    topDepartmentScore: 0,
    pendingAppraisals: 0
  };

  appraisalCandidates: AppraisalRecordDto[] = [];
  candidateDepartmentOptions: string[] = [];
  candidateDecisionTypeOptions: AppraisalDecisionType[] = ['PROMOTION', 'SALARY_INCREMENT', 'BOTH'];
  candidateStatusOptions: AppraisalStatus[] = ['PENDING_REVIEW', 'APPROVED', 'RETURNED'];
  candidateCategoryOptions: AppraisalCategory[] = ['READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'];
  scoreDistribution: OrgWideScoreDistributionDto[] = [];
  departmentRanking: OrgWideDepartmentRankingDto[] = [];
  competencyBreakdown: OrgWideCompetencyBreakdownDto[] = [];
  allCompetencyBreakdown: OrgWideCompetencyBreakdownDto[] = [];
  weakestCompetencies: WeakestCompetencyInsight[] = [];
  departmentTrend: OrgWideDepartmentTrendDto[] = [];
  departmentOptions: string[] = [];

  scoreDistributionChartData: ChartConfiguration<'bar'>['data'] = {
    labels: [],
    datasets: [{
      data: [],
      label: 'Staff Count',
      backgroundColor: [],
      borderRadius: 4
    }]
  };

  scoreDistributionChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        callbacks: {
          label: (context) => {
            const bucket = this.scoreDistribution[context.dataIndex];
            const names = bucket?.staffNames?.length ? `: ${bucket.staffNames.join(', ')}` : '';
            return `${bucket?.count ?? 0} staff${names}`;
          }
        }
      }
    },
    scales: {
      x: {
        title: {
          display: true,
          text: 'Score Range'
        }
      },
      y: {
        beginAtZero: true,
        ticks: {
          precision: 0
        },
        title: {
          display: true,
          text: 'Staff Count'
        }
      }
    }
  };

  competencyChartData: ChartConfiguration<'bar'>['data'] = {
    labels: [],
    datasets: [{
      data: [],
      label: 'Average Rating',
      backgroundColor: '#1890ff',
      borderRadius: 4
    }]
  };

  competencyChartOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        callbacks: {
          label: (context) => `Average rating: ${Number(context.parsed.x ?? 0).toFixed(2)}/10`
        }
      }
    },
    scales: {
      x: {
        min: 0,
        max: 10,
        title: {
          display: true,
          text: 'Average Rating'
        }
      }
    }
  };

  departmentTrendChartData: ChartConfiguration<'line'>['data'] = {
    labels: [],
    datasets: []
  };

  departmentTrendChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'top'
      },
      tooltip: {
        callbacks: {
          label: (context) => `${context.dataset.label}: ${Number(context.parsed.y ?? 0).toFixed(2)}%`
        }
      }
    },
    scales: {
      y: {
        min: 0,
        max: 100,
        title: {
          display: true,
          text: 'Average Score (%)'
        }
      }
    }
  };

  private departmentColors = ['#1890ff', '#52c41a', '#faad14', '#722ed1', '#13c2c2', '#eb2f96', '#2f54eb'];
  private readonly competencyBenchmark = 7.5;

  constructor(
    private evaluationService: EvaluationService,
    private appraisalRecordService: AppraisalRecordService,
    private router: Router,
    private titleService: Title
  ) {}

  ngOnInit(): void {
    this.titleService.setTitle('Org-Wide Evaluation');
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading = true;

    forkJoin({
      summary: this.evaluationService.getOrgSummary(),
      distribution: this.evaluationService.getOrgScoreDistribution(),
      ranking: this.evaluationService.getOrgDepartmentRanking(),
      competencies: this.evaluationService.getOrgCompetencyBreakdown(),
      trend: this.evaluationService.getOrgDepartmentTrend(this.selectedTrendYears),
      appraisalCandidates: this.appraisalRecordService.getReviewRecords()
    }).subscribe({
      next: ({ summary, distribution, ranking, competencies, trend, appraisalCandidates }) => {
        this.summary = summary;
        this.appraisalCandidates = appraisalCandidates;
        this.summary.pendingAppraisals = appraisalCandidates.filter(record => record.status === 'PENDING_REVIEW').length;
        this.candidateDepartmentOptions = Array.from(new Set(
          appraisalCandidates.map(record => record.departmentName).filter((name): name is string => !!name)
        )).sort((a, b) => a.localeCompare(b));
        this.scoreDistribution = distribution;
        this.departmentRanking = ranking;
        this.competencyBreakdown = competencies;
        this.allCompetencyBreakdown = competencies;
        this.buildWeakestCompetencies();
        this.departmentTrend = trend;
        this.departmentOptions = this.buildDepartmentOptions(ranking, competencies, trend);
        this.buildScoreDistributionChart();
        this.buildCompetencyChart();
        this.buildDepartmentTrendChart();
        this.loading = false;
        this.refreshCharts();
      },
      error: (error) => {
        console.error('Error loading org-wide dashboard:', error);
        this.loading = false;
      }
    });
  }

  scrollToAppraisalCandidates(): void {
    this.appraisalCandidatesSection?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  onDepartmentChange(departmentName: string | null): void {
    this.selectedDepartment = departmentName;
    this.competencyLoading = true;

    this.evaluationService.getOrgCompetencyBreakdown(departmentName || undefined).subscribe({
      next: (competencies) => {
        this.competencyBreakdown = competencies;
        this.buildCompetencyChart();
        this.competencyLoading = false;
        this.refreshCharts();
      },
      error: (error) => {
        console.error('Error loading competency breakdown:', error);
        this.competencyLoading = false;
      }
    });
  }

  setTrendYears(years: TrendYears): void {
    if (this.selectedTrendYears === years) {
      return;
    }

    this.selectedTrendYears = years;
    this.trendLoading = true;

    this.evaluationService.getOrgDepartmentTrend(years).subscribe({
      next: (trend) => {
        this.departmentTrend = trend;
        this.buildDepartmentTrendChart();
        this.trendLoading = false;
        this.refreshCharts();
      },
      error: (error) => {
        console.error('Error loading department trend:', error);
        this.trendLoading = false;
      }
    });
  }

  navigateToTeamOverview(departmentName: string): void {
    this.router.navigate(['/evaluation/overview'], {
      queryParams: { departmentName, context: 'hr' }
    });
  }

  navigateToAppraisalReview(candidate: AppraisalRecordDto): void {
    this.router.navigate(['/performance', candidate.staffId], {
      queryParams: { context: 'hr', appraisalId: candidate.id }
    });
  }

  get filteredAppraisalCandidates(): AppraisalRecordDto[] {
    return this.appraisalCandidates.filter(record => {
      const matchesDepartment = !this.selectedCandidateDepartment
        || record.departmentName === this.selectedCandidateDepartment;
      const matchesDecisionType = !this.selectedCandidateDecisionType
        || record.decisionType === this.selectedCandidateDecisionType;
      const matchesStatus = !this.selectedCandidateStatus
        || record.status === this.selectedCandidateStatus;
      const matchesCategory = !this.selectedCandidateCategory
        || record.promotionEffectiveCategory === this.selectedCandidateCategory
        || record.salaryEffectiveCategory === this.selectedCandidateCategory;

      return matchesDepartment && matchesDecisionType && matchesStatus && matchesCategory;
    });
  }

  formatAppraisalValue(value?: string | null): string {
    return value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()) : '—';
  }

  getDecisionTypeColor(decisionType: AppraisalDecisionType): string {
    if (decisionType === 'PROMOTION') return 'purple';
    if (decisionType === 'SALARY_INCREMENT') return 'blue';
    return 'cyan';
  }

  getAppraisalStatusColor(status?: AppraisalStatus): string {
    if (status === 'APPROVED') return 'green';
    if (status === 'RETURNED') return 'red';
    if (status === 'PENDING_REVIEW') return 'orange';
    return 'default';
  }

  getAppraisalCategoryColor(category?: AppraisalCategory | null): string {
    if (category === 'READY') return 'green';
    if (category === 'BORDERLINE') return 'orange';
    if (category === 'NEEDS_IMPROVEMENT') return 'red';
    return 'default';
  }

  getDistributionColor(range: string): string {
    const lowerBound = Number(range.split('-')[0]);
    if (lowerBound < 50) {
      return '#ff4d4f';
    }
    if (lowerBound < 70) {
      return '#faad14';
    }
    return '#52c41a';
  }

  getStatusColor(status: string): string {
    if (status === 'Excellent') {
      return 'green';
    }
    if (status === 'Satisfactory') {
      return 'orange';
    }
    return 'red';
  }

  getScoreChangeIcon(scoreChange: number): string {
    if (scoreChange > 0) {
      return 'arrow-up';
    }
    if (scoreChange < 0) {
      return 'arrow-down';
    }
    return 'minus';
  }

  getScoreChangeClass(scoreChange: number): string {
    if (scoreChange > 0) {
      return 'positive';
    }
    if (scoreChange < 0) {
      return 'negative';
    }
    return 'neutral';
  }

  getTopDepartmentLabel(): string {
    return this.summary.topDepartment || 'No data';
  }

  getDepartmentColor(index: number): string {
    return this.departmentColors[index % this.departmentColors.length];
  }

  getGapClass(gap: number): string {
    return gap < 0 ? 'negative' : 'positive';
  }

  private buildScoreDistributionChart(): void {
    this.scoreDistributionChartData = {
      labels: this.scoreDistribution.map(bucket => bucket.range),
      datasets: [{
        data: this.scoreDistribution.map(bucket => bucket.count),
        label: 'Staff Count',
        backgroundColor: this.scoreDistribution.map(bucket => this.getDistributionColor(bucket.range)),
        borderRadius: 4
      }]
    };
  }

  private buildCompetencyChart(): void {
    const competencyAverages = this.getCompetencyAverages();

    this.competencyChartData = {
      labels: competencyAverages.map(item => item.competencyName),
      datasets: [{
        data: competencyAverages.map(item => item.averageRating),
        label: 'Average Rating',
        backgroundColor: competencyAverages.map(item => this.getCompetencyColor(item.averageRating)),
        borderRadius: 4
      }]
    };
  }

  private buildDepartmentTrendChart(): void {
    const years = this.departmentTrend.map(item => String(item.year));
    const departmentNames = Array.from(new Set(
      this.departmentTrend.flatMap(item => item.departments.map(department => department.departmentName))
    ));

    this.departmentTrendChartData = {
      labels: years,
      datasets: departmentNames.map((departmentName, index) => {
        const color = this.departmentColors[index % this.departmentColors.length];
        return {
          data: this.departmentTrend.map(yearPoint =>
            yearPoint.departments.find(department => department.departmentName === departmentName)?.averageScore ?? null
          ),
          label: departmentName,
          borderColor: color,
          backgroundColor: color,
          fill: false,
          tension: 0.35
        };
      })
    };
  }

  private getCompetencyAverages(): OrgWideCompetencyAverageDto[] {
    const competencyMap = new Map<string, number[]>();

    this.competencyBreakdown.forEach(department => {
      department.competencies.forEach(competency => {
        if (!competencyMap.has(competency.competencyName)) {
          competencyMap.set(competency.competencyName, []);
        }
        competencyMap.get(competency.competencyName)!.push(competency.averageRating);
      });
    });

    return Array.from(competencyMap.entries())
      .map(([competencyName, values]) => ({
        competencyName,
        averageRating: this.round(values.reduce((sum, value) => sum + value, 0) / values.length)
      }))
      .sort((a, b) => b.averageRating - a.averageRating);
  }

  private buildWeakestCompetencies(): void {
    this.weakestCompetencies = this.allCompetencyBreakdown
      .map(department => {
        const weakestCompetency = [...department.competencies]
          .sort((a, b) => a.averageRating - b.averageRating)[0];

        if (!weakestCompetency) {
          return null;
        }

        return {
          departmentName: department.departmentName,
          competencyName: weakestCompetency.competencyName,
          averageRating: weakestCompetency.averageRating,
          benchmark: this.competencyBenchmark,
          gap: this.roundToOneDecimal(weakestCompetency.averageRating - this.competencyBenchmark)
        };
      })
      .filter((item): item is WeakestCompetencyInsight => item !== null);
  }

  private getCompetencyColor(score: number): string {
    if (score >= 7) {
      return '#52c41a';
    }
    if (score >= 5) {
      return '#faad14';
    }
    return '#ff4d4f';
  }

  private buildDepartmentOptions(
    ranking: OrgWideDepartmentRankingDto[],
    competencies: OrgWideCompetencyBreakdownDto[],
    trend: OrgWideDepartmentTrendDto[]
  ): string[] {
    return Array.from(new Set([
      ...ranking.map(item => item.departmentName),
      ...competencies.map(item => item.departmentName),
      ...trend.flatMap(item => item.departments.map(department => department.departmentName))
    ])).sort((a, b) => a.localeCompare(b));
  }

  private refreshCharts(): void {
    setTimeout(() => this.charts?.forEach(chart => chart.update()), 0);
  }

  private round(value: number): number {
    return Math.round(value * 100) / 100;
  }

  private roundToOneDecimal(value: number): number {
    return Math.round(value * 10) / 10;
  }
}
