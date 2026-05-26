import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class FileService {
  private baseUrl = `${environment.apiBaseUrl}/files`;

  constructor(private http: HttpClient) { }

  downloadTemplate(templateName: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/download-template`, {
      params: { templateName },
      withCredentials: true,
      responseType: 'blob',
      observe: 'response'
    });
  }
}
