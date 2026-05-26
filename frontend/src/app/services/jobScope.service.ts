import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { JobScope } from '../models/jobScope.model';

@Injectable({
  providedIn: 'root',
})
export class JobScopeService {
  private baseUrl = environment.apiBaseUrl + "/jobScope";

  constructor(private http: HttpClient) { }

  getAllJobScopes(): Observable<JobScope[]> {
    return this.http.get<JobScope[]>(`${this.baseUrl}`, { withCredentials: true });
  }
}
