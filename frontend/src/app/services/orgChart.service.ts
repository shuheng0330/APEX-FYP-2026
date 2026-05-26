import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { OrgChart, OrgChartGraphDto } from '../models/orgChart.model';

@Injectable({
  providedIn: 'root',
})
export class OrgChartService {
  private baseUrl = environment.apiBaseUrl + '/orgChart';

  constructor(private http: HttpClient) { }

  getAllOrgChart(): Observable<OrgChart[]> {
    return this.http.get<OrgChart[]>(`${this.baseUrl}`, { withCredentials: true });
  }

  getDepartmentList(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/get-departments`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
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

  show(): Observable<OrgChartGraphDto[]> {
    return this.http.get<OrgChartGraphDto[]>(`${this.baseUrl}/show-org-chart`, {
      withCredentials: true,
    });
  }

}
