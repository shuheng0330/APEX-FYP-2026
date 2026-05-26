import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';

import { CompetencyAssignmentRequiredData, CompetencyAssignmentProposalOverviewData, ProposeCompetencyAssignmentRequest, RoleCompetencyProposalId, UpdateCompetencyAssignmentRequest } from '../models/competency-collaboration.model';

@Injectable({
    providedIn: 'root',
})
export class CompetencyAssignmentCollaborationService {
    private baseUrl = `${environment.apiBaseUrl}/competency-assignment-collaboration`;

    constructor(private http: HttpClient) { }

    createCollaboration(competencyAssignmentProposal: ProposeCompetencyAssignmentRequest): Observable<void> {
        return this.http.post<void>(`${this.baseUrl}`, competencyAssignmentProposal, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    getCompetencyRequired(roleCompetencyProposalId?: RoleCompetencyProposalId): Observable<CompetencyAssignmentRequiredData[]> {
        let params = new HttpParams();

        if (roleCompetencyProposalId) {
            const { proposalId, roleId, staffId } = roleCompetencyProposalId;
            if (proposalId !== undefined) params = params.set('proposalId', proposalId.toString());
            if (roleId !== undefined) params = params.set('roleId', roleId.toString());
            if (staffId !== undefined) params = params.set('staffId', staffId.toString());
        }

        return this.http.get<CompetencyAssignmentRequiredData[]>(`${this.baseUrl}/creation-required-competency`, {
            withCredentials: true,
            params,
        });
    }

    getOverviewData(): Observable<CompetencyAssignmentProposalOverviewData[]> {
        return this.http.get<CompetencyAssignmentProposalOverviewData[]>(`${this.baseUrl}/overview`,
            {
                withCredentials: true
            });
    }

    updateCompetency(req: UpdateCompetencyAssignmentRequest): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/edit-competency-assignment-proposal`, req, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    rejectCollaboration(selectedId: RoleCompetencyProposalId): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/reject-competency-assignment-proposal`, selectedId, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    bulkRejectCollaboration(selectedIds: RoleCompetencyProposalId[]): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/bulk-reject-competency-assignment-proposal`, selectedIds, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    approveCollaboration(selectedId: RoleCompetencyProposalId): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/approve-competency-assignment-proposal`, selectedId, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    bulkApproveCollaboration(selectedIds: RoleCompetencyProposalId[]): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/bulk-approve-competency-assignment-proposal`, selectedIds, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }
}
