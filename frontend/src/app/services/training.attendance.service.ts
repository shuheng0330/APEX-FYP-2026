import {Injectable} from '@angular/core';
import {environment} from '../../environments/environment';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {CheckInResponse, TrainingAttendance} from '../models/training.model';
import {Observable} from 'rxjs';
import {Staff} from '../models/staff.model';


@Injectable({
  providedIn: 'root',
})
export class TrainingAttendanceService {
  private baseUrl = environment.apiBaseUrl + '/trainings/attendance';

  constructor(private http: HttpClient) {
  }

  checkin(attendance: TrainingAttendance): Observable<CheckInResponse> {
    return this.http.post<CheckInResponse>(`${this.baseUrl}/checkin`, attendance, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getAttendedStaffListByTrainingId(trainingId: number): Observable<Staff[]> {
    return this.http.get<Staff[]>(`${this.baseUrl}/staff/${trainingId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getTrainingAttendanceByTrainingId(trainingId: number):Observable<TrainingAttendance[]> {
    return this.http.get<TrainingAttendance[]>(`${this.baseUrl}/${trainingId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
  }

  getAttendedTrainingIdListByStaffId(staffId: string): Observable<number[]> {
    return this.http.get<number[]>(`${this.baseUrl}/by-staff/${staffId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
  }
}
