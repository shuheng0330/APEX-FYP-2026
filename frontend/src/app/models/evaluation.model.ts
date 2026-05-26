export interface EvaluationDTO {
  evaluationId?: number;
  staffId: string;
  comment: string;
  overallScore?: number | null;
  createdAt?: string;
  createdBy?: string;
  evaluationCycleEndDate?: string;
  ratings: RatingDTO[];
}

export interface RatingDTO {
  compId: number;
  competencyName?: string;
  rating: number;
}
