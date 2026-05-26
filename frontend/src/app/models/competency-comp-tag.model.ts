import { CompTag } from "./comp-tag.model";

export interface CompetencyCompTag {
    competencyId?: number;
    competencyName: string;
    competencyDescription?: string;
    assignedCompTags: CompTag[];
}