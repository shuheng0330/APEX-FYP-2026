import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { CascaderOption, TrainingProgram } from '../models/training.model';

@Injectable({
  providedIn: 'root',
})
export class TrainingService {
  private baseUrl = environment.apiBaseUrl + '/trainings';

  constructor(private http: HttpClient) { }

  getAllTrainingProgramsPagination(
    page: number = 0,
    size: number = 10,
    search: string = '',
    departmentIds: number[] = [],
    isPublic: string = 'all' // 'all' | 'public' | 'private'
  ): Observable<any> {
    let params: any = {
      page: page.toString(),
      size: size.toString()
    };

    if (search) {
      params['search'] = search;
    }

    if (departmentIds && departmentIds.length > 0) {
      params['departmentIds'] = departmentIds.join(',');
    }

    if (isPublic !== 'all') {
      params['isPublic'] = isPublic === 'public';
    }

    return this.http.get<any>(`${this.baseUrl}/paged`, {
      params,
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getAllTrainingPrograms(): Observable<TrainingProgram[]> {
    return this.http.get<TrainingProgram[]>(`${this.baseUrl}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  createTrainingProgram(training: TrainingProgram): Observable<TrainingProgram> {
    return this.http.post<TrainingProgram>(`${this.baseUrl}`, training, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  deleteTrainingProgram(trainingId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${trainingId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  updateTrainingProgram(trainingId: number, training: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/${trainingId}`, training, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getTrainingProgramsByStaffId(staffId: string): Observable<TrainingProgram[]> {
    return this.http.get<TrainingProgram[]>(`${this.baseUrl}/registered/${staffId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getTrainingProgramsByRoleId(roleId: number): Observable<TrainingProgram[]> {
    return this.http.get<TrainingProgram[]>(`${this.baseUrl}/by-role/${roleId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getRoleCascaderOptions(): Observable<CascaderOption[]> {
    return this.http.get<CascaderOption[]>(`${this.baseUrl}/role-options`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

}
