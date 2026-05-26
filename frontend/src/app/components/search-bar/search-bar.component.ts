import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {NzInputModule} from 'ng-zorro-antd/input';
import {NzFormModule} from 'ng-zorro-antd/form';
import {NzIconModule} from 'ng-zorro-antd/icon';
import {NzOptionComponent, NzSelectComponent} from 'ng-zorro-antd/select';
import {FormsModule} from '@angular/forms';
import {NzButtonModule} from 'ng-zorro-antd/button';

export interface SearchOption {
  id: number;
  name: string;
}

@Component({
  selector: 'app-search-bar',
  imports: [CommonModule, NzInputModule, NzFormModule, NzIconModule, NzSelectComponent, FormsModule, NzOptionComponent, NzButtonModule],
  templateUrl: './search-bar.component.html',
  styleUrl: './search-bar.component.scss'
})
export class SearchBarComponent {
  @Input() placeholder: string | undefined = 'Search...';
  @Input() optionList: SearchOption[] = [];
  @Input() showSearchFieldSelector: boolean = true;

  @Output() searchChange = new EventEmitter<{ value: string; field: number }>();
  @Output() cleared = new EventEmitter<void>();

  searchValue: string = '';
  selectedField: number = 0;

  onSearchChange(): void {
    this.emitSearch();
  }

  triggerSearch(): void {
    this.emitSearch();
  }

  clearSearch(): void {
    this.searchValue = '';
    this.emitSearch();
    this.cleared.emit();
  }

  private emitSearch(): void {
    this.searchChange.emit({
      value: this.searchValue.trim(),
      field: this.selectedField,
    });
  }
}
