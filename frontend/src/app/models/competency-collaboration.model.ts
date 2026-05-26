import { Staff } from "./staff.model";
import { CompTag } from "./comp-tag.model";
import { Competency } from "./competency.model";
import { JobScope } from "./jobScope.model";

export interface CompetencyProposal {
    id: ProposalParticipantId,
    staff?: Staff;
    name: string;
    description?: string;
    createdBy: string;
    createdAt: string;
    updatedBy: string;
    updatedAt: string;
}

export interface ProposeCompetencyRequest {
    competencyName: string;
    competencyDescription: string;
    competencyTagList: string[];
    reviewerList: string[];
    proposerList: string[];
}

export interface ProposalParticipantId {
    proposalId: number;
    staffId: string;
}

export interface CompetencyCollaborationOverviewData {
    proposalParticipantId: ProposalParticipantId;
    competencyName: string;
    competencyDescription?: string;
    assignedCompTags?: CompTag[];
    collaboratorName?: string;
    collaboratorEmail: string;
    reviewer: boolean;
    lastUpdatedBy: Staff;
}

export interface EditCompetencyCollaboration {
    proposalParticipantId: ProposalParticipantId;
    competencyName: string;
    competencyDescription?: string;
    compTagList: string[];
}

export interface ProposeCompetencyAssignmentRequest {
    orgChartId: number;
    roleId: number;
    description?: string;
    competencyList: CompetencyAssignmentProposal[];
    jobScopeList?: string[];
    reviewerList?: string[];
    proposerList?: string[];
}

export interface UpdateCompetencyAssignmentRequest {
    id: RoleCompetencyProposalId;
    orgChartId: number;
    roleId: number;
    description?: string;
    competencyList: CompetencyAssignmentProposal[];
    jobScopeList?: string[];
}

export interface CompetencyAssignmentRequiredData {
    id: CompetencyAssignmentRequiredDataId;
    name: string;
    proposerEmail?: string;
}

export interface CompetencyAssignmentRequiredDataId {
    competencyId?: number;
    competencyProposalId?: ProposalParticipantId;
    proposal: boolean;
}

export interface CompetencyAssignmentProposal {
    id: CompetencyAssignmentRequiredDataId;
    competency?: Competency;
    competencyProposal?: CompetencyProposal;
    weightage: number;
}

export interface RoleCompetencyProposalId {
    proposalId: number;
    roleId: number;
    staffId: string;
}

export interface CompetencyAssignmentProposalOverviewData {
    id: RoleCompetencyProposalId;
    orgChartId: number;
    orgChartName: string;
    roleId: number;
    roleName: string;
    description: string;
    assignedJobScopes?: JobScope[];
    assignedCompetencies: CompetencyAssignmentProposal[];
    collaboratorName?: string;
    collaboratorEmail: string;
    reviewer: boolean;
    lastUpdatedBy: Staff;
    totalWeightage: number;
}