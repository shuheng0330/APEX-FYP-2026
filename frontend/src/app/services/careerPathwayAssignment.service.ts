import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { CareerPathwayAssignmentOverview, UpdateCareerPathwayAssignmentRequest } from '../models/careerPathway.model';

@Injectable({
  providedIn: 'root',
})
export class CareerPathwayAssignmentService {
  private baseUrl = environment.apiBaseUrl + '/career-pathway-assignment';

  constructor(private http: HttpClient) { }

  assign(careerPathway: UpdateCareerPathwayAssignmentRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}`, careerPathway, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  overview(): Observable<CareerPathwayAssignmentOverview[]> {
    return this.http.get<CareerPathwayAssignmentOverview[]>(`${this.baseUrl}/overview`, {
      withCredentials: true
    });
  }

  edit(careerPathway: UpdateCareerPathwayAssignmentRequest): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/edit`, careerPathway, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  delete(selectedCareerPathwayId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete`, {
      withCredentials: true,
      params: { selectedCareerPathwayId },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  deleteAll(selectedCareerPathwayIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      withCredentials: true,
      params: { selectedCareerPathwayIds },
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
