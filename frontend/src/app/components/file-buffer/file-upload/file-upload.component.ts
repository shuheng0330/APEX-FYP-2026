import { Component, EventEmitter, Input, Output } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzUploadFile, NzUploadModule } from 'ng-zorro-antd/upload';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzAlertModule } from 'ng-zorro-antd/alert';

import { FileService } from '../../../services/file.service';

@Component({
  selector: 'app-file-upload',
  imports: [CommonModule, NzButtonModule, NzModalModule, TranslateModule, NzUploadModule,
    NzIconModule, NzAlertModule],
  templateUrl: './file-upload.component.html',
  styleUrls: ['./file-upload.component.scss']
})
export class FileUploadComponent {
  @Input() isMultiple: boolean = false;
  @Input() accept: string = '';
  @Input() maxSizeMB: number = 5;
  @Input() uploadInstruction?: string;
  @Input() uploadHint?: string;
  @Input() templateName?: string;
  @Input() errorTitle?: string;
  @Input() errorMessage?: string;
  @Input() isUploading: boolean = false;

  @Output() selectedFiles: File[] = [];
  @Output() confirm = new EventEmitter<void>();

  visible$ = new BehaviorSubject<boolean>(false);

  constructor(private fileService: FileService) { }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.selectedFiles = [];
    this.errorMessage = undefined;
    this.errorTitle = undefined;
    this.visible$.next(false);
  }

  removeFile(index: number): void {
    this.selectedFiles.splice(index, 1);
  }

  downloadTemplate(): void {
    if (!this.templateName) {
      return;
    }

    this.fileService.downloadTemplate(`${this.templateName}.xlsx`).subscribe((response) => {
      const blob = response.body!;
      const contentDisposition = response.headers.get('content-disposition');
      const filename = contentDisposition?.split('filename=')[1]?.replace(/"/g, '') || 'download.xlsx';

      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  beforeUpload = (file: NzUploadFile): boolean => {
    if (!this.isMultiple) {
      this.selectedFiles = [];
    }
    this.selectedFiles.push(file as unknown as File);
    return false; // prevents auto-upload
  };

  handleOk(): void {
    this.confirm.emit();
  }

}
