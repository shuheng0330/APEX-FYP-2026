export interface RoleAssignmentOverviewData {
    orgChartId: number;
    orgChartName: string;
    orgChartDeleted: boolean;
    roleId: number;
    roleName: string;
    roleDeleted: boolean;
    staffList: StaffDto[]
}

interface StaffDto {
    id: string;
    name?: string;
    email: string;
}

export interface RoleAssignmentCreation {
    roleId: number;
    staffIds: string[];
}