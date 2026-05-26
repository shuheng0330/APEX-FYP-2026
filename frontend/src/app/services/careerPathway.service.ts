import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { CareerPathway, CareerPathwayOverview, CreateCareerPathwayRequest } from '../models/careerPathway.model';

@Injectable({
  providedIn: 'root',
})
export class CareerPathwayService {
  private baseUrl = environment.apiBaseUrl + '/career-pathway';

  constructor(private http: HttpClient) { }

  getAll(): Observable<CareerPathway[]> {
    return this.http.get<CareerPathway[]>(`${this.baseUrl}`, {
      withCredentials: true
    });
  }

  create(careerPathway: CreateCareerPathwayRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}`, careerPathway, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  edit(careerPathway: CreateCareerPathwayRequest): Observable<void> {
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

  overview(): Observable<CareerPathwayOverview[]> {
    return this.http.get<CareerPathwayOverview[]>(`${this.baseUrl}/overview`, {
      withCredentials: true
    });
  }

  my(staffId: string): Observable<CareerPathwayOverview> {
    return this.http.get<CareerPathwayOverview>(`${this.baseUrl}/my`, {
      params: { staffId },
      withCredentials: true
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
