import { Component, CUSTOM_ELEMENTS_SCHEMA, NgZone, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrainingCardComponent } from '../../../components/training/training-card/training-card.component';
import { NzIconModule } from 'ng-zorro-antd/icon';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { TrainingService } from '../../../services/training.service';
import { BulkInvitation, TrainingInvitation, TrainingProgram, StaffConflictDTO } from '../../../models/training.model';
import { NzFormDirective, NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzSwitchComponent } from 'ng-zorro-antd/switch';
import { NzSkeletonComponent } from 'ng-zorro-antd/skeleton';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { NzDrawerModule, NzDrawerRef, NzDrawerService } from 'ng-zorro-antd/drawer';
import { NzTabComponent, NzTabSetComponent } from 'ng-zorro-antd/tabs';
import {
  NzTableFilterFn,
  NzTableFilterList,
  NzTableModule,
  NzTableSortFn,
  NzTableSortOrder,
  NzTdAddOnComponent,
  NzThSelectionComponent
} from 'ng-zorro-antd/table';
import { StaffService } from '../../../services/staff.service';
import { TrainingInvitationService } from '../../../services/training.invitation.service';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzCascaderModule } from 'ng-zorro-antd/cascader';
import { NzSafeAny } from 'ng-zorro-antd/core/types';
import { StaffTemp } from '../../../models/staff-temp.model';
import { NzOptionComponent, NzSelectComponent } from 'ng-zorro-antd/select';
import { OrgChartService } from '../../../services/orgChart.service';
import { Role } from '../../../models/role.model';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { TrainingRegistrationService } from '../../../services/training.registration.service';
import { MapsLoaderService } from '../../../services/maps-loader.service';
import { NzEmptyComponent } from 'ng-zorro-antd/empty';
import { TrainingAttendanceService } from '../../../services/training.attendance.service';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { catchError, forkJoin, of } from 'rxjs';
import { CompetencyService } from '../../../services/competency.service';
import { NzTooltipDirective } from 'ng-zorro-antd/tooltip';

declare const google: any;

interface Option {
  id: number;
  name: string;
}

interface ItemData {
  name: string;
  role: Role;
}

interface ColumnItem {
  name: string;
  sortOrder: NzTableSortOrder | null;
  sortFn: NzTableSortFn<ItemData> | null;
  listOfFilter: NzTableFilterList;
  filterFn: NzTableFilterFn<ItemData> | null;
  filterMultiple: boolean;
  isSearchable: boolean;
  nzWidth: string;
  sortDirections: NzTableSortOrder[];
}

@Component({
  selector: 'training-management-page',
  standalone: true,
  templateUrl: './training-management-page.html',
  styleUrls: ['./training-management-page.scss'],
  imports: [CommonModule, TrainingCardComponent, NzIconModule, NzFormDirective, ReactiveFormsModule, NzFormModule, NzInputModule, NzModalModule,
    NzButtonModule, NzDatePickerModule, NzSwitchComponent, FormsModule, NzSkeletonComponent, TranslatePipe, NzDrawerModule, NzTabSetComponent, NzTabComponent, NzTableModule, NzThSelectionComponent, NzTdAddOnComponent, NzCascaderModule, NzSelectComponent, NzOptionComponent, NzDropDownModule, NzEmptyComponent, NzRadioModule, NzTagModule, NzTooltipDirective
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})

export class TrainingManagementPageComponent implements OnInit {
  @ViewChild('addModalTpl', { static: true }) addModalTpl!: TemplateRef<any>;
  @ViewChild('trainingDetailTpl', { static: true }) trainingDetailTpl!: TemplateRef<any>;
  @ViewChild('invitationDrawerTpl', { static: true }) invitationDrawerTpl!: TemplateRef<any>;
  @ViewChild('rejectReasonTpl', { static: false }) rejectReasonTpl?: TemplateRef<any>;

  readonly titleKey = 'PAGE.TRAINING_MANAGEMENT.TITLE';
  private readonly searchAnyKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchTitleKey = 'PAGE.TRAINING_MANAGEMENT.SEARCH.FIELD.TITLE';
  private readonly searchVenueKey = 'PAGE.TRAINING_MANAGEMENT.SEARCH.FIELD.VENUE';
  private readonly searchDescKey = 'PAGE.TRAINING_MANAGEMENT.SEARCH.FIELD.DESC';

  form!: FormGroup;
  map!: google.maps.Map;
  marker!: google.maps.Marker;
  // Tab State
  selectedTabIndex: number = 0;

  // Data State
  allTrainingPrograms: TrainingProgram[] = []; // Raw data from API
  ongoingTrainings: TrainingProgram[] = [];    // Separated Ongoing
  pastTrainings: TrainingProgram[] = [];       // Separated Past

  filteredOngoing: TrainingProgram[] = [];     // Filtered Ongoing
  filteredPast: TrainingProgram[] = [];        // Filtered Past

  // Display
  trainings: TrainingProgram[] = []; // Legacy field, might remove or keep as 'current acting list'
  filteredTrainings: TrainingProgram[] = []; // Currently displayed filtered list (points to filteredOngoing or filteredPast)
  listOfDisplayData: TrainingProgram[] = []; // Final displayed page/slice

  loading = true;
  total = 0;
  pageSize = 10;
  pageIndex = 1;

  // Filters
  searchValue = '';
  searchField = 0; // 0:all, 1:title, 2:desc, 3:venue
  visibilityFilter = 'all'; // 'all', 'public', 'private'

  suffixSearchSelect?: TemplateRef<any>;
  suffixSearchButton?: TemplateRef<any>;
  inputClearTpl?: TemplateRef<any>;

  listOfColumns: ColumnItem[] = [];
  listOfColumnsParticipants: ColumnItem[] = [];
  // Filters
  selectedDepartmentIds: number[] = [];
  selectedCompetenciesIds: number[] = [];

  departments: Array<{ id: number; name: string }> = [];
  competencies: Array<{ id: number, name: string }> = [];

  visibilityOptions = [
    { label: 'All', value: 'all' },
    { label: 'Public', value: 'public' },
    { label: 'Private', value: 'private' }
  ];
  optionList: Option[] = []
  selectedTraining?: TrainingProgram;
  importantNoteForm!: FormGroup;
  invitationResponse: TrainingInvitation[] = [];
  selectedRejection?: TrainingInvitation;
  checked = false;
  indeterminate = false;
  listOfCurrentPageData: readonly StaffTemp[] = [];
  setOfCheckedId = new Set<string>();
  staffConflicts = new Map<string, StaffConflictDTO>();
  importantNotes: Array<{ id: number; controlInstance: string }> = [];
  listOfStaff: StaffTemp[] = [];
  filteredStaffList: StaffTemp[] = [];
  registeredStaffList: StaffTemp[] = [];
  filteredRegisteredStaffList: StaffTemp[] = [];
  staffSearch: string = '';
  visible = false;
  registeredStaffSearch: string = '';
  values: NzSafeAny[][] | null = null;
  listOfRole: NzTableFilterList = [];
  listOfDepartment: NzTableFilterList = [];
  visibleParticipants = false;
  trainingHasStarted: boolean = false;
  viewMode: 'card' | 'table' = 'card';
  isTableInitialized = false;

  trainingDetailDrawerRef?: NzDrawerRef;
  minDate: string = '';

  constructor(
    private fb: FormBuilder,
    private modal: NzModalService,
    private trainingService: TrainingService,
    private translate: TranslateService,
    private titleService: Title,
    private drawerService: NzDrawerService,
    private staffService: StaffService,
    private trainingInvitationService: TrainingInvitationService,
    private message: NzMessageService,
    private translateService: TranslateService,
    private orgChartService: OrgChartService,
    private trainingRegistrationService: TrainingRegistrationService,
    private ngZone: NgZone,
    private mapsLoader: MapsLoaderService,
    private trainingAttendanceService: TrainingAttendanceService,
    private competencyService: CompetencyService
  ) {

  }

  ngOnInit() {

    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['', [Validators.required, Validators.maxLength(1000)]],
      startDate: ['', [Validators.required]],
      endDate: ['', [Validators.required, this.endDateValidator()]],
      startTime: ['', [Validators.required]],
      endTime: ['', [Validators.required, this.endTimeValidator()]],
      venue: ['', [Validators.required]],
      departmentIds: [[], [Validators.required]],
      competencyIds: [[], [Validators.required]],
      locationName: ['', [Validators.required]],
      latitude: [''],
      longitude: [''],
      capacity: ['', [Validators.required, this.numberValidator, Validators.min(1)]],
      isPublic: [false],
      targetRoles: [[]],
      isMandatory: [false],
    });
    this.importantNoteForm = this.fb.group({});
    this.translate.get(this.titleKey).subscribe((translatedTitle: string) => {
      this.titleService.setTitle(translatedTitle);
    });
    this.mapsLoader.load();
    this.minDate = new Date().toLocaleDateString('en-CA');
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();
    this.addField();
    this.loadOptionList();
    this.loadDropdownOptions();
    this.getAllTrainingPrograms();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchTitleKey) },
      { id: 2, name: this.translateService.instant(this.searchDescKey) },
      { id: 3, name: this.translateService.instant(this.searchVenueKey) }
    ]
  }

  loadDropdownOptions(): void {
    forkJoin({
      departments: this.orgChartService.getDepartmentList().pipe(
        catchError(err => {
          console.error('Failed to load departments', err);
          return of([]);
        })
      ),
      competencies: this.competencyService.getAllCompetencies().pipe(
        catchError(err => {
          console.error('Failed to load competencies', err);
          return of([]);
        })
      )
    }).subscribe(({ departments, competencies }) => {
      this.departments = departments.map((d) => ({
        id: d.id,
        name: d.name
      }));

      this.competencies = competencies
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((c) => ({
          id: c.id!,
          name: c.name
        }));

      console.log('drop down options loaded', this.departments, this.competencies);
    });
  }

  refreshCheckedStatus(): void {
    const listOfEnabledData = this.listOfCurrentPageData;

    if (listOfEnabledData.length === 0) {
      this.checked = false;
      this.indeterminate = false;
      return;
    }

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.id));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.id)) && !this.checked;
  }

  onCurrentPageDataChange($event: readonly StaffTemp[]): void {
    this.listOfCurrentPageData = $event;
    this.refreshCheckedStatus();
  }

  updateCheckedSet(id: string, checked: boolean): void {
    if (checked) {
      this.setOfCheckedId.add(id);
    } else {
      this.setOfCheckedId.delete(id);
    }
  }

  onItemChecked(id: string, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(checked: boolean): void {
    this.listOfCurrentPageData
      .filter(item => !this.isStaffAlreadyInvited(item.id))
      .forEach(item => this.updateCheckedSet(item.id, checked));

    this.refreshCheckedStatus();
  }

  addField(e?: MouseEvent): void {
    e?.preventDefault();
    const id = this.importantNotes.length > 0 ? this.importantNotes[this.importantNotes.length - 1].id + 1 : 0;

    const control = {
      id,
      controlInstance: `note${id}`
    };
    const index = this.importantNotes.push(control);
    console.log(this.importantNotes[this.importantNotes.length - 1]);
    this.importantNoteForm.addControl(
      this.importantNotes[index - 1].controlInstance,
      this.fb.control('')
    );
  }

  removeField(i: { id: number; controlInstance: string }, e: MouseEvent): void {
    e.preventDefault();

    if (this.importantNotes.length > 1) {
      const index = this.importantNotes.indexOf(i);
      this.importantNotes.splice(index, 1); // remove from UI
      this.importantNoteForm.removeControl(i.controlInstance); // remove control
    }
  }

  isStaffAlreadyInvited(staffId: string): boolean {
    const alreadyInvited = this.invitationResponse?.some(inv => inv.staff.id === staffId);
    const alreadyRegistered = this.filteredRegisteredStaffList?.some(reg => reg.id === staffId)
    return alreadyInvited || alreadyRegistered;
  }

  isStaffOccupied(staffId: string): boolean {
    return this.staffConflicts.has(staffId);
  }

  getConflictDetails(staffId: string): StaffConflictDTO | undefined {
    return this.staffConflicts.get(staffId);
  }

  formatTime(time: string | undefined): string {
    if (!time) return '';
    const [hours, minutes] = time.split(':');
    const h = parseInt(hours, 10);
    const m = parseInt(minutes, 10);
    const ampm = h >= 12 ? 'PM' : 'AM';
    const h12 = h % 12 || 12;
    return `${h12}:${m < 10 ? '0' + m : m} ${ampm}`;
  }

  getAllStaffList() {
    this.staffService.getAllStaffTempToBeReplaced().subscribe(staff => {
      this.listOfStaff = staff.sort((a, b) =>
        a.name.localeCompare(b.name)
      );
      this.listOfRole = this.buildFilterList(staff.map(s => s.role.name));
      this.listOfDepartment = this.buildFilterList(staff.map(s => s.role.orgChart.name));
      this.listOfColumns = this.generateColumns();
      this.filteredStaffList = [...this.listOfStaff];
    })
  }

  getAllRegisteredStaffList(trainingId: number) {
    this.trainingRegistrationService.getAllRegisteredStaffList(trainingId).subscribe(registeredStaff => {

      this.trainingAttendanceService.getTrainingAttendanceByTrainingId(trainingId)
        .subscribe(attendanceList => {

          const attendanceMap = new Map(
            attendanceList.map(a => [a.staffId, a])
          );

          this.registeredStaffList = registeredStaff
            .map(staff => {
              const attendance = attendanceMap.get(staff.id);
              return {
                ...staff,
                attendance: attendance && attendance.withinGeofence ? 'present' : '-'
              };
            })
            .sort((a, b) => a.name.localeCompare(b.name));

          // Update UI data
          this.listOfColumnsParticipants = this.generateColumnsParticipant();
          this.filteredRegisteredStaffList = [...this.registeredStaffList];
        });
    });
  }

  isInvitationLoading = false;

  getInvitationResponseByTrainingId(trainingId: number) {
    this.isInvitationLoading = true;
    forkJoin({
      invitations: this.trainingInvitationService.getAllInvitationsByTraining(trainingId),
      conflicts: this.trainingRegistrationService.getConflictingRegistrations(trainingId)
    }).subscribe({
      next: ({ invitations, conflicts }) => {
        this.invitationResponse = invitations.sort((a, b) => b.invitationId - a.invitationId);
        this.invitationResponse = [...this.invitationResponse];

        this.staffConflicts.clear();
        conflicts.forEach(c => this.staffConflicts.set(c.staffId, c));
        console.log('Occupied staff count:', this.staffConflicts.size);
        this.isInvitationLoading = false;
      },
      error: (err) => {
        console.error('Failed to load invitation data', err);
        this.isInvitationLoading = false;
      }
    });
  }

  getAllTrainingPrograms() {
    this.loading = true;
    this.trainingService.getAllTrainingPrograms().subscribe(trainings => {
      // Sort globally by start date descending first, or we can sort after split
      this.allTrainingPrograms = trainings.sort((a, b) =>
        new Date(b.startDate).getTime() - new Date(a.startDate).getTime());
      this.distributeTrainings(); // Split into Ongoing/Past

      this.applyFilters(); // Apply local filters to both lists
      this.getAllStaffList();
      this.loading = false;
    }, error => {
      console.error('Error loading trainings', error);
      this.loading = false;
    });
  }

  private distributeTrainings(): void {
    const now = new Date();
    // Reset lists
    this.ongoingTrainings = [];
    this.pastTrainings = [];

    this.allTrainingPrograms.forEach(t => {

      const endDateTime = new Date(`${t.endDate}T${t.endTime}`);

      if (now > endDateTime) {
        this.pastTrainings.push(t);
      } else {
        this.ongoingTrainings.push(t);
      }
    });
  }

  onLoadMore(): void {
    // Increment page for Card view
    this.pageIndex++;
    this.updateDisplayData();
  }

  onViewModeChange(mode: 'card' | 'table'): void {
    this.viewMode = mode;
    this.pageIndex = 1; // Reset page on view switch
    this.updateDisplayData();
  }

  onAddTrainingFormPopup(): void {
    const modalWidth = window.innerWidth > 768 ? '650px' : '100%';
    const modalRef = this.modal.create({
      nzTitle: 'Add New Training',
      nzContent: this.addModalTpl,
      nzMaskClosable: false,
      nzWrapClassName: 'my-custom-style',
      nzWidth: modalWidth,
      nzOkText: 'Submit',
      nzOnOk: () => this.handleSubmitAndClose(),
    });

    modalRef.afterOpen.subscribe(() => {
      const input = document.getElementById('venueInput') as HTMLInputElement;
      if (input) {
        this.initAutocomplete(input);
      }
    })

    modalRef.afterClose.subscribe(() => {
      this.resetForm();
    });
  }

  search(): void {
    this.applyFilters();
  }

  resetSearch(): void {
    this.searchValue = '';
    this.search();
  }



  private applyFilters(): void {
    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || '';

    // Helper function to filter a list
    const filterList = (list: TrainingProgram[]) => {
      let filtered = [...list];

      // Apply type filter
      if (this.visibilityFilter === 'public') {
        filtered = filtered.filter(t => t.isPublic);
      } else if (this.visibilityFilter === 'private') {
        filtered = filtered.filter(t => !t.isPublic);
      }

      // Apply department filter
      if (this.selectedDepartmentIds && this.selectedDepartmentIds.length > 0) {
        filtered = filtered.filter(t =>
          t.departments?.some((d: any) => this.selectedDepartmentIds.includes(d.id))
        );
      }

      //Apply competencies filter
      if (this.selectedCompetenciesIds && this.selectedCompetenciesIds.length > 0) {
        filtered = filtered.filter(t =>
          t.competencies?.some((d: any) => this.selectedCompetenciesIds.includes(d.id))
        );
      }

      // Apply text search
      if (searchVal) {
        filtered = filtered.filter((item: TrainingProgram) => {
          const matchesTitle = item.title?.toLowerCase().includes(searchVal);
          const matchesDescription = item.description?.toLowerCase().includes(searchVal);
          const matchesVenue = item.venue?.toLowerCase().includes(searchVal);

          if (this.searchField === 0) {
            return matchesTitle || matchesDescription || matchesVenue;
          } else if (this.searchField === 1) {
            return matchesTitle;
          } else if (this.searchField === 2) {
            return matchesDescription;
          } else if (this.searchField === 3) {
            return matchesVenue;
          }
          return false;
        });
      }
      return filtered;
    };

    // Filter BOTH lists
    this.filteredOngoing = filterList(this.ongoingTrainings);
    this.filteredPast = filterList(this.pastTrainings);

    // Set current displayed list
    if (this.selectedTabIndex === 0) {
      this.filteredTrainings = this.filteredOngoing;
    } else {
      this.filteredTrainings = this.filteredPast;
    }

    this.total = this.filteredTrainings.length;
    this.pageIndex = 1; // Reset to page 1 on filter change
    this.updateDisplayData();
  }

  private updateDisplayData(): void {
    // Ensure filteredTrainings points to correct tab data
    if (this.selectedTabIndex === 0) {
      this.filteredTrainings = this.filteredOngoing;
    } else {
      this.filteredTrainings = this.filteredPast;
    }

    // Update total for pagination
    this.total = this.filteredTrainings.length;

    if (this.viewMode === 'table') {
      this.listOfDisplayData = [...this.filteredTrainings];
    } else {
      // Card view: Sliced data for "Load More"
      // Show (pageSize * pageIndex) items
      const limit = this.pageSize * this.pageIndex;
      this.listOfDisplayData = this.filteredTrainings.slice(0, limit);
    }
  }

  onTabChange(index: number): void {
    this.selectedTabIndex = index;
    this.pageIndex = 1;
    this.updateDisplayData();
  }

  sendInvitations(trainingId: number | undefined): void {
    if (trainingId !== undefined) {
      const bulkInvitation: BulkInvitation = {
        trainingId: trainingId,
        staffIds: Array.from(this.setOfCheckedId)
      };
      this.trainingInvitationService.inviteStaff(bulkInvitation).subscribe({
        next: () => {
          this.message.success('Invitations sent successfully');
          this.getInvitationResponseByTrainingId(trainingId);
          this.setOfCheckedId.clear();
          this.refreshCheckedStatus();
        },
        error: (err) => {
          const title = err?.error?.title || 'Invitation Failed';
          const message = err?.error?.message || 'Unable to send invitations. Please try again later.';

          this.modal.error({
            nzTitle: title,
            nzContent: message,
            nzCentered: true
          });
        }
      });
    } else {
      console.log('Training not found for id:', trainingId);
    }
  }

  handleSubmitAndClose(): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      if (this.form.valid && this.importantNoteForm.valid) {
        const newTraining = this.form.value;

        const roleIds: number[] = Array.isArray(newTraining.targetRoles)
          ? newTraining.targetRoles
            .filter((path: number[]) => path.length > 1) // only accept child selections
            .map((path: number[]) => path[path.length - 1]) // extract last (child) value
          : [];


        const notes: string[] = this.importantNotes.map(note => {
          return this.importantNoteForm.get(note.controlInstance)?.value?.trim();
        }).filter(note => note);

        const training = {
          ...newTraining,
          roleIds: roleIds,
          importantNotes: notes.length > 0 ? notes : null
        };

        this.trainingService.createTrainingProgram(training).subscribe(
          (training) => {
            this.getAllTrainingPrograms();
            this.allTrainingPrograms.sort((a, b) =>
              new Date(b.startDate).getTime() - new Date(a.startDate).getTime());

            this.distributeTrainings();
            this.applyFilters();
            this.message.success('The training program has been successfully created.');
            resolve(true);
          },
          (error) => {
            console.error("Failed to create training", error);
            resolve(false);
          }
        );
      } else {
        Object.values(this.form.controls).forEach(control => {
          control.markAsDirty();
          control.updateValueAndValidity();
        });

        Object.values(this.importantNoteForm.controls).forEach(control => {
          control.markAsDirty();
          control.updateValueAndValidity();
        });

        resolve(false);
      }
    });
  }

  resetForm(): void {
    this.form.reset();
    this.importantNotes.forEach(note => {
      this.importantNoteForm.removeControl(note.controlInstance);
    });
    this.importantNotes = [];
    this.importantNoteForm.reset();

    this.addField();
  }

  onOpenTrainingDetails(trainingId: number): void {
    this.selectedTraining = this.allTrainingPrograms.find(training => training.trainingId === trainingId);

    if (!this.selectedTraining) {
      console.warn('Training not found for id:', trainingId);
      return;
    }

    const today = new Date();

    if (this.selectedTraining?.startDate && this.selectedTraining?.startTime) {
      const [hours, minutes] = this.selectedTraining.startTime.split(':').map(Number);
      const trainingStart = new Date(this.selectedTraining.startDate);
      trainingStart.setHours(hours, minutes, 0, 0);

      this.trainingHasStarted = trainingStart <= today;
    } else {
      this.trainingHasStarted = false;
    }

    if (this.trainingDetailDrawerRef) {
      this.trainingDetailDrawerRef.close();
    }

    const drawerWidth = window.innerWidth > 768 ? '750px' : '100%';

    this.trainingDetailDrawerRef = this.drawerService.create({
      nzContent: this.trainingDetailTpl,
      nzWidth: drawerWidth,
      nzClosable: false, // Disable default close button
      nzTitle: undefined, // Remove default title
      nzWrapClassName: 'custom-drawer training-detail-drawer',
    });


    this.trainingDetailDrawerRef.afterOpen.subscribe(() => {
      this.getAllRegisteredStaffList(trainingId);
      this.registeredStaffSearch = '';
      this.filteredStaffList = [...this.listOfStaff];
      this.filteredRegisteredStaffList = [...this.registeredStaffList];
    });

    this.trainingDetailDrawerRef.afterClose.subscribe(() => {
      this.trainingDetailDrawerRef = undefined;
    });
  }

  onCloseTrainingDetailDrawer(): void {
    this.trainingDetailDrawerRef?.close();
  }

  onEditTrainingFormPopup(trainingId: number): void {
    this.selectedTraining = this.allTrainingPrograms.find(training => training.trainingId === trainingId);
    if (!this.selectedTraining) {
      console.warn('Training not found for id:', trainingId);
      return;
    }

    this.trainingDetailDrawerRef?.close();

    this.form.patchValue({
      title: this.selectedTraining.title,
      venue: this.selectedTraining.venue,
      startDate: this.selectedTraining.startDate,
      endDate: this.selectedTraining.endDate,
      startTime: this.selectedTraining.startTime,
      endTime: this.selectedTraining.endTime,
      departmentIds: this.selectedTraining.departments?.map((dept: any) => dept.id),
      competencyIds: this.selectedTraining.competencies?.map(c => c.id) || [],
      capacity: this.selectedTraining.capacity,
      isPublic: this.selectedTraining.isPublic,
      description: this.selectedTraining.description,
      locationName: this.selectedTraining.locationName,
      latitude: this.selectedTraining.latitude,
      longitude: this.selectedTraining.longitude
    });

    this.importantNoteForm.reset();
    Object.keys(this.importantNoteForm.controls).forEach(key => {
      this.importantNoteForm.removeControl(key);
    });
    this.importantNotes = [];

    if (this.selectedTraining.importantNotes && this.selectedTraining.importantNotes.length > 0) {
      this.selectedTraining.importantNotes.forEach((note, index) => {
        const controlInstance = `note${index}`;
        console.log('note', controlInstance)
        this.importantNoteForm.addControl(controlInstance, new FormControl(note));
        this.importantNotes.push({
          id: index,
          controlInstance
        });
      });
    }

    const modalWidth = window.innerWidth > 768 ? '650px' : '100%';
    const modalRef = this.modal.create({
      nzTitle: 'Edit Training',
      nzContent: this.addModalTpl,
      nzMaskClosable: false,
      nzWidth: modalWidth,
      nzWrapClassName: 'my-custom-style',
      nzOkText: 'Submit',
      nzOnOk: () => this.onUpdateTrainingDetails(trainingId),
    });

    modalRef.afterOpen.subscribe(() => {
      const input = document.getElementById('venueInput') as HTMLInputElement;
      if (input) {
        this.mapsLoader.load()
          .then(() => {
            this.initAutocomplete(input);
          })
          .catch(err => {
            console.error('Failed to load Google Maps:', err);
          });
      }
    })

    modalRef.afterClose.subscribe(() => {
      this.resetForm();
    });
  }

  onUpdateTrainingDetails(trainingId: number): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      if (this.form.valid && this.importantNoteForm.valid) {
        const updatedTraining = this.form.value;
        const roleIds = Array.isArray(updatedTraining.targetRoles)
          ? updatedTraining.targetRoles.map((roleId: number[]) => roleId[roleId.length - 1])
          : [];
        const notes: string[] = this.importantNotes.map(note => {
          return this.importantNoteForm.get(note.controlInstance)?.value;
        });

        const training = {
          ...updatedTraining,
          roleIds: roleIds,
          importantNotes: notes
        };

        this.trainingService.updateTrainingProgram(trainingId, training).subscribe(() => {
          this.getAllTrainingPrograms();
          resolve(true);
          this.modal.closeAll();
          this.form.reset();
          console.log("Training Details Updated");

        },
          (error) => {
            console.error("Failed to create training", error);
            resolve(false);
          });

      } else {
        Object.values(this.form.controls).forEach(control => {
          control.markAsDirty();
          control.updateValueAndValidity();
        });

        Object.values(this.importantNoteForm.controls).forEach(control => {
          control.markAsDirty();
          control.updateValueAndValidity();
        });
        resolve(false);
      }
    });
  }

  onDeleteTraining(trainingId: number | undefined): void {
    if (trainingId !== undefined) {

      console.log("trainingId", trainingId);
      this.modal.confirm({
        nzTitle: 'Are you sure you want to delete this training?',
        nzCentered: true,
        nzOnOk: () => this.trainingService.deleteTrainingProgram(trainingId).subscribe(() => {
          this.getAllTrainingPrograms();
          this.modal.closeAll();
          this.trainingDetailDrawerRef?.close();
          this.message.success('The training program has been successfully deleted.');
        }),
      });
    } else {
      console.log('Training not found for id:', trainingId);
    }
  }

  onOpenInvitationDrawer(trainingId: number): void {
    this.selectedTraining = this.allTrainingPrograms.find(training => training.trainingId === trainingId);

    if (!this.selectedTraining) {
      console.warn('Training not found for id:', trainingId);
      return;
    }

    this.setOfCheckedId.clear();
    this.checked = false;
    this.indeterminate = false;
    this.listOfCurrentPageData = [];
    this.staffConflicts.clear(); // Clear stale conflicts immediately
    this.staffSearch = '';

    // Trigger fetch immediately so loading state is true before drawer renders
    this.getInvitationResponseByTrainingId(trainingId);

    const drawerWidth = window.innerWidth > 768 ? '750px' : '100%';

    const drawerRef = this.drawerService.create({
      nzContent: this.invitationDrawerTpl,
      nzTitle: 'Training Assignment',
      nzWidth: drawerWidth,
      nzWrapClassName: 'custom-drawer',
      // nzClosable: false
    });

    drawerRef.afterOpen.subscribe(() => {
      // Logic that MUST wait for animation can go here
    });

    drawerRef.afterClose.subscribe(() => {
      this.setOfCheckedId.clear();
      this.checked = false;
      this.indeterminate = false;
    });

  }

  onViewRejectionReason(inv: TrainingInvitation): void {
    this.selectedRejection = inv;
    this.modal.create({
      nzTitle: 'Rejection Reason',
      nzContent: this.rejectReasonTpl!,
      nzFooter: null,
      nzCentered: true,
      nzMaskClosable: true,
      nzWidth: '520px'
    });
  }

  filterStaff(): void {
    const q = this.staffSearch.trim().toLowerCase();
    if (!q) {
      this.filteredStaffList = [...this.listOfStaff];
      return;
    }
    this.filteredStaffList = this.listOfStaff.filter((s) => {
      const name = s.name?.toLowerCase() || '';
      return name.includes(q);
    });
  }

  filterRegisteredStaff(): void {
    const q = this.registeredStaffSearch.trim().toLowerCase();
    if (!q) {
      this.filteredRegisteredStaffList = [...this.registeredStaffList];
      return;
    }
    this.filteredRegisteredStaffList = this.registeredStaffList.filter((s) => {
      const name = s.name?.toLowerCase() || '';
      return name.includes(q);
    });
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

  numberValidator(control: AbstractControl): { [key: string]: any } | null {
    const value = control.value;
    if (value === null || value === '') return null; // skip check if empty (handled by required)
    if (isNaN(Number(value))) {
      return { notNumber: true };
    }
    if (!Number.isInteger(Number(value))) {
      return { notInteger: true };
    }
    return null;
  }

  endDateValidator(): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
      const formGroup = control.parent;
      if (!formGroup) return null;

      const startDate = formGroup.get('startDate')?.value;
      const endDate = control.value;

      if (!startDate || !endDate) return null;

      if (new Date(endDate) < new Date(startDate)) {
        return { endDateBeforeStartDate: true };
      }

      return null;
    };
  }

  endTimeValidator(): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
      const formGroup = control.parent;
      if (!formGroup) return null;

      const startTime = formGroup.get('startTime')?.value;
      const endTime = control.value;

      if (!startTime || !endTime) return null;

      // Convert "HH:mm" strings into Date objects on the same arbitrary date
      const start = new Date(`1970-01-01T${startTime}`);
      const end = new Date(`1970-01-01T${endTime}`);

      if (end.getTime() === start.getTime()) {
        return { endTimeSameAsStartTime: true };
      }
      if (end < start) {
        return { endTimeBeforeStartTime: true };
      }

      return null;
    };
  }

  private buildFilterList(values: string[]): NzTableFilterList {
    return Array.from(new Set(values)).map(value => ({ text: value, value }));
  }

  generateColumns(): ColumnItem[] {
    return [
      // {
      //   name: 'Staff',
      //   sortOrder: null,
      //   sortFn: (a, b) => a.name.localeCompare(b.name),
      //   sortDirections: ['ascend', 'descend', null],
      //   filterMultiple: true,
      //   isSearchable: true,
      //   listOfFilter: [],
      //   filterFn: null,
      // },
      {
        name: 'Department',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfDepartment,
        nzWidth: '150px',
        filterFn: (dept: string, item: ItemData) => item.role.orgChart.name.includes(dept),
      },
      {
        name: 'Role',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfRole,
        nzWidth: '150px',
        filterFn: (role: string, item: ItemData) => item.role.name.includes(role),
      }
    ];
  }

  generateColumnsParticipant(): ColumnItem[] {
    return [
      {
        name: 'Department',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfDepartment,
        nzWidth: '150px',
        filterFn: (dept: string, item: ItemData) => item.role.orgChart.name.includes(dept),
      },
      {
        name: 'Role',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfRole,
        nzWidth: '150px',
        filterFn: (role: string, item: ItemData) => item.role.name.includes(role),
      }
      // {
      //   name: 'Attendance',
      //   sortOrder: null,
      //   sortFn: null,
      //   sortDirections: [null],
      //   filterMultiple: false,
      //   isSearchable: false,
      //   listOfFilter: this.listOfRole,
      //   filterFn: (role: string, item: ItemData) => item.role.name.includes(role),
      // }
    ];
  }
  private initAutocomplete(input: HTMLInputElement): void {

    if (!google || !google.maps || !google.maps.places) {
      console.error("Google Maps Places API not ready yet.");
      return;
    }

    const autocomplete = new google.maps.places.Autocomplete(input, {
      fields: ['geometry', 'formatted_address', 'name'],
      types: ['establishment', 'geocode'],
    });

    autocomplete.addListener('place_changed', () => {
      this.ngZone.run(() => {
        const place = autocomplete.getPlace();
        console.log("place", place);
        if (!place.geometry || !place.geometry.location) return;

        const lat = place.geometry.location.lat();
        const lng = place.geometry.location.lng();
        console.log("latitude", lat);
        console.log("longitude", lng);

        this.form.patchValue({
          locationName: place.formatted_address || place.name,
          latitude: lat,
          longitude: lng,
        });
      });
    });
  }

  private showMap(lat: number, lng: number): void {
    const mapElement = document.getElementById('map');
    if (!mapElement) return;

    if (!this.map) {
      this.map = new google.maps.Map(mapElement, {
        center: { lat, lng },
        zoom: 15,
      });
    } else {
      this.map.setCenter({ lat, lng });
    }

    if (this.marker) this.marker.setMap(null);
    this.marker = new google.maps.Marker({
      position: { lat, lng },
      map: this.map,
    });
  }

}


//TODO:  try to not fetch again list, check endTime not later than startTime, separate private & public(?)
//TODO: check the generate table for invite staff table and registered staff table, the filter column, if full add tag
