import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { Competency } from '../models/competency.model';
import { CompetencyCompTag } from '../models/competency-comp-tag.model';
import { CompetencyCreation, CompetencyEdit } from '../models/competency.model';

@Injectable({
  providedIn: 'root',
})
export class CompetencyService {
  private baseUrl = `${environment.apiBaseUrl}/competency`;

  constructor(private http: HttpClient) { }

  getAllCompetencies(): Observable<Competency[]> {
    return this.http.get<Competency[]>(`${this.baseUrl}`, {
      withCredentials: true
    });
  }

  getCompetencyOverview(): Observable<CompetencyCompTag[]> {
    return this.http.get<CompetencyCompTag[]>(`${this.baseUrl}/overview`, { withCredentials: true });
  }

  createCompetency(competency: CompetencyCreation): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}`, competency, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  updateCompetency(competency: CompetencyEdit): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/edit-competency`, competency, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  deleteCompetency(competencyId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete`, {
      withCredentials: true,
      params: { competencyId },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  bulkDeleteCompetency(competencyIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      withCredentials: true,
      params: { competencyIds },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  export(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/export`, {
      withCredentials: true,
      responseType: 'blob',
      observe: 'response'
    });
  }

  import(file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<void>(`${this.baseUrl}/import`, formData, {
      withCredentials: true,
      headers: new HttpHeaders({
        'X-Show-Success': 'true'
      })
    });
  }
}
