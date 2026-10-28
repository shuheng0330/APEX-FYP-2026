import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  TrainingAudienceCheck, TrainingSession, TrainingSessionRequest, TrainingTrainer
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
}
