interface UpdateDocumentProgress {
  staffId: string;
  documentId: number;
  materialId: number;
  progress: number;
  lastPosition: string
  isCompleted?: boolean;
  overallProgress?: number;
}

interface StaffLearningDocumentProgress {
  id: number;
  enrollmentId: number;
  documentId: number;
  progress: number;
  lastPosition: string;
  lastAccessedAt: string;
  isCompleted: boolean;
}
