import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {Observable} from 'rxjs';


@Injectable({
  providedIn: 'root',
})
export class LearningDocumentProgressService {
  private baseUrl = environment.apiBaseUrl + '/learning-document/progress';

  constructor(private http: HttpClient) {
  }

  saveProgress(progress: UpdateDocumentProgress) {
    return this.http.post(`${this.baseUrl}/update`, progress, {
      withCredentials: true,
      headers: new HttpHeaders({'X-Skip-Error-Handler': 'true',  'X-Skip-Loading': 'true'})
    });
  }

  getProgress(staffId: string, materialId: number, documentId: number): Observable<StaffLearningDocumentProgress> {
    return this.http.get<StaffLearningDocumentProgress>(`${this.baseUrl}/${staffId}/${materialId}/${documentId}`);
  }

  getAllProgress(staffId: string, materialId: number): Observable<StaffLearningDocumentProgress[]> {
    return this.http.get<StaffLearningDocumentProgress[]>(`${this.baseUrl}/all/${staffId}/${materialId}`,{
      withCredentials: true,
      headers: new HttpHeaders({'X-Skip-Error-Handler': 'true'})
    });
  }
}
