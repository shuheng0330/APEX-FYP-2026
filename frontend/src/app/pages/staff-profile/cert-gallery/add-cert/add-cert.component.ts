import { Component, EventEmitter, HostListener, inject, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule, FormArray, FormControl } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzAutocompleteModule } from 'ng-zorro-antd/auto-complete';

import { FileUploadComponent } from '../../../../components/file-buffer/file-upload/file-upload.component';

import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';
import { StaffSkillService } from '../../../../services/staff-skill.service';
import { StaffCertService } from '../../../../services/staff-cert.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-add-cert',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzAutocompleteModule, FileUploadComponent],
  templateUrl: './add-cert.component.html',
  styleUrl: './add-cert.component.scss'
})
export class AddCertComponent {
  @ViewChild(FileUploadComponent) fileUploadComponent!: FileUploadComponent;
  @Output() formSubmitted = new EventEmitter<void>();

  selectedFiles: File[] = [];
  readonly maxFileSizeMB: number = 5;
  readonly acceptedFileType: string = '.pdf';
  readonly acceptedFileExtension: string[] = ['pdf'];

  private readonly confirmTitleKey = "PAGE.PROFILE.CERT.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.PROFILE.CERT.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.PROFILE.CERT.ADD.CONFIRM.OK";
  private readonly invalidFileTypeErrorTitleKey = 'COMPONENT.FILE.ERROR.INVALID.TITLE';
  private readonly invalidFileTypeErrorMessageKey = 'COMPONENT.FILE.ERROR.INVALID.MSG';
  private readonly invalidFileSizeErrorTitleKey = 'COMPONENT.FILE.ERROR.LARGE.TITLE';
  private readonly invalidFileSizeErrorMessageKey = 'COMPONENT.FILE.ERROR.LARGE.MSG';
  private readonly errorTitleKey = 'NOTIFICATION.ERR.TITLE';
  private readonly errorMessageKey = 'NOTIFICATION.ERR.MESSAGE';

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    file: [null as unknown as File, [Validators.required]],
    cert: [null as unknown as string, [Validators.required]],
    description: [null as unknown as string, null],
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private staffSkillService: StaffSkillService,
    private loadingService: LoadingService, private staffCertService: StaffCertService) { }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  @HostListener('window:resize', ['$event'])
  onResize(event: any) {
    this.adjustDrawerWidth();
  }

  adjustDrawerWidth() {
    if (window.innerWidth <= 768) {
      this.drawerWidth = '100%';
    } else {
      this.drawerWidth = '736px';
    }
  }

  initialize(): void {
    this.adjustDrawerWidth();
    this.initialized$.next(true);
  }

  uninitialize(): void {
    this.validateForm.reset({
      cert: null as unknown as string,
      description: null as unknown as string,
    });

    this.initialized$.next(false);
  }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
  }

  openFileSelector(): void {
    this.fileUploadComponent.open();
  }

  uploadProfilePicture(): void {
    this.selectedFiles = this.fileUploadComponent.selectedFiles;
    if (this.fileUploadComponent.selectedFiles.length == 1) {
      const file = this.fileUploadComponent.selectedFiles.at(0)!;

      const maxSizeBytes = this.maxFileSizeMB * 1024 * 1024;
      const validExtensions = this.acceptedFileExtension;

      const fileName = file.name.toLowerCase();
      const fileExtension = fileName.split('.').pop()!;
      const isValidExtension = validExtensions.includes(fileExtension);
      const validMimeTypes = [
        'application/pdf',
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

      this.validateForm.get('file')?.setValue(file);

      this.fileUploadComponent.close();
    }
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

      const certiifcateName = this.validateForm.get('cert')?.value;
      const description = this.validateForm.get('description')?.value;
      const file = this.validateForm.get('file')?.value;

      if (!certiifcateName || !file) return;

      this.staffCertService.uploadCert(certiifcateName, file, description ?? '').subscribe({
        next: () => {
          this.fileUploadComponent.close();
          this.formSubmitted.emit();

          this.uninitialize();
          this.close();
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
        }
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

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}



