import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  TrainerAccessRequest, TrainerAccessStaff, TrainerOwnedCourse, TrainingAuditEntry
} from '../models/trainer-access.model';

/** Client for Manage Trainer Access (/api/trainer-access, FR-20-01, FR-20-07). Super Admin only. */
@Injectable({ providedIn: 'root' })
export class TrainerAccessService {
  private baseUrl = environment.apiBaseUrl + '/trainer-access';
  /** Grant and revoke errors are shown inside their dialog rather than as a pop-up. */
  private quietErrors = new HttpHeaders({ 'X-Skip-Error-Handler': 'true' });

  constructor(private http: HttpClient) {}

  list(): Observable<TrainerAccessStaff[]> {
    return this.http.get<TrainerAccessStaff[]>(this.baseUrl, { withCredentials: true });
  }

  grant(staffId: string, request: TrainerAccessRequest): Observable<TrainerAccessStaff> {
    return this.http.post<TrainerAccessStaff>(`${this.baseUrl}/${staffId}/grant`, request,
      { headers: this.quietErrors, withCredentials: true });
  }

  revoke(staffId: string, reason: string): Observable<TrainerAccessStaff> {
    return this.http.post<TrainerAccessStaff>(`${this.baseUrl}/${staffId}/revoke`, { reason },
      { headers: this.quietErrors, withCredentials: true });
  }

  history(staffId: string): Observable<TrainingAuditEntry[]> {
    return this.http.get<TrainingAuditEntry[]>(`${this.baseUrl}/${staffId}/history`, { withCredentials: true });
  }

  ownedCourses(staffId: string): Observable<TrainerOwnedCourse[]> {
    return this.http.get<TrainerOwnedCourse[]>(`${this.baseUrl}/${staffId}/courses`, { withCredentials: true });
  }
}
