import {Role} from './role.model';

export interface StaffTemp {
 id: string;
 name: string;
 role: Role;
 performanceOverallScore : number;
 attendance: string;
}
