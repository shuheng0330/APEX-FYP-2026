import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CompetencyScore, EnrollmentProgress, Scoreboard } from '../models/sop-scoreboard.model';

/** Client for the Training Scoreboard (/api/sop-scoreboard, FR-10). */
@Injectable({ providedIn: 'root' })
export class SopScoreboardService {
  private baseUrl = environment.apiBaseUrl + '/sop-scoreboard';

  constructor(private http: HttpClient) {}

  /**
   * The scoreboard. A background refresh skips the page-wide loading overlay and error pop-ups,
   * so the page can update itself quietly while it is open.
   */
  scoreboard(background = false): Observable<Scoreboard> {
    const headers = background
      ? new HttpHeaders({ 'X-Skip-Loading': 'true', 'X-Skip-Error-Handler': 'true' })
      : undefined;
    return this.http.get<Scoreboard>(this.baseUrl, { headers, withCredentials: true });
  }

  enrollment(enrollmentId: number): Observable<EnrollmentProgress> {
    return this.http.get<EnrollmentProgress>(`${this.baseUrl}/enrollments/${enrollmentId}`, { withCredentials: true });
  }

  /** The read-only competency scores the Staff Appraisal module uses (/api/sop-competency, FR-10-06). */
  competencyScores(staffId: string): Observable<CompetencyScore[]> {
    return this.http.get<CompetencyScore[]>(`${environment.apiBaseUrl}/sop-competency/${staffId}`, { withCredentials: true });
  }
}
