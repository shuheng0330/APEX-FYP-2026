import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';

import { CompetencyCollaborationOverviewData, EditCompetencyCollaboration, ProposalParticipantId, ProposeCompetencyRequest } from '../models/competency-collaboration.model';

@Injectable({
    providedIn: 'root',
})
export class CompetencyDefinitionCollaborationService {
    private baseUrl = `${environment.apiBaseUrl}/competency-definition-collaboration`;

    constructor(private http: HttpClient) { }

    createCollaboration(competencyProposal: ProposeCompetencyRequest): Observable<void> {
        return this.http.post<void>(`${this.baseUrl}`, competencyProposal, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    getOverviewData(): Observable<CompetencyCollaborationOverviewData[]> {
        return this.http.get<CompetencyCollaborationOverviewData[]>(`${this.baseUrl}/overview`, {
            withCredentials: true
        });
    }

    editCollaboration(competencyProposal: EditCompetencyCollaboration): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/edit-competency-proposal`, competencyProposal, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    rejectCollaboration(proposalParticipantId: ProposalParticipantId): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/reject-competency-proposal`, proposalParticipantId, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    bulkRejectCollaboration(proposalParticipantIds: ProposalParticipantId[]): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/bulk-reject-competency-proposal`, proposalParticipantIds, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    approveCollaboration(proposalParticipantId: ProposalParticipantId): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/approve-competency-proposal`, proposalParticipantId, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    bulkApproveCollaboration(proposalParticipantIds: ProposalParticipantId[]): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/bulk-approve-competency-proposal`, proposalParticipantIds, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }
}
