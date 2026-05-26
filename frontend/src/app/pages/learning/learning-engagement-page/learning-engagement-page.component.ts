import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { LearningMaterial } from '../../../models/learning-material.model';
import { LearningMaterialService } from '../../../services/learning-material.service';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzDrawerModule, NzDrawerRef, NzDrawerService } from 'ng-zorro-antd/drawer';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import {
  LearningMaterialDetailComponent
} from '../../../components/learning-material/learning-material-detail/learning-material-detail.component';
import { AuthService } from '../../../services/auth.service';
import { forkJoin } from 'rxjs';
import { StaffLearningMaterialService } from '../../../services/learning-enrollment.service';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { TranslatePipe } from '@ngx-translate/core';
import { StaffLearningMaterial } from '../../../models/staff-learning-material.model';
import {NzSkeletonComponent} from 'ng-zorro-antd/skeleton';
//TODO: add my learning learning material detail
@Component({
  selector: 'app-learning-engagement-page',
  standalone: true,
  imports: [CommonModule, RouterLink, NzIconModule, NzDrawerModule, LearningMaterialDetailComponent, NzTabsModule, NzEmptyModule, NzProgressModule, NzButtonModule, TranslatePipe, NzSkeletonComponent],
  templateUrl: './learning-engagement-page.component.html',
  styleUrl: './learning-engagement-page.component.scss'
})
export class LearningEngagementPageComponent implements OnInit {
  isLoading = false;
  allMaterials: LearningMaterial[] = [];
  recommendedList: LearningMaterial[] = [];
  userId: string | null = null;
  selectedMaterial?: LearningMaterial;
  inProgressCourseList: LearningMaterial[] = [];
  completedCourseList: LearningMaterial[] = [];
  allCourseList: LearningMaterial[] = [];
  progress: number = 0;
  titleKey: string = "PAGE.LEARNING_ENGAGEMENT.TITLE";
  courseDetailDrawerRef?: NzDrawerRef;

  @ViewChild('materialDetailsTpl', { static: false }) materialDetailsTpl?: TemplateRef<{
    $implicit: { value: string };
    drawerRef: NzDrawerRef<string>;
  }>;

  constructor(private learningService: LearningMaterialService, private router: Router, private drawerService: NzDrawerService, private auth: AuthService
    , private staffLearningMaterialService: StaffLearningMaterialService, private message: NzMessageService) { }

  ngOnInit(): void {
    this.isLoading = true;
    this.userId = this.auth.userId;
    this.loadData(); // TODO: error, userId sometimes cant get so list empty
  }

  private loadData(): void {
    forkJoin({
      allMaterials: this.learningService.getAllLearningMaterials(),
      recommended: this.learningService.getRecommendationMaterialList(this.userId!)
    }).subscribe({
      next: ({ allMaterials, recommended }) => {
        this.allMaterials = allMaterials ?? [];
        this.recommendedList = recommended ?? [];
        this.isLoading = false;
      },
      error: err => {
        console.error('Error fetching materials:', err);
        this.isLoading = false;
      }
    });
    this.getEnrolledCourses(this.userId!);
  }

  get recommended(): LearningMaterial[] {
    return (this.recommendedList || []).slice(0, 6);
  }

  get exploreAllMaterial(): LearningMaterial[] {
    return (this.allMaterials || []).slice(0, 6);
  }

  openDetails(material: LearningMaterial): void {
    this.selectedMaterial = material;

    if (this.courseDetailDrawerRef) {
      this.courseDetailDrawerRef.close();
    }

    this.courseDetailDrawerRef = this.drawerService.create({
      nzTitle: 'Course Details',
      nzContent: this.materialDetailsTpl,
      nzWidth: window.innerWidth > 768 ? '680px' : '100%',
      nzWrapClassName: 'custom-drawer'
    });

    this.courseDetailDrawerRef.afterClose.subscribe(() => {
      this.courseDetailDrawerRef = undefined;
    });
  }

  onCloseCourseDetailDrawer(): void {
    this.courseDetailDrawerRef?.close();
  }

  openCategory(category: 'recommended' | 'explore'): void {
    this.router.navigate(['/learning/engagement', category]);
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
        this.allCourseList.push(material);
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
    forkJoin({
      courses: this.staffLearningMaterialService.getEnrolledCourses(staffId),
      progressList: this.staffLearningMaterialService.getAllProgress(staffId)
    }).subscribe(({ courses, progressList }) => {
      this.allCourseList = courses || [];

      this.allCourseList.forEach(course => {
        const matchedProgress = progressList.find(p => p.materialId === course.materialId);
        course.progress = matchedProgress ? matchedProgress.progress : 0;
      });

      this.inProgressCourseList = this.allCourseList.filter(course => (course.progress ?? 0) < 100)
      this.completedCourseList = this.allCourseList.filter(course => (course.progress ?? 0) >= 100);
    });
  }

  // Placeholder progress calculation until real progress is available
  // getProgress(materialId: number): number {
  //   // if(this.userId){
  //   //   this.staffLearningMaterialService.getProgress(this.userId, materialId).subscribe(dto => {
  //   //     this.progress = dto.progress ?? 0;
  //   //   })
  //   // }
  //   // return this.progress;
  // }

  isEnrolled(materialId: number | undefined): boolean {
    if (materialId == null) return false;
    return this.allCourseList.some(m => m.materialId === materialId);
  }

}

