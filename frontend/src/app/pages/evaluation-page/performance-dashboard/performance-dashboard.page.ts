import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzRateModule } from 'ng-zorro-antd/rate';
import { FormsModule } from '@angular/forms';
import { NzSkeletonComponent } from 'ng-zorro-antd/skeleton';
import { Title } from '@angular/platform-browser';
import { EvaluationService } from '../../../services/evaluation.service';
import { NzMessageService } from 'ng-zorro-antd/message';
import { RoleCompetencyService } from '../../../services/role-competency.service';
import { ActivatedRoute } from '@angular/router';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzProgressFormatter, NzProgressModule } from 'ng-zorro-antd/progress';
import { EvaluationDTO, RatingDTO } from '../../../models/evaluation.model';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzInputDirective } from 'ng-zorro-antd/input';
import { StaffService } from '../../../services/staff.service';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { AuthService } from '../../../services/auth.service';
import { StaffTemp } from '../../../models/staff-temp.model';
import { catchError, forkJoin, of } from 'rxjs';
import { StaffProfileService } from '../../../services/staff-profile.service';
import { StaffProfile } from '../../../models/staff.model';
import { NzImageModule } from 'ng-zorro-antd/image';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration, ChartType } from 'chart.js';

@Component({
  selector: 'evaluation-page',
  standalone: true,
  templateUrl: './performance-dashboard.page.html',
  styleUrls: ['./performance-dashboard.page.scss'],
  imports: [CommonModule, TranslatePipe, NzTableModule, NzModalModule, NzRateModule, FormsModule, NzSkeletonComponent, NzIconModule, NzProgressModule,
    NzEmptyModule, NzInputDirective, NzAlertModule, NzImageModule, BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())]
})

export class PerformanceDashboardPage implements OnInit {
  @ViewChild('ratingFormTpl', { static: true }) ratingFormTpl!: TemplateRef<any>;
  @ViewChild(BaseChartDirective) chart?: BaseChartDirective;
  profileImageFallBackUrl: string = 'assets/avatar.png';
  profileImageUrl: string = this.profileImageFallBackUrl;
  profileData?: StaffProfile;
  mailto?: string;
  titleKey: string = "PAGE.PERFORMANCE.TITLE";
  staffId!: string;
  staffEvaluations: EvaluationDTO[] = [];
  latestEvaluation?: EvaluationDTO;
  latestCompetenciesRatings: RatingDTO[] = [];
  overallScore: number = 0;
  comment: string = '';
  loading: boolean = true;
  value: number = 0;
  staff: StaffTemp | undefined;
  userId: string | null = null;

  // Chart config
  public lineChartData: ChartConfiguration<'line'>['data'] = {
    labels: [], // will hold evaluation dates
    datasets: [
      {
        data: [], // overall scores
        label: 'Overall Score',
        fill: false,
        borderColor: '#1890ff',
        backgroundColor: '#1890ff',
        tension: 0.4
      }
    ]
  };

  public lineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    plugins: {
      legend: {
        display: true,
        position: 'top'
      },
      tooltip: {
        enabled: true
      },
      title: {
        display: true,
        text: 'Overall Performance Trend',
        font: {
          size: 18,
          weight: 'bold',
        },
        color: '#000000',
        padding: {
          top: 10,
          bottom: 20
        }
      },
    },
    scales: {
      y: {
        min: 0,
        max: 100,
        title: {
          display: true,
          text: 'Overall Score (%)'
        }
      },
      x: {
        title: {
          display: true,
          text: 'Evaluation Date'
        }
      }
    }
  };

  readonly tooltips = ['Extremely Poor', 'Very Poor', 'Poor', 'Fair', 'Average', 'Satisfactory', 'Good', 'Very Good', 'Excellent', 'Exceptional'];
  percent: NzProgressFormatter | undefined;

  constructor(private translate: TranslateService, private titleService: Title, private modal: NzModalService, private route: ActivatedRoute,
    private evaluationService: EvaluationService, private message: NzMessageService, private staffService: StaffService,
    private roleCompetenciesService: RoleCompetencyService, private auth: AuthService, private staffProfileService: StaffProfileService) {
  }

  ngOnInit(): void {
    this.userId = this.auth.userId;
    this.translate.get(this.titleKey).subscribe((translatedTitle: string) => {
      this.titleService.setTitle(translatedTitle);
    });
    const paramId = this.route.snapshot.paramMap.get('staffId');
    if (paramId) {
      this.staffId = paramId;
    } else if (this.userId) {
      this.staffId = this.userId;
    } else {
      // Should not happen due to AuthGuard, but good for safety
      this.message.error("Unable to determine user identity.");
      return;
    }
    this.getStaffEvaluations();
  }

  getStaffEvaluations() {
    this.evaluationService.getAllEvaluationsByStaffId(this.staffId).subscribe(evaluations => {
      this.staffEvaluations = evaluations;


      // Sort by date ascending
      const sortedEvals = this.staffEvaluations.sort(
        (a, b) => new Date(a.createdAt!).getTime() - new Date(b.createdAt!).getTime()
      );

      // Populate chart labels and data
      this.lineChartData.labels = sortedEvals.map(e => {
        if (e.evaluationCycleEndDate) {
          return new Date(e.evaluationCycleEndDate).toLocaleDateString();
        }
        return new Date(e.createdAt!).toLocaleDateString();
      });
      this.lineChartData.datasets[0].data = sortedEvals.map(e => e.overallScore ?? 0);

      console.log('Chart labels:', this.lineChartData.labels);
      console.log('Chart data:', this.lineChartData.datasets[0].data);

      // Force chart update
      setTimeout(() => {
        this.chart?.update();
      }, 0);


      const latestEval = evaluations.reduce((latest, current) => {
        return !latest || new Date(current.createdAt!) > new Date(latest.createdAt!) ? current : latest;
      }, null as EvaluationDTO | null);

      if (latestEval) {
        this.latestEvaluation = latestEval;
        this.comment = latestEval.comment;
      }

      this.latestCompetenciesRatings = evaluations.flatMap(e => e.ratings);
      this.loadUserProfile(this.staffId);//TODO: this api is used to get staff details, maybe can add into evaluationDTO
    })
  }

  onClickViewAll() {
    this.modal.create({
      nzTitle: 'Latest Evaluation',
      nzMaskClosable: false,
      nzFooter: null,
      nzContent: this.ratingFormTpl,
      nzWrapClassName: 'my-custom-style'
    })
  }

  getStaffByStaffId() {
    this.staffService.getStaffByIdTempToBeReplaced(this.staffId).subscribe(staff => {
      this.staff = staff;
      this.loading = false;
    })
  }

  loadUserProfile(staffId: string): void {
    forkJoin({
      profile: this.staffProfileService.get(staffId),
      profilePic: this.staffProfileService.getProfilePicture(staffId).pipe(
        catchError(() => of(null))
      )
    }).subscribe({
      next: ({ profile, profilePic }) => {
        this.profileData = profile;
        this.mailto = `mailto:${profile.staff.email}`;

        if (profilePic && profilePic.body) {
          this.profileImageUrl = URL.createObjectURL(profilePic.body);
        } else {
          this.profileImageUrl = this.profileImageFallBackUrl;
        }
        this.loading = false;
      }
    });
  }

  getPerformanceCategory(score: number) {
    if (score >= 80) {
      return {
        colorClass: 'score-green',
        description: 'Excellent performance. Consistently exceeds expectations and demonstrates strong competency mastery.'
      };
    } else if (score >= 60) {
      return {
        colorClass: 'score-orange',
        description: 'Performance meets most expectations. Shows solid understanding but may need improvement in some competency areas.'
      };
    } else {
      return {
        colorClass: 'score-red',
        description: 'Below expected performance. Requires close guidance and development in key competencies.'
      };
    }
  }

  getHistoricalEvaluations(): EvaluationDTO[] {
    if (!this.latestEvaluation || !this.staffEvaluations.length) {
      return [];
    }
    // Return all evaluations except the latest one, sorted by date descending
    return this.staffEvaluations
      .filter(e => e.evaluationId !== this.latestEvaluation?.evaluationId)
      .sort((a, b) => new Date(b.createdAt!).getTime() - new Date(a.createdAt!).getTime());
  }

  formatScore(rating: number): string {
    return rating + '/10';
  }

}
