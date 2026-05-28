export interface OrgWideSummaryDto {
  totalStaffEvaluated: number;
  totalStaff: number;
  orgAverageScore: number;
  topDepartment: string | null;
  topDepartmentScore: number;
  pendingAppraisals: number;
}

export interface OrgWideScoreDistributionDto {
  range: string;
  count: number;
  staffNames: string[];
}

export interface OrgWideDepartmentRankingDto {
  departmentName: string;
  averageScore: number;
  previousAverageScore: number;
  scoreChange: number;
  staffCount: number;
  status: string;
}

export interface OrgWideCompetencyAverageDto {
  competencyName: string;
  averageRating: number;
}

export interface OrgWideCompetencyBreakdownDto {
  departmentName: string;
  competencies: OrgWideCompetencyAverageDto[];
}

export interface OrgWideDepartmentTrendDepartmentDto {
  departmentName: string;
  averageScore: number;
}

export interface OrgWideDepartmentTrendDto {
  year: number;
  departments: OrgWideDepartmentTrendDepartmentDto[];
}
