import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { RoleCompetency } from '../models/role-compotency.model';
import { CompetencyAssignmentOverviewData, RoleCompetencyCreation } from '../models/role-compotency.model';
import { RoleDetailsData } from '../models/role.model';

@Injectable({
  providedIn: 'root',
})
export class RoleCompetencyService {
  private baseUrl = environment.apiBaseUrl + "/role-competency";

  constructor(private http: HttpClient) {
  }

  getAllRoleCompetencies(): Observable<RoleCompetency[]> {
    return this.http.get<RoleCompetency[]>(`${this.baseUrl}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getRoleCompetencyOverview(): Observable<CompetencyAssignmentOverviewData[]> {
    return this.http.get<CompetencyAssignmentOverviewData[]>(`${this.baseUrl}/overview`, {
      withCredentials: true,
    });
  }

  createRoleCompetency(roleCompetency: RoleCompetencyCreation): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}`, roleCompetency, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  deleteRoleCompetency(roleId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete`, {
      withCredentials: true,
      params: { roleId },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  bulkDeleteRoleCompetency(roleIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      withCredentials: true,
      params: { roleIds },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  getRoleDetails(roleId: number): Observable<RoleDetailsData> {
    return this.http.get<RoleDetailsData>(`${this.baseUrl}/role-details`,
      {
        withCredentials: true,
        params: { roleId }
      });
  }

  roleDetailsOverview(): Observable<RoleDetailsData[]> {
    return this.http.get<RoleDetailsData[]>(`${this.baseUrl}/role-details-overview`,
      {
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
