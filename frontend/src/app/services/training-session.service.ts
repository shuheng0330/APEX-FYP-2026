import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  TrainingAudienceCheck, TrainingPerson, TrainingRegistrationForm, TrainingRegistrationRequest, TrainingSession,
  TrainingSessionRequest, TrainingTrainer
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
}
