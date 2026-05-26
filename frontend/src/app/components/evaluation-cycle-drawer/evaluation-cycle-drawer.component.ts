import { Component, OnInit, Input, Output, EventEmitter, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzTimelineModule } from 'ng-zorro-antd/timeline';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { EvaluationCycleDto, EvaluationCycleService } from '../../services/evaluation-cycle.service';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzTooltipDirective } from 'ng-zorro-antd/tooltip';
import { differenceInCalendarDays } from 'date-fns';

@Component({
    selector: 'app-evaluation-cycle-drawer',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        NzFormModule,
        ReactiveFormsModule,
        NzDrawerModule,
        NzButtonModule,
        NzBadgeModule,
        NzTimelineModule,
        NzModalModule,
        NzDatePickerModule,
        NzDescriptionsModule,
        NzEmptyModule,
        NzSpinModule,
        NzIconModule,
        NzCollapseModule,
        NzDividerModule,
        NzTooltipDirective
    ],
    templateUrl: './evaluation-cycle-drawer.component.html',
    styleUrls: ['./evaluation-cycle-drawer.component.scss']
})
export class EvaluationCycleDrawerComponent implements OnInit {
    @Input() visible = false;
    @Output() visibleChange = new EventEmitter<boolean>();

    currentCycle: EvaluationCycleDto | null = null;
    history: EvaluationCycleDto[] = [];
    loading = false;
    historyLoading = false;

    // New Cycle Modal
    isModalVisible = false;
    newCycleForm: FormGroup;
    isSubmitting = false;
    isEditMode = false;
    drawerWidth = '420px';

    constructor(
        private evaluationCycleService: EvaluationCycleService,
        private fb: FormBuilder,
        private message: NzMessageService
    ) {
        this.newCycleForm = this.fb.group({
            dateRange: [null, [Validators.required]]
        });
    }

    ngOnInit(): void {
        this.updateDrawerWidth();
        if (this.visible) {
            this.loadData();
        }
    }

    @HostListener('window:resize', ['$event'])
    onResize(): void {
        this.updateDrawerWidth();
    }

    private updateDrawerWidth(): void {
        this.drawerWidth = window.innerWidth <= 576 ? '100%' : '420px';
    }

    ngOnChanges(): void {
        if (this.visible) {
            this.loadData();
        }
    }

    close(): void {
        this.visible = false;
        this.visibleChange.emit(false);
    }

    loadData(): void {
        this.loading = true;
        this.evaluationCycleService.getCurrentCycle().subscribe({
            next: (data) => {
                this.currentCycle = data;
                this.loading = false;
            },
            error: () => {
                this.loading = false;
                // If 404 or null, it might just mean no active cycle, which is fine
            }
        });

        this.historyLoading = true;
        this.evaluationCycleService.getHistory().subscribe({
            next: (data) => {
                this.history = data;
                this.historyLoading = false;
            },
            error: () => {
                this.historyLoading = false;
            }
        });
    }

    // Status Badge Logic
    getStatusColor(status: string | undefined): 'success' | 'processing' | 'default' | 'error' | 'warning' {
        switch (status) {
            case 'OPEN': return 'success'; // Green
            case 'UPCOMING': return 'processing'; // Blue
            case 'CLOSED': return 'default'; // Grey
            default: return 'default';
        }
    }

    getStatusText(status: string | undefined): string {
        return status || 'UNKNOWN';
    }

    get hasActiveCycle(): boolean {
        return !!this.currentCycle && (this.currentCycle.status === 'OPEN' || this.currentCycle.status === 'UPCOMING');
    }

    // Open New Cycle Modal
    showNewCycleModal(editMode = false): void {
        this.isEditMode = editMode;
        this.isModalVisible = true;

        if (editMode && this.currentCycle) {
            this.newCycleForm.patchValue({
                dateRange: [new Date(this.currentCycle.startDate), new Date(this.currentCycle.endDate)]
            });
        }
    }

    handleCancelModal(): void {
        this.isModalVisible = false;
        this.newCycleForm.reset();
        this.isEditMode = false;
    }

    handleOkModal(): void {
        if (this.newCycleForm.valid) {
            this.isSubmitting = true;
            const dateRange = this.newCycleForm.value.dateRange;

            const dto: EvaluationCycleDto = {
                startDate: this.formatDate(dateRange[0]),
                endDate: this.formatDate(dateRange[1]),
                allDepartments: true // Default as per user request
            };

            if (this.isEditMode && this.currentCycle?.id) {
                // Update existing cycle
                this.evaluationCycleService.updateCycle(this.currentCycle.id, dto).subscribe({
                    next: (updatedCycle) => {
                        this.message.success('Evaluation cycle updated successfully');
                        this.finishSubmit(updatedCycle);
                    },
                    error: (err) => {
                        this.isSubmitting = false;
                        this.message.error('Failed to update evaluation cycle');
                    }
                });
            } else {
                // Open new cycle
                this.evaluationCycleService.openNewCycle(dto).subscribe({
                    next: (newCycle) => {
                        this.message.success('New evaluation cycle opened successfully');
                        this.finishSubmit(newCycle);
                    },
                    error: (err) => {
                        this.isSubmitting = false;
                        if (err?.error?.message !== 'An evaluation cycle is already open') {
                            this.message.error('Failed to open new evaluation cycle');
                        }
                    }
                });
            }
        } else {
            Object.values(this.newCycleForm.controls).forEach(control => {
                if (control.invalid) {
                    control.markAsDirty();
                    control.updateValueAndValidity({ onlySelf: true });
                }
            });
        }
    }
    private finishSubmit(cycle: EvaluationCycleDto): void {
        this.isModalVisible = false;
        this.isSubmitting = false;
        this.isEditMode = false;
        this.newCycleForm.reset();
        this.currentCycle = cycle;
        this.loadData();
    }

    private formatDate(date: Date): string {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    disabledDate = (current: Date): boolean => {
        // Can not select days before today
        return differenceInCalendarDays(current, new Date()) < 0;
    };
}
