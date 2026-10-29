import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TrainingRecord } from '../models/training-record.model';

/** Client for training records (/api/training-records, FR-18). */
@Injectable({ providedIn: 'root' })
export class TrainingRecordService {
  private baseUrl = environment.apiBaseUrl + '/training-records';

  constructor(private http: HttpClient) {}

  /** The signed-in user's own record. */
  mine(): Observable<TrainingRecord> {
    return this.http.get<TrainingRecord>(`${this.baseUrl}/me`, { withCredentials: true });
  }

  /** Someone else's, for their manager, HR and the Super Admin. */
  of(staffId: string): Observable<TrainingRecord> {
    return this.http.get<TrainingRecord>(`${this.baseUrl}/${staffId}`, { withCredentials: true });
  }
}
