import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { StaffProfile } from '../models/staff.model';

@Injectable({
  providedIn: 'root',
})
export class StaffProfileService {
  private baseUrl = `${environment.apiBaseUrl}/staff-profile`;

  constructor(private http: HttpClient) { }

  get(staffId: string): Observable<StaffProfile> {
    return this.http.get<StaffProfile>(`${this.baseUrl}`, {
      params: { staffId },
      withCredentials: true
    });
  }

  update(staff: StaffProfile): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/edit`, staff, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  uploadProfilePicture(file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<void>(`${this.baseUrl}/upload-profile-picture`, formData, {
      withCredentials: true,
      headers: new HttpHeaders({
        'X-Show-Success': 'true'
      })
    });
  }

  getProfilePicture(staffId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/get-profile-picture`, {
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }),
      params: { staffId },
      withCredentials: true,
      responseType: 'blob',
      observe: 'response'
    });
  }
}
