import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {Observable} from 'rxjs';
import {StaffConflictDTO, TrainingRegistration} from '../models/training.model';
import {StaffTemp} from '../models/staff-temp.model';

@Injectable({
  providedIn: 'root',
})
export class TrainingRegistrationService {
  private baseUrl = environment.apiBaseUrl + '/register-training';

  constructor(private http: HttpClient) {}

  createTrainingRegistration(trainingRegistration: TrainingRegistration): Observable<TrainingRegistration> {
    return this.http.post<TrainingRegistration>(`${this.baseUrl}`, trainingRegistration, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getAllRegisteredStaffList(trainingId: number): Observable<StaffTemp[]> {
    return this.http.get<StaffTemp[]>(`${this.baseUrl}/staff/${trainingId}`, {
      withCredentials: true,
        headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getConflictingRegistrations(trainingId: number): Observable<StaffConflictDTO[]> {
    return this.http.get<StaffConflictDTO[]>(`${this.baseUrl}/conflicting-staff/${trainingId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }
}
