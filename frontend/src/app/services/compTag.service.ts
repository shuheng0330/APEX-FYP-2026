import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { CompTag } from '../models/comp-tag.model';

@Injectable({
  providedIn: 'root',
})
export class CompTagService {
  private baseUrl = `${environment.apiBaseUrl}/compTag`;

  constructor(private http: HttpClient) { }

  getAllCompTags(): Observable<CompTag[]> {
    return this.http.get<CompTag[]>(`${this.baseUrl}`, { withCredentials: true });
  }
}
