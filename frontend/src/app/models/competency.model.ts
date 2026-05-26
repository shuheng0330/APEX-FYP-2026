export interface Competency {
  id?: number;
  name: string;
  description: string;
  deleted: boolean;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface CompetencyEdit {
  competencyId: number;
  competencyName: string;
  competencyDescription: string;
  compTagList?: string[];
}

export interface CompetencyCreation {
  competencyName: string;
  competencyDescription: string;
  compTagList: string[];
}

