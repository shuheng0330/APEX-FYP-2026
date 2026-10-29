import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  CourseOwner, CourseTransferRequest, TrainerAccessRequest, TrainerAccessStaff, TrainerOwnedCourse, TrainingAuditEntry
} from '../models/trainer-access.model';

/** Client for Manage Trainer Access (/api/trainer-access, FR-20-01, FR-20-06, FR-20-07). Super Admin only. */
@Injectable({ providedIn: 'root' })
export class TrainerAccessService {
  private baseUrl = environment.apiBaseUrl + '/trainer-access';
  /** Grant, revoke and transfer errors are shown inside their dialog rather than as a pop-up. */
  private quietErrors = new HttpHeaders({ 'X-Skip-Error-Handler': 'true' });

  constructor(private http: HttpClient) {}

  list(): Observable<TrainerAccessStaff[]> {
    return this.http.get<TrainerAccessStaff[]>(this.baseUrl, { withCredentials: true });
  }

  /** Every department in the org chart, for the filter (FR-20-02). */
  departments(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/departments`, { withCredentials: true });
  }

  grant(staffId: string, request: TrainerAccessRequest): Observable<TrainerAccessStaff> {
    return this.http.post<TrainerAccessStaff>(`${this.baseUrl}/${staffId}/grant`, request,
      { headers: this.quietErrors, withCredentials: true });
  }

  /** `transferToId` names who takes over the person's courses when they would lose Trainer access (FR-20-07). */
  revoke(staffId: string, reason: string, transferToId: string | null = null): Observable<TrainerAccessStaff> {
    const request: TrainerAccessRequest = { reason, transferToId };
    return this.http.post<TrainerAccessStaff>(`${this.baseUrl}/${staffId}/revoke`, request,
      { headers: this.quietErrors, withCredentials: true });
  }

  history(staffId: string): Observable<TrainingAuditEntry[]> {
    return this.http.get<TrainingAuditEntry[]>(`${this.baseUrl}/${staffId}/history`, { withCredentials: true });
  }

  ownedCourses(staffId: string): Observable<TrainerOwnedCourse[]> {
    return this.http.get<TrainerOwnedCourse[]>(`${this.baseUrl}/${staffId}/courses`, { withCredentials: true });
  }

  /** Every course with its owner, and whether it needs a new one (FR-20-06). */
  courseOwners(): Observable<CourseOwner[]> {
    return this.http.get<CourseOwner[]>(`${this.baseUrl}/courses`, { withCredentials: true });
  }

  transferCourse(courseId: number, request: CourseTransferRequest): Observable<CourseOwner> {
    return this.http.post<CourseOwner>(`${this.baseUrl}/courses/${courseId}/transfer`, request,
      { headers: this.quietErrors, withCredentials: true });
  }

  courseHistory(courseId: number): Observable<TrainingAuditEntry[]> {
    return this.http.get<TrainingAuditEntry[]>(`${this.baseUrl}/courses/${courseId}/history`, { withCredentials: true });
  }
}
