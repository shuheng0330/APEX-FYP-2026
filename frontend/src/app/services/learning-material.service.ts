import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {Observable} from 'rxjs';
import {LearningMaterial} from '../models/learning-material.model';

@Injectable({
  providedIn: 'root',
})
export class LearningMaterialService {
  private baseUrl = environment.apiBaseUrl + '/learning-material';

  constructor(private http: HttpClient) {}

  createLearningMaterial(learningMaterial: LearningMaterial): Observable<LearningMaterial> {
    return this.http.post<LearningMaterial>(`${this.baseUrl}/create`, learningMaterial, {
      withCredentials: true
    });
  }

  uploadFile(fileData: FormData): Observable<{  fileUrl: string }>{
    return this.http.post<{ fileUrl: string}>(`${this.baseUrl}/upload`, fileData, {
      withCredentials: true
    });
  }

  getAllLearningMaterials(): Observable<LearningMaterial[]>{
    return this.http.get<LearningMaterial[]>(`${this.baseUrl}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getLearningMaterialById(materialId?: number): Observable<LearningMaterial>{
    return this.http.get<LearningMaterial>(`${this.baseUrl}/${materialId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
  }

  getRecommendationMaterialList(staffId: string){
    return this.http.get<LearningMaterial[]>(`${this.baseUrl}/recommendation/${staffId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
  }

  updateLearningMaterial(materialId: number, learningMaterial: any): Observable<any>{
    return this.http.put(`${this.baseUrl}/update/${materialId}`, learningMaterial, {
      withCredentials: true
    });
  }

  deleteLearningMaterial(materialId: number): Observable<void>{
    return this.http.delete<void>(`${this.baseUrl}/delete/${materialId}`,{
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  bulkDeleteLearningMaterial(materialIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bulk-delete`, {
      withCredentials: true,
      params: { materialIds },
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }


}
