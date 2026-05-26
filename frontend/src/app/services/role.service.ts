import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { CreateRoleResponse, Role, RoleJobScopeMap, UpdateRoleResponse } from '../models/role.model';
import { RoleOverview, RoleCreation, RoleEdit } from '../models/role.model';

@Injectable({
  providedIn: 'root',
})
export class RoleService {
  private baseUrl = environment.apiBaseUrl + "/role";

  constructor(private http: HttpClient) { }

  getAllRoles(): Observable<Role[]> {
    return this.http.get<Role[]>(`${this.baseUrl}`, { withCredentials: true });
  }

  getRoleOverview(): Observable<RoleOverview[]> {
    return this.http.get<RoleOverview[]>(`${this.baseUrl}/overview`, { withCredentials: true });
  }

  getRoleJobScopeMap(): Observable<RoleJobScopeMap[]> {
    return this.http.get<RoleJobScopeMap[]>(`${this.baseUrl}/jobScope-map`, { withCredentials: true });
  }

  createRole(roleCreationDto: RoleCreation,): Observable<CreateRoleResponse> {
    return this.http.post<CreateRoleResponse>(`${this.baseUrl}`, roleCreationDto, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  updateRole(role: RoleEdit): Observable<UpdateRoleResponse> {
    return this.http.put<UpdateRoleResponse>(`${this.baseUrl}/edit-role`, role, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  toggleVisibility(roleId: number, visibility: boolean): Observable<void> {
    return this.http.put<void>(
      `${this.baseUrl}/toggle-visibility`,
      { roleId, visibility },
      {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-Show-Success': 'true' })
      }
    );
  }

  deleteRole(roleId: number): Observable<void> {
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
