import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {Observable} from 'rxjs';
import {EvaluationDTO} from '../models/evaluation.model';


@Injectable({
  providedIn: 'root',
})
export class EvaluationService {
  private baseUrl = environment.apiBaseUrl + "/evaluation";

  constructor(private http: HttpClient) {}

  createEvaluation(evaluationDto: EvaluationDTO): Observable<EvaluationDTO> {
    return this.http.post<EvaluationDTO>(`${this.baseUrl}`, evaluationDto, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getAllEvaluations():Observable<EvaluationDTO[]> {
    return this.http.get<EvaluationDTO[]>(`${this.baseUrl}`,{
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getEvaluationsByDirectDownLineId(staffId: string):Observable<EvaluationDTO[]>{
    return this.http.get<EvaluationDTO[]>(`${this.baseUrl}/direct-down-line/${staffId}`,{
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getAllEvaluationsByStaffId(staffId: string):Observable<EvaluationDTO[]> {
    return this.http.get<EvaluationDTO[]>(`${this.baseUrl}/by-staff/${staffId}`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }
}
