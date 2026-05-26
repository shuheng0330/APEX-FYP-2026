import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { CreateStaffSelfDeclaredSkillRequest, StaffSelfDeclaredSkill } from '../models/staff.model';

@Injectable({
  providedIn: 'root',
})
export class StaffSkillService {
  private baseUrl = `${environment.apiBaseUrl}/staff-self-declare-skill`;

  constructor(private http: HttpClient) { }

  create(request: CreateStaffSelfDeclaredSkillRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}`, request, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  overview(staffId: string): Observable<StaffSelfDeclaredSkill[]> {
    return this.http.get<StaffSelfDeclaredSkill[]>(`${this.baseUrl}/overview`, {
      params: { staffId },
      withCredentials: true
    });
  }

  update(request: CreateStaffSelfDeclaredSkillRequest): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/edit`, request, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  delete(selectedId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete`, {
      withCredentials: true,
      params: { selectedId },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  deleteAll(selectedIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      withCredentials: true,
      params: { selectedIds },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }
}
