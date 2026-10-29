import { Component, DestroyRef, ElementRef, EventEmitter, Input, NgZone, OnInit, Output, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTimePickerModule } from 'ng-zorro-antd/time-picker';
import { environment } from '../../../../environments/environment';
import { Audience } from '../../../models/sop-assignment.model';
import {
  TrainingDelivery, TrainingSession, TrainingSessionKind, TrainingSessionRequest, TrainingTemplate, TrainingTrainer,
  TrainingType
} from '../../../models/training-session.model';
import { AuthService } from '../../../services/auth.service';
import { MapsLoaderService } from '../../../services/maps-loader.service';
import { SopAssignmentService } from '../../../services/sop-assignment.service';
import { TrainingSessionService } from '../../../services/training-session.service';
import { formatDuration } from '../../sop/sop.util';
import {
  DELIVERY_LABELS, KIND_LABELS, MAX_CAPACITY, TRAINING_TYPE_LABELS, TRAINING_TYPE_NOTE, needsTrainingType, parseDay,
  startOfDay
} from '../training.util';

/**
 * Schedules a session, or changes one (FR-12-01 to FR-12-04, FR-12-12, FR-12-13, FR-12-15, FR-15-05, FR-17-01,
 * FR-17-05). Once a session has started, only its title, description, trainer and training type can change, and the
 * training type only until attendance is recorded; the server applies the same rules. The form can start from a saved
 * template, and save its set-up as one (FR-12-08, FR-12-09).
 */
@Component({
  selector: 'app-session-form',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    NzAlertModule, NzButtonModule, NzDatePickerModule, NzIconModule, NzInputModule, NzInputNumberModule, NzRadioModule,
    NzSelectModule, NzSwitchModule, NzTimePickerModule
  ],
  templateUrl: './session-form.component.html',
  styleUrls: ['./session-form.component.scss']
})
export class SessionFormComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly audienceTrigger = new Subject<void>();

  /** The session being changed, or null to schedule a new one. */
  @Input() session: TrainingSession | null = null;
  /** For a new session: the day picked on the calendar. */
  @Input() defaultDate: Date | null = null;
  /** For a new session: the template to fill the form from, leaving the date to choose. */
  @Input() template: TrainingTemplate | null = null;
  @Output() saved = new EventEmitter<TrainingSession>();
  @Output() cancelled = new EventEmitter<void>();

  readonly kinds = Object.entries(KIND_LABELS) as [TrainingSessionKind, string][];
  readonly deliveries = Object.entries(DELIVERY_LABELS) as [TrainingDelivery, string][];
  readonly types = Object.entries(TRAINING_TYPE_LABELS) as [TrainingType, string][];
  readonly typeNote = TRAINING_TYPE_NOTE;
  readonly maxCapacity = MAX_CAPACITY;
  readonly formatDuration = formatDuration;

  title = '';
  description = '';
  kind: TrainingSessionKind = 'TRAINING';
  delivery: TrainingDelivery = 'PHYSICAL';
  trainingType: TrainingType | null = null;
  date: Date | null = null;
  start: Date = this.at(9, 0);
  end: Date = this.at(10, 0);
  venue = '';
  locationName: string | null = null;
  latitude: number | null = null;
  longitude: number | null = null;
  capacity: number | null = 20;
  mandatory = false;
  trainerMode: 'STAFF' | 'EXTERNAL' = 'STAFF';
  trainerId: string | null = null;
  trainerName = '';
  departmentIds: number[] = [];
  roleIds: number[] = [];
  staffIds: string[] = [];
  selfRegistration = true;
  registrationForm = false;
  registrationCutoff: Date | null = null;
  waitlistEnabled = true;
  /** Also save the set-up as a reusable template (FR-12-08). */
  saveAsTemplate = false;
  templateName = '';

  trainers: TrainingTrainer[] = [];
  audience: Audience = { departments: [], roles: [], staff: [] };
  audienceSize: number | null = null;
  capacityWarning: string | null = null;
  saving = false;
  /** Place search on the venue box; only with a Google Maps key in environment.ts. */
  mapsReady = false;

  /** The venue text a map pin was picked for: typing over it drops the pin. */
  private pinnedVenue: string | null = null;
  private venueEl?: HTMLInputElement;
  private readonly withPlaces = new WeakSet<HTMLInputElement>();

  @ViewChild('venueInput') set venueInputRef(ref: ElementRef<HTMLInputElement> | undefined) {
    this.venueEl = ref?.nativeElement;
    this.attachPlaces();
  }

  constructor(private sessions: TrainingSessionService, private assignments: SopAssignmentService,
              private auth: AuthService, private maps: MapsLoaderService, private message: NzMessageService,
              private zone: NgZone) {}

  ngOnInit(): void {
    if (this.session) {
      this.fill(this.session);
    } else {
      if (this.template) this.fillFrom(this.template.setup);
      this.date = this.defaultDate;
    }
    this.audienceTrigger.pipe(
      debounceTime(300),
      switchMap(() => this.sessions.checkAudience(this.request()).pipe(catchError(() => of(null)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(check => {
      this.audienceSize = check?.audienceSize ?? null;
      this.capacityWarning = check?.warning ?? null;
    });
    this.sessions.trainers().subscribe({
      next: list => {
        this.trainers = this.withCurrentTrainer(list);
        // Whoever schedules a session usually runs it; a template names its own trainer, or asks for one.
        const me = this.auth.userId;
        if (!this.session && !this.template && !this.trainerId && me && list.some(t => t.id === me)) {
          this.trainerId = me;
          this.checkAudience();
        }
      }
    });
    this.assignments.audience().subscribe({ next: a => (this.audience = a) });
    this.checkAudience();
    if (environment.googleMaps.apiKey) {
      this.maps.load().then(() => this.zone.run(() => {
        this.mapsReady = typeof google !== 'undefined' && !!google.maps?.places;
        this.attachPlaces();
      })).catch(() => (this.mapsReady = false));
    }
  }

  get editing(): boolean {
    return !!this.session;
  }

  /** The session has started: only its title, description, trainer and training type can change. */
  get started(): boolean {
    return !!this.session && this.session.timing !== 'UPCOMING';
  }

  /** Attendance has been recorded, so the training type is fixed (FR-17-01). */
  get typeLocked(): boolean {
    return !!this.session?.attendanceRecorded;
  }

  get sharing(): boolean {
    return this.kind === 'STAFF_SHARING';
  }

  get showsTrainingType(): boolean {
    return needsTrainingType(this.kind, this.delivery);
  }

  get inPerson(): boolean {
    return this.delivery !== 'ONLINE';
  }

  get hasAudience(): boolean {
    return this.departmentIds.length > 0 || this.roleIds.length > 0 || this.staffIds.length > 0;
  }

  get durationMinutes(): number {
    return Math.max(0, this.minutes(this.end) - this.minutes(this.start));
  }

  /** The first thing to put right before saving; the server checks the same and more. */
  get problem(): string | null {
    const title = this.title.trim();
    if (!title) return 'Enter a title.';
    if (this.showsTrainingType && !this.trainingType) return 'Choose the training type.';
    if (!this.date || !this.start || !this.end) return 'Enter the date and the start and end times.';
    if (this.durationMinutes <= 0) return 'The session must end after it starts, on the same day.';
    if (!this.started && this.startsAt() <= new Date()) return "A session can't start in the past.";
    if (this.inPerson && !this.venue.trim()) return 'Enter the venue.';
    if (!this.capacity || this.capacity < 1 || this.capacity > MAX_CAPACITY) return 'Seats must be between 1 and 1,000.';
    if (this.trainerMode === 'STAFF' && !this.trainerId) {
      return this.sharing ? 'Choose who presents the session.' : 'Choose who runs the session.';
    }
    if (this.trainerMode === 'EXTERNAL' && !this.trainerName.trim()) return "Enter the external trainer's name.";
    if (this.registrationCutoff && startOfDay(this.registrationCutoff) > startOfDay(this.date)) {
      return 'The registration cut-off must be on or before the session date.';
    }
    return null;
  }

  disablePast = (d: Date): boolean => startOfDay(d) < startOfDay(new Date());

  disableCutoff = (d: Date): boolean =>
    this.disablePast(d) || (!!this.date && startOfDay(d) > startOfDay(this.date));

  trainerLabel(t: TrainingTrainer): string {
    return t.name + (t.roleName ? ' · ' + t.roleName : '');
  }

  onKindChange(): void {
    if (this.sharing) {
      this.trainerMode = 'STAFF'; // a sharing session is presented by staff with Trainer access (FR-15-05)
    }
    this.checkAudience();
  }

  onVenueChange(): void {
    if (this.pinnedVenue !== null && this.venue !== this.pinnedVenue) {
      this.locationName = this.latitude = this.longitude = this.pinnedVenue = null;
    }
  }

  checkAudience(): void {
    this.audienceTrigger.next();
  }

  save(): void {
    if (this.problem || this.saving) return;
    this.saving = true;
    const request = this.request();
    const call = this.session ? this.sessions.update(this.session.id, request) : this.sessions.schedule(request);
    call.subscribe({
      next: saved => {
        this.saving = false;
        const template = request.templateName ? ` Its set-up is saved as the template "${request.templateName}".` : '';
        this.message.success((this.session ? 'Session saved.' : `Session scheduled for ${formatDate(saved.date, 'EEE d MMM', 'en-US')}.`)
          + template);
        this.saved.emit(saved);
      },
      error: err => {
        this.saving = false;
        this.message.error(err?.error?.message || 'Could not save the session.');
      }
    });
  }

  private request(): TrainingSessionRequest {
    return {
      title: this.title.trim(),
      description: this.description.trim() || null,
      kind: this.kind,
      delivery: this.delivery,
      trainingType: this.showsTrainingType ? this.trainingType : null,
      date: this.date ? formatDate(this.date, 'yyyy-MM-dd', 'en-US') : null,
      startTime: formatDate(this.start, 'HH:mm', 'en-US'),
      endTime: formatDate(this.end, 'HH:mm', 'en-US'),
      venue: this.venue.trim() || null,
      locationName: this.inPerson ? this.locationName : null,
      latitude: this.inPerson ? this.latitude : null,
      longitude: this.inPerson ? this.longitude : null,
      capacity: this.capacity,
      mandatory: this.mandatory,
      trainerId: this.trainerMode === 'STAFF' ? this.trainerId : null,
      trainerName: this.trainerMode === 'EXTERNAL' ? this.trainerName.trim() : null,
      departmentIds: this.departmentIds,
      roleIds: this.roleIds,
      staffIds: this.staffIds,
      selfRegistration: this.selfRegistration,
      registrationForm: this.registrationForm,
      registrationCutoff: this.registrationCutoff ? formatDate(this.registrationCutoff, 'yyyy-MM-dd', 'en-US') : null,
      waitlistEnabled: this.waitlistEnabled,
      templateName: this.saveAsTemplate ? (this.templateName.trim() || this.title.trim()) : null,
      templateId: this.session ? null : this.template?.id ?? null
    };
  }

  /** A saved template's set-up; the date and the cut-off are the trainer's to choose (FR-12-09). */
  private fillFrom(r: TrainingSessionRequest): void {
    this.title = r.title ?? '';
    this.description = r.description ?? '';
    this.kind = r.kind ?? 'TRAINING';
    this.delivery = r.delivery ?? 'PHYSICAL';
    this.trainingType = r.trainingType ?? null;
    if (r.startTime) this.start = this.timeOf(r.startTime);
    if (r.endTime) this.end = this.timeOf(r.endTime);
    this.venue = r.venue ?? '';
    this.locationName = r.locationName ?? null;
    this.latitude = r.latitude ?? null;
    this.longitude = r.longitude ?? null;
    this.pinnedVenue = this.latitude != null ? this.venue : null;
    this.capacity = r.capacity ?? 20;
    this.mandatory = !!r.mandatory;
    this.trainerMode = !r.trainerId && r.trainerName ? 'EXTERNAL' : 'STAFF';
    this.trainerId = r.trainerId ?? null;
    this.trainerName = !r.trainerId ? (r.trainerName ?? '') : '';
    this.departmentIds = [...(r.departmentIds ?? [])];
    this.roleIds = [...(r.roleIds ?? [])];
    this.staffIds = [...(r.staffIds ?? [])];
    this.selfRegistration = r.selfRegistration ?? true;
    this.registrationForm = !!r.registrationForm;
    this.waitlistEnabled = r.waitlistEnabled ?? true;
  }

  private fill(s: TrainingSession): void {
    this.title = s.title;
    this.description = s.description ?? '';
    this.kind = s.kind;
    this.delivery = s.delivery;
    this.trainingType = s.trainingType ?? null;
    this.date = parseDay(s.date);
    this.start = this.timeOf(s.startTime);
    this.end = this.timeOf(s.endTime);
    this.venue = s.venue ?? '';
    this.locationName = s.locationName ?? null;
    this.latitude = s.latitude ?? null;
    this.longitude = s.longitude ?? null;
    this.pinnedVenue = this.latitude != null ? this.venue : null;
    this.capacity = s.capacity;
    this.mandatory = s.mandatory;
    this.trainerMode = s.externalTrainer ? 'EXTERNAL' : 'STAFF';
    this.trainerId = s.trainerId ?? null;
    this.trainerName = s.externalTrainer ? (s.trainerName ?? '') : '';
    this.departmentIds = [...s.departmentIds];
    this.roleIds = [...s.roleIds];
    this.staffIds = [...s.staffIds];
    this.selfRegistration = s.selfRegistration;
    this.registrationForm = s.registrationForm;
    this.registrationCutoff = s.registrationCutoff ? parseDay(s.registrationCutoff) : null;
    this.waitlistEnabled = s.waitlistEnabled;
  }

  /** A session's trainer who has since lost Trainer access stays on it, so keep them selectable. */
  private withCurrentTrainer(list: TrainingTrainer[]): TrainingTrainer[] {
    const s = this.session;
    if (!s?.trainerId || list.some(t => t.id === s.trainerId)) return list;
    return [{ id: s.trainerId, name: `${s.trainerName || 'Unknown user'} (no Trainer access now)` }, ...list];
  }

  /** Suggests places as the venue is typed and pins the one picked, when Google Maps is available. */
  private attachPlaces(): void {
    const input = this.venueEl;
    if (!this.mapsReady || !input || this.withPlaces.has(input)) return;
    this.withPlaces.add(input);
    const autocomplete = new google.maps.places.Autocomplete(input, { fields: ['geometry', 'formatted_address', 'name'] });
    autocomplete.addListener('place_changed', () => this.zone.run(() => {
      const place = autocomplete.getPlace();
      const location = place.geometry?.location;
      if (!location) return;
      const address = place.formatted_address || '';
      this.venue = place.name && !address.startsWith(place.name) ? [place.name, address].filter(Boolean).join(', ') : address;
      this.locationName = address || place.name || null;
      this.latitude = location.lat();
      this.longitude = location.lng();
      this.pinnedVenue = this.venue;
    }));
  }

  private startsAt(): Date {
    const d = new Date(this.date!);
    d.setHours(this.start.getHours(), this.start.getMinutes(), 0, 0);
    return d;
  }

  private minutes(time: Date | null): number {
    return time ? time.getHours() * 60 + time.getMinutes() : 0;
  }

  private timeOf(hms: string): Date {
    const [h, m] = hms.split(':').map(Number);
    return this.at(h, m);
  }

  private at(hours: number, minutes: number): Date {
    const d = new Date();
    d.setHours(hours, minutes, 0, 0);
    return d;
  }
}
