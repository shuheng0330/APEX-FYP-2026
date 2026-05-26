import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {Observable} from 'rxjs';
import {LearningMaterial} from '../models/learning-material.model';
import {StaffLearningMaterial} from '../models/staff-learning-material.model';

@Injectable({
  providedIn: 'root',
})
export class StaffLearningMaterialService {
  private baseUrl = environment.apiBaseUrl + '/learning-material/enroll';

  constructor(private http: HttpClient) {
  }

  enrollLearningMaterial(enrollment: StaffLearningMaterial): Observable<StaffLearningMaterial> {
    return this.http.post<StaffLearningMaterial>(`${this.baseUrl}`, enrollment, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getEnrolledCourses(staffId: string): Observable<LearningMaterial[]> {
    return this.http.get<LearningMaterial[]>(`${this.baseUrl}/my-course/${staffId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }


  getEnrollmentsByMaterialId(materialId: number): Observable<StaffLearningMaterial[]> {
    return this.http.get<StaffLearningMaterial[]>(`${this.baseUrl}/list/${materialId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
  }

  unenroll(staffId: string, materialId: number) :Observable<void>{
    return this.http.delete<void>(`${this.baseUrl}/unenroll/${staffId}/${materialId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
  }

  getAllProgress(staffId: string): Observable<MaterialProgressModel[]>{
    return this.http.get<MaterialProgressModel[]>(`${this.baseUrl}/all/progress/${staffId}`);
  }

  getMaterialByStaffId(staffId: string, materialId: number): Observable<StaffLearningMaterial> {
    return this.http.get<StaffLearningMaterial>(`${this.baseUrl}/${staffId}/${materialId}`);
  }
}
