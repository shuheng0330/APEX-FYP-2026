import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders, HttpParams} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {Observable} from 'rxjs';
import {EvaluationDTO} from '../models/evaluation.model';
import {
  OrgWideCompetencyBreakdownDto,
  OrgWideDepartmentRankingDto,
  OrgWideDepartmentTrendDto,
  OrgWideScoreDistributionDto,
  OrgWideSummaryDto
} from '../models/org-wide-evaluation.model';


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

  getOrgSummary(): Observable<OrgWideSummaryDto> {
    return this.http.get<OrgWideSummaryDto>(`${this.baseUrl}/org/summary`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getOrgScoreDistribution(): Observable<OrgWideScoreDistributionDto[]> {
    return this.http.get<OrgWideScoreDistributionDto[]>(`${this.baseUrl}/org/score-distribution`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getOrgDepartmentRanking(): Observable<OrgWideDepartmentRankingDto[]> {
    return this.http.get<OrgWideDepartmentRankingDto[]>(`${this.baseUrl}/org/department-ranking`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    });
  }

  getOrgCompetencyBreakdown(departmentName?: string): Observable<OrgWideCompetencyBreakdownDto[]> {
    const options: {
      withCredentials: boolean;
      headers: HttpHeaders;
      params?: HttpParams;
    } = {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    };

    if (departmentName) {
      options.params = new HttpParams().set('departmentName', departmentName);
    }

    return this.http.get<OrgWideCompetencyBreakdownDto[]>(`${this.baseUrl}/org/competency-breakdown`, options);
  }

  getOrgDepartmentTrend(years: number = 5): Observable<OrgWideDepartmentTrendDto[]> {
    return this.http.get<OrgWideDepartmentTrendDto[]>(`${this.baseUrl}/org/department-trend`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }),
      params: new HttpParams().set('years', years)
    });
  }
}
