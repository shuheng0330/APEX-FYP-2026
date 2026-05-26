export interface RoleAuthorityMap {
    roleId: number;
    roleName: string;
    authorityMap: Map<number, boolean>; // TranslatedAuthority.id
}

export interface OrgChartRoleAuthorityMap {
    orgChartId: number;
    orgChartName: string;
    orgChartDeleted: boolean;
    roles: RoleAuthorityMap[];
}

export interface TranslatedAuthority {
    id?: number;
    name: string;
    description: string;
    label: string;
}

export interface DepartmentPermission {
    permissions: TranslatedAuthority[];
    departments: OrgChartRoleAuthorityMap[];
}