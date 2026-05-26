import { Component, EventEmitter, Input, Output } from '@angular/core';
import { LearningMaterial } from '../../../models/learning-material.model';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { StaffLearningMaterialService } from '../../../services/learning-enrollment.service';
import { NzMessageService } from 'ng-zorro-antd/message';
import { StaffLearningMaterial } from '../../../models/staff-learning-material.model';
import {NzTagModule} from 'ng-zorro-antd/tag';

@Component({
  selector: 'app-learning-material-detail',
  imports: [CommonModule, NzTagModule],
  templateUrl: './learning-material-detail.component.html',
  styleUrl: './learning-material-detail.component.scss'
})
export class LearningMaterialDetailComponent {
  @Input() selectedMaterial: any;
  @Input() isEnrolled: boolean = false;
  @Input() userId: string | null = null;
  @Input() actionButton: boolean = false;
  @Output() navigate = new EventEmitter<void>();
  myCourseList: LearningMaterial[] = [];


  constructor(private router: Router,
    private staffLearningMaterialService: StaffLearningMaterialService,
    private message: NzMessageService) {
  }

  startCourse(material: LearningMaterial): void {
    this.navigate.emit();
    this.router.navigate(['/learning-materials', material.materialId, 'preview']);
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
        this.navigate.emit();
        this.router.navigate(['/learning-materials', material.materialId, 'preview']);
      },
      error: (err) => {
        console.error('Enrollment failed', err);
        this.message.error('Failed to enroll in this material. Please try again.');
      }
    });
  }

  unenroll(material: LearningMaterial): void {
    if (!material) {
      this.message.warning('No material selected to unenroll.');
      return;
    }

    if (!this.userId) {
      this.message.error('User is not logged in.');
      return;
    }

    const materialId = material.materialId;
    if (!materialId) {
      this.message.error('Invalid material selected.');
      return;
    }

    // Optionally: disable button to prevent multiple clicks
    material.isUnenrolling = true;

    this.staffLearningMaterialService.unenroll(this.userId, materialId).subscribe({
      next: () => {
        this.message.success(`Successfully unenrolled from "${material.title}"`);

        this.myCourseList = this.myCourseList.filter(m => m.materialId !== materialId);
        this.isEnrolled = false;
      },
      error: (err) => {
        console.error('Unenroll failed', err);

        if (err.status === 404) {
          this.message.error('Enrollment record not found.');
        } else if (err.status === 400) {
          this.message.error('Invalid request. Cannot unenroll.');
        } else {
          this.message.error('Failed to unenroll. Please try again later.');
        }
      },
      complete: () => {
        material.isUnenrolling = false;
      }
    });
  }
}
