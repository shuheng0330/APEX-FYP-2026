/** A course category on the Course Categories page (FR-19), with what the signed-in user may do. */
export interface CourseCategory {
  id: number;
  name: string;
  /** #RRGGBB */
  color: string;
  /** An icon name */
  icon: string;
  /** Comes with the system; General is the one courses fall back to. */
  defaultCategory: boolean;
  general: boolean;
  retired: boolean;
  courseCount: number;
  createdByName?: string;
  canEdit: boolean;
  canRename: boolean;
  canRetire: boolean;
  canDelete: boolean;
}

/** One of a course's categories; the primary one comes first (FR-19-05). */
export interface CourseCategoryChip {
  id: number | null;
  name: string;
  color: string;
  icon: string;
  primary: boolean;
  retired: boolean;
}

/** The colours and icons a category can have. */
export interface CourseCategoryOptions {
  colors: string[];
  icons: string[];
}
