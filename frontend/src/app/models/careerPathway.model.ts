import { OrgChartNode } from "ngx-interactive-org-chart";
import { Track } from "./track.model";
import { NzTreeNodeOptions } from "ng-zorro-antd/core/tree";
import { OrgChart } from "./orgChart.model";
import { Staff } from "./staff.model";

export interface CareerPathway {
    id?: number;
    name: string;
    description: string;
    track: string;
    orgChart: OrgChart;
    deleted: boolean;
    createdBy: string;
    createdAt: string;
    updatedBy: string;
    updatedAt: string;
}

export interface CreateCareerPathwayRequest {
    careerPathwayId?: number;
    orgChartId: number;
    careerPathwayName: string;
    description?: string;
    track?: string[];
    rootRoleId: number;
    parentChildRoleId?: Record<number, number[]>
}

export interface ParentChildRoleId {
    parentId: number;
    childId: number;
}

export interface CareerPathwayOverview {
    orgChartId: number;
    orgChartName: string;
    orgChartDeleted: boolean;
    careerPathwayId: number;
    careerPathwayName: string;
    careerPathwayDescription?: string;
    trackDtoList: Track[];
    graph: OrgChartNode;
    nzNode?: NzTreeNodeOptions;
    currentRoleName?: string;
}

export interface CareerPathwayOverviewGraphData {
    deleted: boolean;
    hasVisited: number;
}

export interface UpdateCareerPathwayAssignmentRequest {
    careerPathwayId: number;
    staffIds?: string[];
}

export interface CareerPathwayAssignmentOverview {
    orgChartId: number;
    orgChartName: string;
    orgChartDeleted: boolean;
    careerPathwayId: number;
    careerPathwayName: string;
    staffs: Staff[];
}