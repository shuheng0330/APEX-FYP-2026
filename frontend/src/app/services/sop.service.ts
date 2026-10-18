import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  QuizReviewEvent, SopCourseSettings, SopDetail, SopDocument, SopModule, SopQuizQuestion, UploadedMedia
} from '../models/sop.model';

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

  /**
   * The courses you may see: your own, plus (for HR and the Super Admin) everyone else's read-only.
   * @param mine only your own courses, as on the upload page
   */
  /** `archived` lists the archived courses instead of the current ones (FR-06-14, FR-06-16). */
  list(mine = false, archived = false): Observable<SopDocument[]> {
    return this.http.get<SopDocument[]>(this.baseUrl, { params: { mine, archived }, withCredentials: true });
  }

  /** The course's own pass mark and estimated duration (FR-07-15); its owner only. */
  updateSettings(id: number, settings: SopCourseSettings): Observable<SopDocument> {
    return this.http.put<SopDocument>(`${this.baseUrl}/${id}/settings`, settings, { withCredentials: true });
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

  /** Archives a course staff have enrolled in; those still taking it are withdrawn and told (FR-06-14, FR-06-15). */
  archive(id: number): Observable<SopDocument> {
    return this.http.post<SopDocument>(`${this.baseUrl}/${id}/archive`, {}, { withCredentials: true });
  }

  /** Makes an archived course Live again (FR-06-16). */
  restore(id: number): Observable<SopDocument> {
    return this.http.post<SopDocument>(`${this.baseUrl}/${id}/restore`, {}, { withCredentials: true });
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

  /** Adds a trainer-written question; the quiz then needs approving again. */
  addQuestion(moduleId: number, dto: Partial<SopQuizQuestion>): Observable<SopQuizQuestion> {
    return this.http.post<SopQuizQuestion>(`${this.baseUrl}/module/${moduleId}/quiz`, dto, { withCredentials: true });
  }

  approveQuiz(moduleId: number): Observable<SopModule> {
    return this.http.post<SopModule>(`${this.baseUrl}/module/${moduleId}/quiz/approve`, {}, { withCredentials: true });
  }

  rejectQuiz(moduleId: number, reason: string): Observable<SopModule> {
    return this.http.post<SopModule>(`${this.baseUrl}/module/${moduleId}/quiz/reject`, { reason }, { withCredentials: true });
  }

  /** Who approved, rejected or edited a module's quiz, and each AI regeneration (FR-08-09). */
  quizHistory(moduleId: number): Observable<QuizReviewEvent[]> {
    return this.http.get<QuizReviewEvent[]>(`${this.baseUrl}/module/${moduleId}/quiz/history`, { withCredentials: true });
  }

  /** Uploads an image or video for a content block (FR-07-06). */
  uploadMedia(file: File): Observable<UploadedMedia> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<UploadedMedia>(`${this.baseUrl}/media`, form, { withCredentials: true });
  }
}
