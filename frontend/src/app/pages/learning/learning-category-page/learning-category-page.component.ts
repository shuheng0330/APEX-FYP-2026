import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LearningMaterial } from '../../../models/learning-material.model';
import { LearningMaterialService } from '../../../services/learning-material.service';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinComponent } from 'ng-zorro-antd/spin';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import {
  LearningMaterialDetailComponent
} from '../../../components/learning-material/learning-material-detail/learning-material-detail.component';
import { catchError, forkJoin, of } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { NzMessageService } from 'ng-zorro-antd/message';
import { StaffLearningMaterialService } from '../../../services/learning-enrollment.service';
import { StaffLearningMaterial } from '../../../models/staff-learning-material.model';
import { NzInputDirective, NzInputGroupComponent, NzInputGroupWhitSuffixOrPrefixDirective } from 'ng-zorro-antd/input';
import { NzOptionComponent, NzSelectComponent } from 'ng-zorro-antd/select';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { OrgChartService } from '../../../services/orgChart.service';
import { CompetencyService } from '../../../services/competency.service';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-learning-category-page',
  standalone: true,
  imports: [CommonModule, NzButtonModule, NzIconModule, NzSpinComponent, LearningMaterialDetailComponent, RouterLink, NzInputDirective, NzInputGroupComponent, NzInputGroupWhitSuffixOrPrefixDirective, NzOptionComponent, NzSelectComponent, ReactiveFormsModule, TranslatePipe, FormsModule, NzDrawerModule],
  templateUrl: './learning-category-page.component.html',
  styleUrl: './learning-category-page.component.scss'
})
export class LearningCategoryPageComponent implements OnInit, OnDestroy {
  isLoading = false;
  title = '';
  allMaterials: LearningMaterial[] = [];
  recommendedList: LearningMaterial[] = [];
  selectedMaterial?: LearningMaterial;
  userId: string | null = null;
  category: 'recommended' | 'explore' | null = null;
  myCourseList: LearningMaterial[] = [];
  completedCourseList: LearningMaterial[] = [];
  searchValue?: string;
  optionList: Option[] = []
  searchField: number = 0;

  listOfDisplayData: LearningMaterial[] = [];
  fetchedLearningMaterialList: LearningMaterial[] = [];

  departments: Option[] = [];
  competencies: Option[] = [];

  selectedDepartmentIds: number[] = [];
  selectedCompetenciesIds: number[] = [];

  // Mobile Drawer Logic
  isMobile = false;
  drawerVisible = false;
  private destroy$ = new Subject<void>();

  private readonly searchAnyKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchTitleKey = 'PAGE.LEARNING_MANAGEMENT.SEARCH.FIELD.TITLE';
  private readonly searchLearningOutcomeKey = 'PAGE.LEARNING_MANAGEMENT.SEARCH.FIELD.OUTCOME';
  private readonly searchDescKey = 'PAGE.LEARNING_MANAGEMENT.SEARCH.FIELD.DESC';

  constructor(
    private route: ActivatedRoute,
    private learningMaterialService: LearningMaterialService,
    private auth: AuthService,
    private router: Router,
    private message: NzMessageService,
    private staffLearningMaterialService: StaffLearningMaterialService,
    private orgChartService: OrgChartService,
    private competencyService: CompetencyService,
    private translateService: TranslateService,
    private breakpointObserver: BreakpointObserver
  ) { }

  ngOnInit(): void {

    this.category = this.route.snapshot.paramMap.get('category') as 'recommended' | 'explore' | null;
    this.title = this.category === 'explore' ? 'Explore Courses' : 'Recommended Courses';
    this.userId = this.auth.userId
    this.isLoading = true;
    forkJoin({
      allMaterials: this.learningMaterialService.getAllLearningMaterials(),
      recommended: this.learningMaterialService.getRecommendationMaterialList(this.userId!)
    }).subscribe({
      next: ({ allMaterials, recommended }) => {
        this.allMaterials = allMaterials ?? [];
        this.recommendedList = recommended ?? [];

        this.fetchedLearningMaterialList = this.category === 'explore' ? this.allMaterials : this.recommendedList;
        this.listOfDisplayData = [...this.fetchedLearningMaterialList];
        this.loadDropdownOptions();
        this.loadOptionList();

        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error fetching materials:', err);
        this.isLoading = false;
      }
    });
    if (this.userId) {
      this.getEnrolledCourses(this.userId);
    }

    // Breakpoint observer
    this.breakpointObserver.observe([Breakpoints.Handset, Breakpoints.Tablet, '(max-width: 768px)'])
      .pipe(takeUntil(this.destroy$))
      .subscribe(result => {
        this.isMobile = result.matches;
        if (!this.isMobile) {
          this.drawerVisible = false; // Close drawer if switching to desktop
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get items(): LearningMaterial[] {
    return this.listOfDisplayData;
  }

  selectMaterial(material: LearningMaterial): void {
    this.selectedMaterial = material;
    if (this.isMobile) {
      this.drawerVisible = true;
    }
  }

  closeDrawer(): void {
    this.drawerVisible = false;
    // Optional: Deselect material if desired when closing drawer, 
    // or keep it selected but just hide drawer. 
    // Keeping it selected might be better state preservation.
  }


  loadDropdownOptions(): void {
    forkJoin({
      departments: this.orgChartService.getDepartmentList().pipe(
        catchError(err => {
          console.error('Failed to load departments', err);
          return of([]);
        })
      ),
      competencies: this.competencyService.getAllCompetencies().pipe(
        catchError(err => {
          console.error('Failed to load competencies', err);
          return of([]);
        })
      )
    }).subscribe(({ departments, competencies }) => {
      this.departments = departments.map((d) => ({
        id: d.id,
        name: d.name
      }));

      this.competencies = competencies.map((c) => ({
        id: c.id!,
        name: c.name
      }));
      console.log('drop down options loaded', this.departments, this.competencies);
    });
  }


  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchTitleKey) },
      { id: 2, name: this.translateService.instant(this.searchDescKey) },
      { id: 3, name: this.translateService.instant(this.searchLearningOutcomeKey) },
    ]
  }

  enrollLearningMaterial(material: LearningMaterial): void {
    if (!material) {
      return;
    }

    const enrollment: StaffLearningMaterial = {
      staffId: this.userId!,
      materialId: material.materialId
    };

    this.staffLearningMaterialService.enrollLearningMaterial(enrollment).subscribe({
      next: (response) => {
        this.myCourseList.push(material);
        this.message.success(`Successfully enrolled in "${material.title}"`);
        this.router.navigate(['/learning-materials', material.materialId, 'preview']);
      },
      error: (err) => {
        console.error('Enrollment failed', err);
        this.message.error('Failed to enroll in this material. Please try again.');
      }
    });
  }

  getEnrolledCourses(staffId: string) {
    this.staffLearningMaterialService.getEnrolledCourses(staffId).subscribe(courses => {
      this.myCourseList = courses || [];
      // can filter isCompleted at here
      this.completedCourseList = [];
    })
  }

  isEnrolled(materialId: number | undefined): boolean {
    if (materialId == null) return false;
    return this.myCourseList.some(m => m.materialId === materialId);
  }

  goBack(): void {
    this.router.navigate(['/learning/engagement']);
  }

  search(): void {
    this.applyFilters();
  }

  resetSearch(): void {
    this.searchValue = '';
    this.search();
  }

  private applyFilters(): void {
    const searchVal = this.searchValue?.trim().toLowerCase() || '';

    let filtered = [...this.fetchedLearningMaterialList];

    if (this.selectedDepartmentIds?.length > 0) {
      filtered = filtered.filter(material =>
        material.departments?.some(d => this.selectedDepartmentIds.includes(d.id))
      );
    }

    if (this.selectedCompetenciesIds && this.selectedCompetenciesIds?.length > 0) {
      filtered = filtered.filter(material =>
        material.competency?.some((c: any) => this.selectedCompetenciesIds.includes(c.id))
      );
    }

    if (searchVal) {
      filtered = filtered.filter((item: LearningMaterial) => {
        const title = item.title?.toLowerCase() || '';
        const desc = item.description?.toLowerCase() || '';
        const outcomes = item.learningOutcomes?.map(o => o.toLowerCase()) || [];

        const matchesTitle = title.includes(searchVal);
        const matchesDesc = desc.includes(searchVal);
        const matchesOutcome = outcomes.some(o => o.includes(searchVal));

        if (this.searchField === 0) {
          // ALL FIELDS
          return matchesTitle || matchesDesc || matchesOutcome;
        } else if (this.searchField === 1) {
          return matchesTitle;
        } else if (this.searchField === 2) {
          return matchesDesc;
        } else if (this.searchField === 3) {
          return matchesOutcome;
        }

        return false;
      });
    }

    this.listOfDisplayData = filtered;
  }

}


