import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CourseCategory, CourseCategoryChip, CourseCategoryOptions } from '../models/course-category.model';

/** Client for course categories (/api/course-categories, FR-19). */
@Injectable({ providedIn: 'root' })
export class CourseCategoryService {
  private baseUrl = environment.apiBaseUrl + '/course-categories';

  constructor(private http: HttpClient) {}

  /** Every category, retired ones too, General first. */
  list(): Observable<CourseCategory[]> {
    return this.http.get<CourseCategory[]>(this.baseUrl, { withCredentials: true });
  }

  options(): Observable<CourseCategoryOptions> {
    return this.http.get<CourseCategoryOptions>(`${this.baseUrl}/options`, { withCredentials: true });
  }

  create(name: string, color: string, icon: string): Observable<CourseCategory> {
    return this.http.post<CourseCategory>(this.baseUrl, { name, color, icon }, { withCredentials: true });
  }

  update(id: number, name: string, color: string, icon: string): Observable<CourseCategory> {
    return this.http.put<CourseCategory>(`${this.baseUrl}/${id}`, { name, color, icon }, { withCredentials: true });
  }

  retire(id: number): Observable<CourseCategory> {
    return this.http.post<CourseCategory>(`${this.baseUrl}/${id}/retire`, {}, { withCredentials: true });
  }

  restore(id: number): Observable<CourseCategory> {
    return this.http.post<CourseCategory>(`${this.baseUrl}/${id}/restore`, {}, { withCredentials: true });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`, { withCredentials: true });
  }

  /** A course's categories, at least one, with the primary one (FR-19-05); by the course's owner. */
  setCourse(documentId: number, categoryIds: number[], primaryId: number | null): Observable<CourseCategoryChip[]> {
    return this.http.put<CourseCategoryChip[]>(`${this.baseUrl}/courses/${documentId}`, { categoryIds, primaryId },
      { withCredentials: true });
  }
}
