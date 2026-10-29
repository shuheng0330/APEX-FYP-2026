import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  TrainingAttendanceEntry, TrainingAttendanceSheet, TrainingAudienceCheck, TrainingBatchResult, TrainingPerson,
  TrainingRegistrationForm, TrainingRegistrationRequest, TrainingSession, TrainingSessionReport, TrainingSessionRequest,
  TrainingSharingBoard, TrainingTemplate, TrainingTrainer
} from '../models/training-session.model';

/** Client for the Training Calendar's sessions (/api/trainings, FR-12). */
@Injectable({ providedIn: 'root' })
export class TrainingSessionService {
  private baseUrl = environment.apiBaseUrl + '/trainings';

  constructor(private http: HttpClient) {}

  /** The sessions between two dates (yyyy-MM-dd) in the order they run; archived ones only when asked for. */
  calendar(from: string, to: string, archived = false): Observable<TrainingSession[]> {
    const params = new HttpParams().set('from', from).set('to', to).set('archived', archived);
    return this.http.get<TrainingSession[]>(this.baseUrl, { params, withCredentials: true });
  }

  /** The signed-in user's upcoming sessions, for the My Learning home (FR-13-05). */
  mine(): Observable<TrainingSession[]> {
    return this.http.get<TrainingSession[]>(`${this.baseUrl}/mine`, { withCredentials: true });
  }

  get(id: number): Observable<TrainingSession> {
    return this.http.get<TrainingSession>(`${this.baseUrl}/${id}`, { withCredentials: true });
  }

  /** Who can run or present a session. */
  trainers(): Observable<TrainingTrainer[]> {
    return this.http.get<TrainingTrainer[]>(`${this.baseUrl}/trainers`, { withCredentials: true });
  }

  checkAudience(request: TrainingSessionRequest): Observable<TrainingAudienceCheck> {
    return this.http.post<TrainingAudienceCheck>(`${this.baseUrl}/audience-check`, request, { withCredentials: true });
  }

  schedule(request: TrainingSessionRequest): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(this.baseUrl, request, { withCredentials: true });
  }

  update(id: number, request: TrainingSessionRequest): Observable<TrainingSession> {
    return this.http.put<TrainingSession>(`${this.baseUrl}/${id}`, request, { withCredentials: true });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`, { withCredentials: true });
  }

  cancel(id: number, reason: string): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/cancel`, { reason }, { withCredentials: true });
  }

  archive(id: number): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/archive`, {}, { withCredentials: true });
  }

  restore(id: number): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/restore`, {}, { withCredentials: true });
  }

  // --- registering: always as the signed-in user (FR-13) --------------------------------

  registrationForm(id: number): Observable<TrainingRegistrationForm> {
    return this.http.get<TrainingRegistrationForm>(`${this.baseUrl}/${id}/registration-form`, { withCredentials: true });
  }

  register(id: number, request: TrainingRegistrationRequest = {}): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/register`, request, { withCredentials: true });
  }

  cancelRegistration(id: number): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/cancel-registration`, {}, { withCredentials: true });
  }

  acceptOffer(id: number): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/accept-offer`, {}, { withCredentials: true });
  }

  declineOffer(id: number): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/decline-offer`, {}, { withCredentials: true });
  }

  declineInvitation(id: number, reason: string): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/decline-invitation`, { reason }, { withCredentials: true });
  }

  // --- who is on a session: the trainer and HR ---------------------------------------------

  people(id: number): Observable<TrainingPerson[]> {
    return this.http.get<TrainingPerson[]>(`${this.baseUrl}/${id}/people`, { withCredentials: true });
  }

  addPerson(id: number, staffId: string): Observable<TrainingPerson[]> {
    return this.http.post<TrainingPerson[]>(`${this.baseUrl}/${id}/people`, { staffId }, { withCredentials: true });
  }

  removePerson(id: number, registrationId: number): Observable<TrainingPerson[]> {
    return this.http.post<TrainingPerson[]>(`${this.baseUrl}/${id}/people/${registrationId}/remove`, {}, { withCredentials: true });
  }

  offerSeat(id: number, registrationId: number): Observable<TrainingPerson[]> {
    return this.http.post<TrainingPerson[]>(`${this.baseUrl}/${id}/people/${registrationId}/offer`, {}, { withCredentials: true });
  }

  // --- attendance: the trainer and HR (FR-14) ------------------------------------------------

  /** Sessions whose attendance the signed-in user has still to submit, oldest first (FR-14-10). */
  awaitingAttendance(): Observable<TrainingSession[]> {
    return this.http.get<TrainingSession[]>(`${this.baseUrl}/awaiting-attendance`, { withCredentials: true });
  }

  attendance(id: number): Observable<TrainingAttendanceSheet> {
    return this.http.get<TrainingAttendanceSheet>(`${this.baseUrl}/${id}/attendance`, { withCredentials: true });
  }

  /** The reason only when HR changes it after submission or after the trainer's 7 days. */
  recordAttendance(id: number, entries: TrainingAttendanceEntry[], reason?: string): Observable<TrainingAttendanceSheet> {
    return this.http.put<TrainingAttendanceSheet>(`${this.baseUrl}/${id}/attendance`, { entries, reason }, { withCredentials: true });
  }

  addWalkIn(id: number, staffId: string, reason?: string): Observable<TrainingAttendanceSheet> {
    return this.http.post<TrainingAttendanceSheet>(`${this.baseUrl}/${id}/attendance/walk-ins`, { staffId, reason },
      { withCredentials: true });
  }

  submitAttendance(id: number, reason?: string): Observable<TrainingAttendanceSheet> {
    return this.http.post<TrainingAttendanceSheet>(`${this.baseUrl}/${id}/attendance/submit`, { reason }, { withCredentials: true });
  }

  addEvidence(id: number, file: File): Observable<TrainingAttendanceSheet> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<TrainingAttendanceSheet>(`${this.baseUrl}/${id}/attendance/evidence`, form, { withCredentials: true });
  }

  removeEvidence(id: number, evidenceId: number): Observable<TrainingAttendanceSheet> {
    return this.http.delete<TrainingAttendanceSheet>(`${this.baseUrl}/${id}/attendance/evidence/${evidenceId}`,
      { withCredentials: true });
  }

  /** The file itself, fetched with the sign-in so it can be opened in a new tab. */
  evidenceFile(id: number, evidenceId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/attendance/evidence/${evidenceId}`, { responseType: 'blob', withCredentials: true });
  }

  /** The HRDC attendance template as an Excel file, listing everyone on the session (FR-14-05). */
  hrdcTemplate(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/attendance/hrdc-template`, { responseType: 'blob', withCredentials: true });
  }

  /** A filled-in template, or a CSV with its columns, applied in one go (FR-14-06). */
  uploadAttendance(id: number, file: File, reason?: string): Observable<TrainingBatchResult> {
    const form = new FormData();
    form.append('file', file);
    if (reason) form.append('reason', reason);
    return this.http.post<TrainingBatchResult>(`${this.baseUrl}/${id}/attendance/batch`, form, { withCredentials: true });
  }

  // --- staff sharing (FR-15) ------------------------------------------------------------------

  /** The Staff Sharing page for a year; this year when none is given. */
  sharingBoard(year?: number): Observable<TrainingSharingBoard> {
    const params = year ? new HttpParams().set('year', year) : undefined;
    return this.http.get<TrainingSharingBoard>(`${this.baseUrl}/sharing`, { params, withCredentials: true });
  }

  /** FR-15-06: asks the Super Admin for Trainer access, after attending this Product Training session. */
  requestTrainerAccess(id: number): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(`${this.baseUrl}/${id}/request-trainer-access`, {}, { withCredentials: true });
  }

  // --- reports and templates (FR-12-07 to FR-12-10) -----------------------------------------

  report(id: number): Observable<TrainingSessionReport> {
    return this.http.get<TrainingSessionReport>(`${this.baseUrl}/${id}/report`, { withCredentials: true });
  }

  reportFile(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/report/file`, { responseType: 'blob', withCredentials: true });
  }

  /** The signed-in user's templates; every template for HR. */
  templates(): Observable<TrainingTemplate[]> {
    return this.http.get<TrainingTemplate[]>(`${this.baseUrl}/templates`, { withCredentials: true });
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/templates/${id}`, { withCredentials: true });
  }
}
