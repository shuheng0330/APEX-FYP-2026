import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { StaffTemp } from '../models/staff-temp.model';
import { StaffOverviewData, StaffCreation, StaffEdit, Staff, AddStaffRequiredData } from '../models/staff.model';

//TODO: update staffModel
@Injectable({
  providedIn: 'root',
})
export class StaffService {
  private baseUrl = `${environment.apiBaseUrl}/staff`;

  constructor(private http: HttpClient) { }

  getAllStaffTempToBeReplaced(): Observable<StaffTemp[]> {
    return this.http.get<StaffTemp[]>(`${this.baseUrl}`, {
      withCredentials: true
    });
  }

  getAllStaff(): Observable<Staff[]> {
    return this.http.get<Staff[]>(`${this.baseUrl}`, {
      withCredentials: true
    });
  }

  getStaffOverview(): Observable<StaffOverviewData[]> {
    return this.http.get<StaffOverviewData[]>(`${this.baseUrl}/overview`, {
      withCredentials: true
    });
  }

  register(staff: StaffCreation): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/register`, staff, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  updateStaff(staff: StaffEdit): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/edit-staff`, staff, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  toggleAccountStatus(staffId: string, accountStatus: boolean): Observable<void> {
    return this.http.put<void>(
      `${this.baseUrl}/toggle-account-status`,
      { staffId, accountStatus },
      {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-Show-Success': 'true' })
      }
    );
  }

  deleteStaff(staffId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete`, {
      withCredentials: true,
      params: { staffId },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  bulkDeleteStaff(staffIds: string[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      withCredentials: true,
      params: { staffIds },
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  getAllStaffByAuthorities(authorityNames: string[]): Observable<Staff[]> {
    return this.http.get<Staff[]>(`${this.baseUrl}/by-authority-name`, {
      params: new HttpParams({ fromObject: { authorityNames } }),
      withCredentials: true
    });
  }

  getStaffByIdTempToBeReplaced(id: string): Observable<StaffTemp> {
    return this.http.get<StaffTemp>(`${this.baseUrl}/${id}`, {
      withCredentials: true
    });
  }

  getAddStaffInfo(): Observable<AddStaffRequiredData> {
    return this.http.get<AddStaffRequiredData>(`${this.baseUrl}/add-staff`, {
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

  getDirectDownLineByManagerId(staffId: string):Observable<StaffTemp[]> {
    return this.http.get<StaffTemp[]>(`${this.baseUrl}/direct-down-line/${staffId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }
}
