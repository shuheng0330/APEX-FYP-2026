import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {Observable} from 'rxjs';
import {BulkInvitation, TrainingInvitation, UpdateInvitation} from '../models/training.model';

@Injectable({
  providedIn: 'root',
})
export class TrainingInvitationService {
  private baseUrl = environment.apiBaseUrl + '/training/invitation';

  constructor(private http: HttpClient) {}

  getAllInvitations(): Observable<TrainingInvitation[]> {
    return this.http.get<TrainingInvitation[]>(`${this.baseUrl}`, {
    withCredentials: true,
    headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  inviteStaff(bulkInvitation: BulkInvitation): Observable<TrainingInvitation[]> {
    return this.http.post<TrainingInvitation[]>(`${this.baseUrl}/create`, bulkInvitation, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getAllInvitationsByTraining(trainingId: number): Observable<TrainingInvitation[]> {
    return this.http.get<TrainingInvitation[]>(`${this.baseUrl}/get/${trainingId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getTrainingInvitationsByStaffId(staffId: string): Observable<TrainingInvitation[]> {
    return this.http.get<TrainingInvitation[]>(`${this.baseUrl}/staff/${staffId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  updateInvitationStatus(invitationId: number, dto: UpdateInvitation): Observable<TrainingInvitation> {
    return this.http.patch<TrainingInvitation>(`${this.baseUrl}/${invitationId}/status`, dto, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }
}
