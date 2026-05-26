import { Component, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, catchError, forkJoin, of } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { NzImageModule } from 'ng-zorro-antd/image';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';

import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';

import { LoadingService } from '../../../services/loading.service';
import { StaffProfile } from '../../../models/staff.model';
import { StaffProfileService } from '../../../services/staff-profile.service';
import { AuthService } from '../../../services/auth.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-general-information',
  imports: [CommonModule, TranslateModule, NzImageModule, NzIconModule, NzFormModule,
    NzInputModule, FormsModule, ReactiveFormsModule, NzModalModule, FileUploadComponent],
  templateUrl: './general-information.component.html',
  styleUrl: './general-information.component.scss'
})
export class GeneralInformationComponent {
  @ViewChild(FileUploadComponent) fileUploadComponent!: FileUploadComponent;

  selectedFiles: File[] = [];
  readonly maxFileSizeMB: number = 5;
  readonly acceptedFileType: string = '.jpg,.jpeg,.png,.heic,.webp';
  readonly acceptedFileExtension: string[] = ['jpg', 'jpeg', 'png', 'heic', 'webp'];

  private readonly confirmTitleKey = "PAGE.PROFILE.GENERAL.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.PROFILE.GENERAL.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.PROFILE.GENERAL.CONFIRM.OK";
  private readonly invalidFileTypeErrorTitleKey = 'COMPONENT.FILE.ERROR.INVALID.TITLE';
  private readonly invalidFileTypeErrorMessageKey = 'COMPONENT.FILE.ERROR.INVALID.MSG';
  private readonly invalidFileSizeErrorTitleKey = 'COMPONENT.FILE.ERROR.LARGE.TITLE';
  private readonly invalidFileSizeErrorMessageKey = 'COMPONENT.FILE.ERROR.LARGE.MSG';
  private readonly errorTitleKey = 'NOTIFICATION.ERR.TITLE';
  private readonly errorMessageKey = 'NOTIFICATION.ERR.MESSAGE';

  private forgotPasswordPage: any = '/profile/forgot-password';
  profileData?: StaffProfile;
  isUploading = false;
  profileImageFallBackUrl: string = 'assets/avatar.png';
  profileImageUrl: string = this.profileImageFallBackUrl;
  mailto?: string;

  private fb = inject(NonNullableFormBuilder);
  validateForm = this.fb.group({
    email: [null as unknown as string, [Validators.required]],
    name: [null as unknown as string, [Validators.required]],
    contact: [null as unknown as string, [Validators.required]],
    department: [{ value: null as unknown as string, disabled: true }, [Validators.required]],
    role: [{ value: null as unknown as string, disabled: true }, [Validators.required]],
    careerPathway: [{ value: null as unknown as string, disabled: true }, [Validators.required]],
    manager: [{ value: null as unknown as string, disabled: true }, [Validators.required]],
    about: [null as unknown as string, []],
  });

  isEditing$ = new BehaviorSubject<boolean>(false);
  isVisiting$ = new BehaviorSubject<boolean>(false);

  confirmModal?: NzModalRef;

  constructor(
    private router: Router, private translateService: TranslateService,
    private modal: NzModalService, private loadingService: LoadingService,
    private staffProfileService: StaffProfileService, private authService: AuthService,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const staffId = params['staffId'];
      if (staffId) {
        if (staffId != this.authService.userId!) this.isVisiting$.next(true);
        else this.isVisiting$.next(false);
        this.loadUserProfile(staffId);
      } else {
        this.isVisiting$.next(false);
        this.loadUserProfile(this.authService.userId!);
      }
    });
    this.validateForm.disable();
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
        this.resetForm();
      }
    });
  }

  resetForm(): void {
    if (this.profileData) {
      this.validateForm.reset({
        email: this.profileData?.staff.email,
        name: this.profileData?.staff.name ?? '',
        contact: this.profileData?.contactNumber ?? '',
        department: this.profileData?.staff.role?.orgChart.name ?? '-',
        role: this.profileData?.staff.role?.name ?? '-',
        careerPathway: this.profileData.staff.careerPathway?.name ?? '-',
        manager: this.profileData?.staff.manager ?
          this.profileData?.staff.manager?.name ?
            `${this.profileData.staff.manager?.name!} (${this.profileData.staff.manager?.email})` :
            `${this.profileData?.staff.manager?.email}` :
          '-',
        about: this.profileData?.about ?? ''
      })

      this.validateForm.disable();
      this.isEditing$.next(false);
    }
  }

  openFileSelector(): void {
    this.fileUploadComponent.open();
  }

  uploadProfilePicture(): void {
    if (this.fileUploadComponent.selectedFiles.length == 1) {
      const file = this.fileUploadComponent.selectedFiles.at(0)!;

      const maxSizeBytes = this.maxFileSizeMB * 1024 * 1024;
      const validExtensions = this.acceptedFileExtension;

      const fileName = file.name.toLowerCase();
      const fileExtension = fileName.split('.').pop()!;
      const isValidExtension = validExtensions.includes(fileExtension);
      const validMimeTypes = [
        'image/jpeg',
        'image/png',
        'image/heic',
        'image/webp',
        'application/octet-stream',
        ''
      ];
      const isValidType = validMimeTypes.includes(file.type);

      if (file.size > maxSizeBytes) {
        this.fileUploadComponent.errorTitle = this.translateService.instant(this.invalidFileSizeErrorTitleKey);
        this.fileUploadComponent.errorMessage = this.translateService.instant(this.invalidFileSizeErrorMessageKey);
        return;
      }
      if (!isValidExtension || !isValidType) {
        this.fileUploadComponent.errorTitle = this.translateService.instant(this.invalidFileTypeErrorTitleKey);
        this.fileUploadComponent.errorMessage = this.translateService.instant(this.invalidFileTypeErrorMessageKey);
        return;
      }

      this.loadingService.show();
      this.fileUploadComponent.isUploading = true;
      this.staffProfileService.uploadProfilePicture(file).subscribe({
        next: () => {
          this.fileUploadComponent.close();
          this.ngOnInit();
        },
        error: (error: HttpErrorResponse) => {
          let errorTitle = this.translateService.instant(this.errorTitleKey);
          let errorMessage = this.translateService.instant(this.errorMessageKey);

          if (error.error?.message) {
            errorMessage = error.error?.message;
          }

          if (error.error?.title) {
            errorTitle = error.error?.title;
          }

          this.fileUploadComponent.errorMessage = errorMessage;
          this.fileUploadComponent.errorTitle = errorTitle;
        },
        complete: () => {
          this.loadingService.hide();
          this.fileUploadComponent.isUploading = false;
        }
      });
    }
  }

  onResetPassword(): void {
    const email = this.validateForm.get('email')?.value;
    if (email && !this.isVisiting$.value) {
      this.router.navigate([this.forgotPasswordPage], {
        queryParams: { email: email }
      });
    }
  }

  onEditInformation(): void {
    this.validateForm.get('email')?.enable();
    this.validateForm.get('name')?.enable();
    this.validateForm.get('contact')?.enable();
    this.validateForm.get('about')?.enable();

    this.isEditing$.next(true);
  }

  showConfirm(): void {
    if (this.validateForm.valid) {
      const confirmTitle = this.translateService.instant(this.confirmTitleKey);
      const confirmContent = this.translateService.instant(this.confirmContentKey);
      const confirmOk = this.translateService.instant(this.confirmOkKey);

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: confirmContent,
        nzOkText: confirmOk,
        nzOnOk: () => this.submit()
      });

    } else {
      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
    }
  }

  submit(): void {
    if (this.validateForm.valid) {
      this.loadingService.show();

      const email = this.validateForm.get('email')?.value;
      const name = this.validateForm.get('name')?.value;
      const contact = this.validateForm.get('contact')?.value;
      const about = this.validateForm.get('about')?.value;

      if (!this.profileData?.staffId || !this.profileData?.staff
        || !email || !name || !contact) return

      this.profileData.staff.name = name;

      const requestBody: StaffProfile = {
        staffId: this.profileData?.staffId!,
        staff: this.profileData?.staff!,
        about: about ?? '',
        contactNumber: contact,
        profilePicturePath: this.profileData?.profilePicturePath ?? '',
        createdBy: this.profileData?.updatedBy,
        createdAt: this.profileData?.createdAt,
        updatedBy: this.profileData?.updatedBy,
        updatedAt: this.profileData?.updatedAt,
      }

      this.staffProfileService.update(requestBody).subscribe({
        complete: () => {
          this.isEditing$.next(false);
          this.validateForm.disable();
          this.loadingService.hide();
        }
      })

    } else {
      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
    }
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
