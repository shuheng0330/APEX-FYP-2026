import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { NzSelectModule } from 'ng-zorro-antd/select';
@Component({
  selector: 'app-table-page-size-selector',
  imports: [TranslateModule, NzSelectModule, CommonModule, FormsModule],
  templateUrl: './table-page-size-selector.component.html',
  styleUrl: './table-page-size-selector.component.scss'
})
export class TablePageSizeSelectorComponent {
  @Input() pageSize: number = 10;
  @Output() pageSizeChange = new EventEmitter<number>();

  @Input() sizeOptions: number[] = [10, 20, 50, 100];

  onSizeChange(newSize: number) {
    this.pageSize = newSize;
    this.pageSizeChange.emit(newSize);
  }
}
