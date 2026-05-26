import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { StaffCert } from '../models/staff.model';

@Injectable({
  providedIn: 'root',
})
export class StaffCertService {
  private baseUrl = `${environment.apiBaseUrl}/staff-cert`;

  constructor(private http: HttpClient) { }

  uploadCert(
    certificateName: string,
    certificate: File,
    certificateDescription?: string
  ): Observable<void> {
    const formData = new FormData();
    formData.append('file', certificate);
    formData.append('name', certificateName);

    if (certificateDescription) {
      formData.append('description', certificateDescription);
    }

    return this.http.post<void>(`${this.baseUrl}`, formData, {
      withCredentials: true,
      headers: new HttpHeaders({
        'X-Show-Success': 'true'
      })
    });
  }

  viewCert(staffId: string, fileName: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/get-uploaded-cert`, {
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }),
      params: { staffId, fileName },
      withCredentials: true,
      responseType: 'blob',
      observe: 'response'
    });
  }

  overview(staffId: string): Observable<StaffCert[]> {
    return this.http.get<StaffCert[]>(`${this.baseUrl}/overview`, {
      params: { staffId },
      withCredentials: true
    });
  }

  update(staff: StaffCert): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/edit`, staff, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  delete(certId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete`, {
      params: { certId },
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }

  deleteAll(certIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      params: { certIds },
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Show-Success': 'true' })
    });
  }
}
