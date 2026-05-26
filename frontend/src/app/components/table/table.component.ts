import {Component, Input} from '@angular/core';
import {NzTableModule} from 'ng-zorro-antd/table';
import {NzButtonComponent} from 'ng-zorro-antd/button';
import {NgForOf, NgIf} from '@angular/common';

export interface TableAction {
  label: string;
  onClick: (row: any) => void;
  type?: 'primary' | 'default' | 'link';
}

@Component({
  selector: 'app-reusable-table',
  templateUrl: './table.component.html',
  imports: [
    NzTableModule,
    NzButtonComponent,
    NgForOf,
    NgIf
  ],
  styleUrls: ['./table.component.scss']
})
export class TableComponent {
  @Input() columns: { title: string; key: string }[] = [];
  @Input() data: any[] = [];
  @Input() actions: TableAction[] = [];
}
