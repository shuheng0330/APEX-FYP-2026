import { Role } from "./role.model";
import { CareerPathway } from "./careerPathway.model";

export interface CareerPathwayRole {
  id?: number;
  careerPathway: CareerPathway;
  role: Role;
  fromRole?: Role;
  isDeleted: boolean;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}
