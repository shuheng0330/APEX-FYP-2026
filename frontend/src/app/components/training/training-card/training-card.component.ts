import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTagComponent } from 'ng-zorro-antd/tag';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzDropDownADirective, NzDropDownDirective, NzDropdownMenuComponent } from 'ng-zorro-antd/dropdown';
import { NzMenuDirective, NzMenuItemComponent } from 'ng-zorro-antd/menu';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'training-card',
  standalone: true,
  templateUrl: './training-card.component.html',
  styleUrls: ['./training-card.component.scss'],
  imports: [CommonModule, NzCardModule, NzTagComponent, NzIconModule, NzDropDownADirective, NzDropDownDirective, NzDropdownMenuComponent, NzMenuDirective, NzMenuItemComponent, TranslatePipe]
})

export class TrainingCardComponent {
  @Input() title!: string;
  @Input() visible: boolean = false;
  @Input() description!: string;
  @Input() startDate!: string;
  @Input() endDate!: string;
  @Input() venue!: string;
  @Input() capacity!: number;
  @Input() startTime!: string;
  @Input() endTime!: string;
  @Input() isPublic?: boolean;
  @Input() actionLabel?: string;
  @Input() isPast: boolean = false;
  @Input() participantsNumber?: number;
  @Input() importantNotes: string[] = [];
  @Input() attendanceStatus?: 'present' | 'absent';
  @Output() actionClicked = new EventEmitter<void>();
  @Output() onEdit = new EventEmitter<void>();
  @Output() onDelete = new EventEmitter<void>();
  @Output() onAssign = new EventEmitter<void>();

  constructor(
    private auth: AuthService
  ) { }

  ngOnInit(): void {

  }

  onActionClicked(event: MouseEvent): void {
    event.stopPropagation();
    this.actionClicked.emit();
  }

  onMenuTriggerClick(event: MouseEvent): void {
    event.stopPropagation();
  }

  onEditClicked(event: MouseEvent): void {
    event.stopPropagation();
    this.onEdit.emit();
  }

  onDeleteClicked(event: MouseEvent): void {
    event.stopPropagation();
    this.onDelete.emit();
  }

  onAssignClicked(event: MouseEvent): void {
    event.stopPropagation();
    this.onAssign.emit();
  }

  formatTimeToAMPM(time: string | undefined): string {
    if (!time) return '';

    const [hourStr, minuteStr] = time.split(':');
    let hour = parseInt(hourStr, 10);
    const minute = minuteStr;
    const ampm = hour >= 12 ? 'PM' : 'AM';

    hour = hour % 12 || 12; // Convert 0 or 12 to 12, 13 to 1, etc.

    return `${hour.toString().padStart(2, '0')}:${minute} ${ampm}`;
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.auth.hasRole(role));
  }

}
