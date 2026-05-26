import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { RoleAssignmentOverviewData, RoleAssignmentCreation } from '../models/staff-role.model';

@Injectable({
  providedIn: 'root',
})
export class RoleAssignmentService {
  private baseUrl = environment.apiBaseUrl + "/role/assignment";

  constructor(private http: HttpClient) { }

  getRoleAssignmentOverview(): Observable<RoleAssignmentOverviewData[]> {
    return this.http.get<RoleAssignmentOverviewData[]>(`${this.baseUrl}/overview`, {
      withCredentials: true
    });
  }

  createRoleAssignment(roleAssignment: RoleAssignmentCreation): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}`, roleAssignment, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  updateRoleAssignment(roleAssignment: RoleAssignmentCreation): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/edit-role-assignment`, roleAssignment, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  deleteRoleAssignment(roleId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete`, {
      withCredentials: true,
      params: { roleId },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  bulkDeleteRole(roleIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      withCredentials: true,
      params: { roleIds },
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
