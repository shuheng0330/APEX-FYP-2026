import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AssignableCourse, Assignment, AssignmentPreview, AssignmentRequest, AssignmentResult, Audience, SopVisibility
} from '../models/sop-assignment.model';

/** Client for Assign Training (/api/sop-assignments, FR-09). */
@Injectable({ providedIn: 'root' })
export class SopAssignmentService {
  private baseUrl = environment.apiBaseUrl + '/sop-assignments';

  constructor(private http: HttpClient) {}

  courses(): Observable<AssignableCourse[]> {
    return this.http.get<AssignableCourse[]>(`${this.baseUrl}/courses`, { withCredentials: true });
  }

  audience(): Observable<Audience> {
    return this.http.get<Audience>(`${this.baseUrl}/audience`, { withCredentials: true });
  }

  preview(request: AssignmentRequest): Observable<AssignmentPreview> {
    return this.http.post<AssignmentPreview>(`${this.baseUrl}/preview`, request, { withCredentials: true });
  }

  assign(request: AssignmentRequest): Observable<AssignmentResult> {
    return this.http.post<AssignmentResult>(this.baseUrl, request, { withCredentials: true });
  }

  /** Assignment history of one course, or of every course the user may assign. */
  history(sopDocumentId?: number): Observable<Assignment[]> {
    const params = sopDocumentId ? new HttpParams().set('sopDocumentId', sopDocumentId) : undefined;
    return this.http.get<Assignment[]>(`${this.baseUrl}/history`, { params, withCredentials: true });
  }

  setVisibility(sopDocumentId: number, visibility: SopVisibility): Observable<AssignableCourse> {
    return this.http.put<AssignableCourse>(`${this.baseUrl}/courses/${sopDocumentId}/visibility`, { visibility },
      { withCredentials: true });
  }
}
