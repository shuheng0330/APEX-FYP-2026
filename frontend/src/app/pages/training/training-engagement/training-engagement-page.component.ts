import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrainingCardComponent } from '../../../components/training/training-card/training-card.component';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { FormsModule, ReactiveFormsModule, } from '@angular/forms';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { isAfter, isEqual, isBefore, startOfDay } from 'date-fns';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzSkeletonComponent } from 'ng-zorro-antd/skeleton';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import {
  TrainingAttendance,
  TrainingInvitation,
  TrainingProgram,
  TrainingRegistration
} from '../../../models/training.model';
import { TrainingService } from '../../../services/training.service';
import { NzDrawerModule, NzDrawerRef, NzDrawerService } from 'ng-zorro-antd/drawer';
import { NzTabComponent, NzTabsModule } from 'ng-zorro-antd/tabs';
import { TrainingRegistrationService } from '../../../services/training.registration.service';
import { AuthService } from '../../../services/auth.service';
import {
  TrainingAssignmentDrawerComponent
} from '../../../components/training/training-assignment-drawer/training-assignment-drawer.component';
import { TrainingInvitationService } from '../../../services/training.invitation.service';
import { SearchBarComponent } from '../../../components/search-bar/search-bar.component';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzCalendarModule } from 'ng-zorro-antd/calendar';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { TrainingAttendanceService } from '../../../services/training.attendance.service';
import { NzOptionComponent, NzSelectComponent } from 'ng-zorro-antd/select';
import { OrgChartService } from '../../../services/orgChart.service';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

//TODO:  add to registered list, view detail, add category for training, add department for training so can filter, backend block register for same training
interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'training-engagement-page',
  standalone: true,
  templateUrl: './training-engagement-page.component.html',
  styleUrls: ['./training-engagement-page.component.scss'],
  imports: [CommonModule, TrainingCardComponent, NzIconModule, ReactiveFormsModule, NzFormModule, NzInputModule, NzModalModule,
    NzButtonModule, NzDatePickerModule, FormsModule, NzSkeletonComponent, TranslatePipe, NzDrawerModule, NzTabComponent, NzTabsModule, SearchBarComponent, NzEmptyModule, NzCalendarModule, NzBadgeModule, NzOptionComponent, NzSelectComponent, NzTagModule, NzToolTipModule],
})

export class TrainingEngagementPageComponent implements OnInit {
  @ViewChild('trainingDetailTpl', { static: false }) trainingDetailTpl?: TemplateRef<{
    $implicit: { value: string };
    drawerRef: NzDrawerRef<string>;
  }>;

  private readonly searchAnyKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchTitleKey = 'PAGE.TRAINING_MANAGEMENT.SEARCH.FIELD.TITLE';
  private readonly searchVenueKey = 'PAGE.TRAINING_MANAGEMENT.SEARCH.FIELD.VENUE';
  private readonly searchDescKey = 'PAGE.TRAINING_MANAGEMENT.SEARCH.FIELD.DESC';

  titleKey: string = "PAGE.TRAINING_ENGAGEMENT.TITLE";
  publicTrainingList: TrainingProgram[] = [];
  listOfDisplayTraining = [...this.publicTrainingList];
  registeredTrainingList: TrainingProgram[] = [];
  upcomingTrainingList: TrainingProgram[] = [];
  listOfDisplayUpcomingTraining = [...this.upcomingTrainingList];
  todayTrainingList: TrainingProgram[] = [];
  tomorrowTrainingList: TrainingProgram[] = [];
  listOfDisplayTodayTraining = [...this.todayTrainingList];
  listOfDisplayTomorrowTraining = [...this.tomorrowTrainingList];
  completedTrainingList: TrainingProgram[] = [];
  listOfDisplayCompletedTraining = [...this.completedTrainingList];
  selectedDate?: Date;
  selectedDepartmentIds: number[] = [];
  departments: Array<{ id: number; name: string }> = [];
  // key: yyyy-mm-dd -> trainings on that date
  calendarEventsByDate: Map<string, TrainingProgram[]> = new Map<string, TrainingProgram[]>();
  registeredTrainingIdList: number[] = [];
  allTrainingList: TrainingProgram[] = [];
  trainingInvitation: TrainingInvitation[] = [];
  selectedTraining?: any;
  loading = true;
  userId: string | null = null;
  optionList: Option[] = [];
  checkedInTrainingIds: number[] = [];
  checkingInTrainingIds: number[] = [];
  visibilityFilter: 'all' | 'public' | 'private' = 'all';
  visibilityOptions = [
    { label: 'All', value: 'all' },
    { label: 'Public', value: 'public' },
    { label: 'Private', value: 'private' }
  ];

  constructor(private titleService: Title,
    private translate: TranslateService,
    private trainingService: TrainingService,
    private modal: NzModalService,
    private drawerService: NzDrawerService,
    private trainingInvitationService: TrainingInvitationService,
    private trainingRegistrationService: TrainingRegistrationService,
    private auth: AuthService,
    private translateService: TranslateService,
    private trainingAttendanceService: TrainingAttendanceService,
    private orgChartService: OrgChartService) {
  }

  ngOnInit() {
    this.userId = this.auth.userId;
    console.log("user id", this.userId);
    this.translate.get(this.titleKey).subscribe((translatedTitle: string) => {
      this.titleService.setTitle(translatedTitle);
    });
    this.getAllTrainingPrograms();
    this.getStaffInvitation();
    this.loadOptionList();
    this.loadDepartments();
  }

  onSearchChange(event?: { value: string; field: number }): void {
    let value = '';
    let field = 0;

    if (event) {
      value = (event.value || '').toLowerCase();
      field = event.field ?? 0;
    }

    const filteredBySearch = this.publicTrainingList.filter((item: TrainingProgram) => {
      if (!event) return true;
      const title = item.title?.toLowerCase() || '';
      const desc = item.description?.toLowerCase() || '';
      const venue = item.venue?.toLowerCase() || '';

      const matchesTitle = title.includes(value);
      const matchesDesc = desc.includes(value);
      const matchesVenue = venue.includes(value);

      if (!value) return true;
      if (field === 0) return matchesTitle || matchesDesc || matchesVenue;
      if (field === 1) return matchesTitle;
      if (field === 2) return matchesDesc;
      if (field === 3) return matchesVenue;
      return true;
    });

    // Apply department filter (if any)
    if (this.selectedDepartmentIds?.length > 0) {
      this.listOfDisplayTraining = filteredBySearch.filter(t =>
        t.departments?.some((d: any) => this.selectedDepartmentIds.includes(d.id))
      );
    } else {
      this.listOfDisplayTraining = filteredBySearch;
    }

  }

  onSearchCleared(): void {
    this.listOfDisplayTraining = [...this.publicTrainingList];
  }

  onSearchChangeUpcoming(event: { value: string; field: number }): void {
    const { value, field } = event;
    this.listOfDisplayUpcomingTraining = this.upcomingTrainingList.filter((item: TrainingProgram) => {
      const matchesTitle = item.title?.toLowerCase().includes(value.toLowerCase());
      const matchesDesc = item.description?.toLowerCase().includes(value.toLowerCase());
      const matchesVenue = item.venue?.toLowerCase().includes(value.toLowerCase());

      if (field === 0) return matchesTitle || matchesDesc || matchesVenue;
      if (field === 1) return matchesTitle;
      if (field === 2) return matchesDesc;
      if (field === 3) return matchesVenue;
      return false;
    });
  }

  onSearchClearedUpcoming(): void {
    this.listOfDisplayUpcomingTraining = [...this.upcomingTrainingList];
  }

  onSearchChangeCompleted(event?: { value: string; field: number }): void {
    let value = '';
    let field = 0;

    if (event) {
      value = (event.value || '').toLowerCase();
      field = event.field ?? 0;
    }

    const searched = this.completedTrainingList.filter((item: TrainingProgram) => {
      if (!event) return true;
      const title = item.title?.toLowerCase() || '';
      const desc = item.description?.toLowerCase() || '';
      const venue = item.venue?.toLowerCase() || '';

      const matchesTitle = title.includes(value);
      const matchesDesc = desc.includes(value);
      const matchesVenue = venue.includes(value);

      if (!value) return true;
      if (field === 0) return matchesTitle || matchesDesc || matchesVenue;
      if (field === 1) return matchesTitle;
      if (field === 2) return matchesDesc;
      if (field === 3) return matchesVenue;
      return true;
    });

    this.listOfDisplayCompletedTraining = searched.filter(item => {
      if (this.visibilityFilter === 'all') return true;
      if (this.visibilityFilter === 'public') return item.isPublic;
      return !item.isPublic; // private
    });
  }

  onSearchClearedCompleted(): void {
    // Only apply visibility filter when search cleared
    this.listOfDisplayCompletedTraining = this.completedTrainingList.filter(item => {
      if (this.visibilityFilter === 'all') return true;
      if (this.visibilityFilter === 'public') return item.isPublic;
      return !item.isPublic;
    });
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchTitleKey) },
      { id: 2, name: this.translateService.instant(this.searchDescKey) },
      { id: 3, name: this.translateService.instant(this.searchVenueKey) }
    ]
  }

  loadDepartments(): void {
    this.orgChartService.getDepartmentList().subscribe((list) => {
      this.departments = (list || []).map((d: any) => ({ id: d.id, name: d.name }));
    });
  }

  getStaffInvitation() {
    this.trainingInvitationService.getTrainingInvitationsByStaffId(this.userId!).subscribe(list => {
      this.trainingInvitation = list;
      this.getUpcomingTrainingList();
    })
  }

  getAllTrainingPrograms() {
    this.trainingService.getAllTrainingPrograms().subscribe(trainings => {
      const now = new Date();
      this.publicTrainingList = trainings.filter(training => training.isPublic)
        .filter(training => {
          const start = this.toDateTime(training.startDate, training.startTime);
          return start > now;
        });

      this.listOfDisplayTraining = [...this.publicTrainingList];

      if (this.userId) {
        this.getTrainingProgramByStaffId(this.userId);
      }

      this.allTrainingList = trainings;
      this.trainingAttendanceService.getAttendedTrainingIdListByStaffId(this.userId!).subscribe(list => {
        this.checkedInTrainingIds = list;
        this.getUpcomingTrainingList();
      });
      this.loading = false;
    })
  }

  onOpenTrainingDetails(trainingId: number): void {
    this.selectedTraining = this.allTrainingList.find(training => training.trainingId === trainingId);

    if (!this.selectedTraining) {
      console.warn('Training not found for id:', trainingId);
      return;
    }

    const drawerWidth = window.innerWidth > 530 ? '530px' : '100%';
    this.drawerService.create({
      nzTitle: 'Training Details',
      nzContent: this.trainingDetailTpl,
      nzWidth: drawerWidth,
      nzWrapClassName: 'custom-drawer'
    });
  }

  getTrainingProgramByStaffId(staffId: string) {
    this.trainingService.getTrainingProgramsByStaffId(staffId).subscribe(training => {
      this.registeredTrainingList = training;
      this.registeredTrainingIdList = training.map(training => training.trainingId);
      this.getUpcomingTrainingList();
    })
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

  onClickRegisterTraining(training: TrainingProgram): void {

    const trainingId = training.trainingId;
    if (trainingId === undefined) {
      console.error('Training ID is undefined');
      return;
    }

    const registration: TrainingRegistration = {
      staffId: this.userId!,
      training: training
    }

    this.modal.confirm({
      nzTitle: 'Are you sure you want to register for this training?',
      nzCentered: true,
      nzOnOk: () => {
        return this.trainingRegistrationService.createTrainingRegistration(registration).toPromise().then(() => {

          if (!this.registeredTrainingIdList.includes(trainingId)) {
            this.registeredTrainingIdList.push(trainingId);
          }

          this.registeredTrainingList.push(training);
          this.getUpcomingTrainingList();

          this.modal.success({
            nzTitle: 'Registration successful',
            nzCentered: true
          });
        }).catch(error => {
          console.error('Registration failed', error);
          const message =
            typeof error.error === 'string'
              ? error.error
              : error.error?.message || 'Registration failed';
          if (error.status === 400) {
            this.modal.error({
              nzTitle: 'Registration Failed',
              nzContent: message,
              nzCentered: true
            });
          } else {
            this.modal.error({
              nzTitle: 'Registration Failed',
              nzContent: 'Please try again later.',
              nzCentered: true
            });
          }
        });
      }
    });
  }

  isWithinCheckInWindow(training: TrainingProgram): boolean {
    if (
      !training?.startDate ||
      !training?.endDate ||
      !training?.startTime ||
      !training?.endTime
    ) return false;

    const now = new Date();

    const todayStr = now.toISOString().split('T')[0];

    // Check date range (day-level)
    if (todayStr < training.startDate || todayStr > training.endDate) {
      return false;
    }

    const todayStart = new Date(`${todayStr}T${training.startTime}`);
    const todayEnd = new Date(`${todayStr}T${training.endTime}`);

    return now >= todayStart && now <= todayEnd;
  }

  openAssignmentDrawer(): void {
    const drawerWidth = window.innerWidth > 768 ? '720px' : '100%';
    const drawerRef = this.drawerService.create<TrainingAssignmentDrawerComponent, { invitations: any[] }, string>({
      nzTitle: 'Training Assignments',
      nzContent: TrainingAssignmentDrawerComponent,
      nzWidth: drawerWidth,
      nzWrapClassName: 'custom-drawer',
      nzContentParams: {
        invitations: this.trainingInvitation
      }
    });

    drawerRef.afterClose.subscribe(() => {
      this.getTrainingProgramByStaffId(this.userId!);
    });
  }

  onCheckIn(trainingId: number, event: Event): void {
    event.stopPropagation(); // Prevent event card click

    if (this.checkedInTrainingIds.includes(trainingId) || this.checkingInTrainingIds.includes(trainingId)) {
      return;
    }

    this.checkingInTrainingIds.push(trainingId);

    let watchId: number | null = null;
    const maxWaitTime = 20000; // 20 seconds max wait
    const requiredAccuracy = 20; // meters

    const timeoutId = setTimeout(() => {
      if (watchId !== null) {
        navigator.geolocation.clearWatch(watchId);
      }
      this.checkingInTrainingIds = this.checkingInTrainingIds.filter(id => id !== trainingId);
      this.modal.error({
        nzTitle: 'Location Timeout',
        nzContent: 'Could not get a sufficiently accurate location. Please try again or move outdoors.',
        nzCentered: true
      });
    }, maxWaitTime);

    watchId = navigator.geolocation.watchPosition(
      (pos) => {
        console.log('Current Lat:', pos.coords.latitude);
        console.log('Current Long:', pos.coords.longitude);
        console.log('Accuracy (meters):', pos.coords.accuracy);

        // Only proceed if accuracy is good enough
        if (pos.coords.accuracy <= requiredAccuracy) {
          clearTimeout(timeoutId);
          if (watchId !== null) {
            navigator.geolocation.clearWatch(watchId);
          }

          const attendance: TrainingAttendance = {
            trainingId: trainingId,
            staffId: this.userId!,
            checkInLatitude: pos.coords.latitude,
            checkInLongitude: pos.coords.longitude
          };

          this.trainingAttendanceService.checkin(attendance).subscribe({
            next: (response) => {
              this.checkingInTrainingIds = this.checkingInTrainingIds.filter(id => id !== trainingId);

              if (response.success) {
                this.checkedInTrainingIds.push(trainingId);
                this.modal.success({
                  nzTitle: 'Attendance Marked Successfully',
                  nzContent: response.message || 'Your attendance has been recorded.',
                  nzCentered: true
                });
              } else {
                this.modal.error({
                  nzTitle: 'Attendance Failed',
                  nzContent: response.message || 'Unable to mark attendance. Please try again.',
                  nzCentered: true
                });
              }
            },
            error: (error) => {
              this.checkingInTrainingIds = this.checkingInTrainingIds.filter(id => id !== trainingId);
              console.error('Check-in failed', error);
              this.modal.error({
                nzTitle: 'Attendance Failed',
                nzContent: 'Unable to mark attendance. Please try again.',
                nzCentered: true
              });
            }
          });
        }
      },
      (err) => {
        clearTimeout(timeoutId);
        if (watchId !== null) {
          navigator.geolocation.clearWatch(watchId);
        }
        this.checkingInTrainingIds = this.checkingInTrainingIds.filter(id => id !== trainingId);

        console.error('Geolocation error', err);
        this.modal.error({
          nzTitle: 'Location Access Required',
          nzContent: 'Please enable location access to mark attendance.',
          nzCentered: true
        });
      },
      {
        enableHighAccuracy: true,
        maximumAge: 0
      }
    );
  }


  // onCheckIn(trainingId: number, event: Event): void {
  //   event.stopPropagation(); // Prevent event card click
  //
  //   if (this.checkedInTrainingIds.includes(trainingId) || this.checkingInTrainingIds.includes(trainingId)) {
  //     return;
  //   }
  //
  //   // Add to checking in list to prevent multiple clicks
  //   this.checkingInTrainingIds.push(trainingId);
  //
  //   navigator.geolocation.getCurrentPosition(
  //     (pos) => {
  //       const attendance: TrainingAttendance = {
  //         trainingId: trainingId,
  //         staffId: this.userId!,
  //         checkInLatitude: pos.coords.latitude,
  //         checkInLongitude: pos.coords.longitude
  //       };
  //       console.log("current lat", pos.coords.latitude);
  //       console.log("current long", pos.coords.longitude);
  //
  //       this.trainingAttendanceService.checkin(attendance).subscribe({
  //         next: (response) => {
  //           // Remove from checking in list
  //           this.checkingInTrainingIds = this.checkingInTrainingIds.filter(id => id !== trainingId);
  //
  //           if (response.success) {
  //             this.checkedInTrainingIds.push(trainingId);
  //             this.modal.success({
  //               nzTitle: 'Attendance Marked Successfully',
  //               nzContent: response.message || 'Your attendance has been recorded.',
  //               nzCentered: true
  //             });
  //           } else {
  //             this.modal.error({
  //               nzTitle: 'Attendance Failed',
  //               nzContent: response.message || 'Unable to mark attendance. Please try again.',
  //               nzCentered: true
  //             });
  //           }
  //         },
  //         error: (error) => {
  //           // Remove from checking in list
  //           this.checkingInTrainingIds = this.checkingInTrainingIds.filter(id => id !== trainingId);
  //
  //           console.error('Check-in failed', error);
  //           this.modal.error({
  //             nzTitle: 'Attendance Failed',
  //             nzContent: 'Unable to mark attendance. Please try again.',
  //             nzCentered: true
  //           });
  //         }
  //       });
  //     },
  //     (err) => {
  //       // Remove from checking in list
  //       this.checkingInTrainingIds = this.checkingInTrainingIds.filter(id => id !== trainingId);
  //
  //       console.error('Geolocation error', err);
  //       this.modal.error({
  //         nzTitle: 'Location Access Required',
  //         nzContent: 'Please enable location access to mark attendance.',
  //         nzCentered: true
  //       });
  //     },
  //     {
  //       enableHighAccuracy: true,
  //       timeout: 10000,
  //       maximumAge: 0
  //     }
  //   );
  // }

  getUpcomingTrainingList(): void {
    const today = startOfDay(new Date());
    const now = new Date();
    const tomorrow = startOfDay(new Date(today.getTime() + 24 * 60 * 60 * 1000));

    const upcomingRegistered = this.registeredTrainingList.filter(t => {
      const start = startOfDay(new Date(t.startDate));
      const end = startOfDay(new Date(t.endDate));

      // Training that hasn’t ended yet (still ongoing or future)
      return isAfter(end, today) || isEqual(end, today);
    });

    const uniqueTrainings = upcomingRegistered.filter(
      (t, index, self) => index === self.findIndex(u => u.trainingId === t.trainingId)
    );

    // 📅 Trainings happening today (today between start & end inclusive)
    this.todayTrainingList = uniqueTrainings.filter(t => {
      const start = startOfDay(new Date(t.startDate));
      const end = startOfDay(new Date(t.endDate));
      return (
        (isEqual(today, start) || isAfter(today, start)) &&
        (isEqual(today, end) || isBefore(today, end))
      );
    });

    // 📅 Trainings happening tomorrow
    this.tomorrowTrainingList = uniqueTrainings.filter(t => {
      const start = startOfDay(new Date(t.startDate));
      const end = startOfDay(new Date(t.endDate));
      return (
        (isEqual(tomorrow, start) || isAfter(tomorrow, start)) &&
        (isEqual(tomorrow, end) || isBefore(tomorrow, end))
      );
    });

    // 📅 Trainings starting after tomorrow
    this.upcomingTrainingList = uniqueTrainings.filter(t => {
      const start = startOfDay(new Date(t.startDate));
      return isAfter(start, tomorrow);
    });

    // ✅ Trainings completed in the past and attended (checked-in)
    this.completedTrainingList = this.registeredTrainingList
      .filter(t => {
        const endDateStr = t.endDate;
        // If endTime provided, use it; otherwise consider end of the endDate day
        const endTimeStr = t.endTime && t.endTime.trim().length > 0 ? t.endTime : '23:59:59';
        const trainingEnd = new Date(`${endDateStr}T${endTimeStr}`);
        return trainingEnd.getTime() < now.getTime();
      })
      // .filter(t => this.checkedInTrainingIds.includes(t.trainingId))
      .filter((t, index, self) => index === self.findIndex(u => u.trainingId === t.trainingId))
      .sort((a, b) => new Date(b.endDate).getTime() - new Date(a.endDate).getTime());

    // Sort all lists
    const sortByStart = (a: any, b: any) =>
      new Date(a.startDate).getTime() - new Date(b.startDate).getTime();

    this.todayTrainingList.sort(sortByStart);
    this.tomorrowTrainingList.sort(sortByStart);
    this.upcomingTrainingList.sort(sortByStart);

    // Update display lists
    this.listOfDisplayTodayTraining = [...this.todayTrainingList];
    this.listOfDisplayTomorrowTraining = [...this.tomorrowTrainingList];
    this.listOfDisplayUpcomingTraining = [...this.upcomingTrainingList];
    this.listOfDisplayCompletedTraining = [...this.completedTrainingList];

    this.buildCalendarEvents();
  }

  private buildCalendarEvents(): void {
    this.calendarEventsByDate.clear();
    const allTrainings = [...this.todayTrainingList, ...this.tomorrowTrainingList, ...this.upcomingTrainingList];

    const uniqueTrainings = allTrainings.filter(
      (t, index, self) => index === self.findIndex(u => u.trainingId === t.trainingId)
    );

    for (const training of uniqueTrainings) {
      const start = new Date(training.startDate);
      const end = new Date(training.endDate ?? training.startDate);
      // iterate inclusive from start to end
      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const key = this.dateKey(d);
        const list = this.calendarEventsByDate.get(key) ?? [];
        list.push(training);
        this.calendarEventsByDate.set(key, list);
      }
    }
  }

  onCalendarSelect(date: Date): void {
    this.selectedDate = date;
    const key = this.dateKey(date);
    const trainings = this.calendarEventsByDate.get(key);
    const today = startOfDay(new Date());
    const tomorrow = startOfDay(new Date(today.getTime() + 24 * 60 * 60 * 1000));

    if (trainings && trainings.length > 0) {
      // Deduplicate by id while preserving order
      const seen = new Set<number>();
      const filteredTrainings = trainings.filter(t => {
        const id = t.trainingId!;
        if (seen.has(id)) return false;
        seen.add(id);
        return true;
      });

      // Update all display lists based on selected date
      this.listOfDisplayTodayTraining = filteredTrainings.filter(t => {
        const start = startOfDay(new Date(t.startDate));
        return isEqual(start, today);
      });

      this.listOfDisplayTomorrowTraining = filteredTrainings.filter(t => {
        const start = startOfDay(new Date(t.startDate));
        return isEqual(start, tomorrow);
      });

      this.listOfDisplayUpcomingTraining = filteredTrainings.filter(t => {
        const start = startOfDay(new Date(t.startDate));
        return isAfter(start, tomorrow);
      });
    } else {
      this.listOfDisplayTodayTraining = [...this.todayTrainingList];
      this.listOfDisplayTomorrowTraining = [...this.tomorrowTrainingList];
      this.listOfDisplayUpcomingTraining = [...this.upcomingTrainingList];
    }
  }

  dateCellEvents(date: Date): TrainingProgram[] {
    const key = this.dateKey(date);
    return this.calendarEventsByDate.get(key) ?? [];
  }

  private dateKey(date: Date): string {
    const y = date.getFullYear();
    const m = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  private toDateTime(dateStr: string, timeStr: string): Date {
    const date = new Date(dateStr);
    if (!timeStr) return date;

    const [hours, minutes] = timeStr.split(':').map(Number);
    date.setHours(hours, minutes, 0, 0);
    return date;
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.auth.hasRole(role));
  }

}

