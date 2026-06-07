import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SopDetail, SopDocument, SopModule, SopQuizQuestion } from '../models/sop.model';

/**
 * Client for the SOP training endpoints (/api/sop): upload + AI generation
 * (FR-08) and the material/quiz review gates (FR-09 / FR-10).
 */
@Injectable({ providedIn: 'root' })
export class SopService {
  private baseUrl = environment.apiBaseUrl + '/sop';

  constructor(private http: HttpClient) {}

  // --- Module 1: upload + generation ---
  upload(form: FormData): Observable<SopDocument> {
    return this.http.post<SopDocument>(`${this.baseUrl}/upload`, form, { withCredentials: true });
  }

  list(): Observable<SopDocument[]> {
    return this.http.get<SopDocument[]>(this.baseUrl, { withCredentials: true });
  }

  detail(id: number): Observable<SopDetail> {
    return this.http.get<SopDetail>(`${this.baseUrl}/${id}`, { withCredentials: true });
  }

  status(id: number): Observable<SopDocument> {
    return this.http.get<SopDocument>(`${this.baseUrl}/${id}/status`, { withCredentials: true });
  }

  regenerate(id: number): Observable<SopDocument> {
    return this.http.post<SopDocument>(`${this.baseUrl}/${id}/generate`, {}, { withCredentials: true });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`, { withCredentials: true });
  }

  // --- Module 2: material review (FR-09) ---
  updateModule(moduleId: number, title: string, content: string): Observable<SopModule> {
    return this.http.put<SopModule>(`${this.baseUrl}/module/${moduleId}`, { title, content }, { withCredentials: true });
  }

  approveModule(moduleId: number): Observable<SopModule> {
    return this.http.post<SopModule>(`${this.baseUrl}/module/${moduleId}/approve`, {}, { withCredentials: true });
  }

  rejectModule(moduleId: number, reason: string): Observable<SopModule> {
    return this.http.post<SopModule>(`${this.baseUrl}/module/${moduleId}/reject`, { reason }, { withCredentials: true });
  }

  // --- Module 2: quiz review (FR-10) ---
  updateQuestion(questionId: number, dto: Partial<SopQuizQuestion>): Observable<SopQuizQuestion> {
    return this.http.put<SopQuizQuestion>(`${this.baseUrl}/quiz/${questionId}`, dto, { withCredentials: true });
  }

  approveQuiz(moduleId: number): Observable<SopModule> {
    return this.http.post<SopModule>(`${this.baseUrl}/module/${moduleId}/quiz/approve`, {}, { withCredentials: true });
  }

  rejectQuiz(moduleId: number, reason: string): Observable<SopModule> {
    return this.http.post<SopModule>(`${this.baseUrl}/module/${moduleId}/quiz/reject`, { reason }, { withCredentials: true });
  }
}
