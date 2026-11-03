import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  FeedbackRequest, LearningCourse, LearningCourseDetail, LearningHome, LearningMaterial, LearningQuestion, QuizResult
} from '../models/sop-learning.model';

/** Client for the staff Learning Portal (/api/sop-learning, FR-11). */
@Injectable({ providedIn: 'root' })
export class SopLearningService {
  private baseUrl = environment.apiBaseUrl + '/sop-learning';

  constructor(private http: HttpClient) {}

  home(): Observable<LearningHome> {
    return this.http.get<LearningHome>(`${this.baseUrl}/home`, { withCredentials: true });
  }

  /** Joins an Open course (FR-11-05). */
  enrol(sopDocumentId: number): Observable<LearningCourse> {
    return this.http.post<LearningCourse>(`${this.baseUrl}/courses/${sopDocumentId}/enrol`, {}, { withCredentials: true });
  }

  course(sopDocumentId: number): Observable<LearningCourseDetail> {
    return this.http.get<LearningCourseDetail>(`${this.baseUrl}/courses/${sopDocumentId}`, { withCredentials: true });
  }

  start(sopDocumentId: number): Observable<LearningCourseDetail> {
    return this.http.post<LearningCourseDetail>(`${this.baseUrl}/courses/${sopDocumentId}/start`, {}, { withCredentials: true });
  }

  feedback(sopDocumentId: number, request: FeedbackRequest): Observable<LearningCourseDetail> {
    return this.http.post<LearningCourseDetail>(`${this.baseUrl}/courses/${sopDocumentId}/feedback`, request,
      { withCredentials: true });
  }

  material(moduleId: number): Observable<LearningMaterial> {
    return this.http.get<LearningMaterial>(`${this.baseUrl}/modules/${moduleId}/material`, { withCredentials: true });
  }

  quiz(moduleId: number): Observable<LearningQuestion[]> {
    return this.http.get<LearningQuestion[]>(`${this.baseUrl}/modules/${moduleId}/quiz`, { withCredentials: true });
  }

  /** Submits answers keyed by question id; the server marks them. */
  submit(moduleId: number, answers: Record<number, string>): Observable<QuizResult> {
    return this.http.post<QuizResult>(`${this.baseUrl}/modules/${moduleId}/attempts`, { answers }, { withCredentials: true });
  }
}
