/** A student as shown in lists. Dates are ISO strings (yyyy-MM-dd / ISO instant). */
export interface Student {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  dateOfBirth: string;
  enrolledCourseCount: number;
  totalCredits: number;
  createdAt: string;
  updatedAt: string;
}

export interface EnrolledCourse {
  courseId: number;
  code: string;
  title: string;
  credits: number;
  enrolledOn: string;
}

/** A single student with their courses. */
export interface StudentDetail extends Student {
  courses: EnrolledCourse[];
}

export interface StudentRequest {
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string;
}

export type StudentSortField = 'firstName' | 'lastName' | 'email' | 'dateOfBirth' | 'createdAt';
export type SortDirection = 'asc' | 'desc';

/** Quick filter chips on the student list. "fulltime" = 12 or more enrolled credits. */
export type StudentStatusFilter = 'all' | 'enrolled' | 'none' | 'fulltime';

export const FULL_TIME_CREDITS = 12;

export interface StudentQuery {
  search: string;
  courseId: number | null;
  status: StudentStatusFilter;
  page: number;
  size: number;
  sort: StudentSortField;
  direction: SortDirection;
}

export interface EmailAvailability {
  email: string;
  available: boolean;
}

/** Validation limits shared by the form; they mirror the backend's Bean Validation rules. */
export const STUDENT_RULES = {
  nameMaxLength: 50,
  emailMaxLength: 100,
} as const;
