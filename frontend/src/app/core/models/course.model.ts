/** A course with live seat numbers. */
export interface Course {
  id: number;
  code: string;
  title: string;
  description: string | null;
  credits: number;
  capacity: number;
  enrolledCount: number;
  availableSeats: number;
  createdAt: string;
  updatedAt: string;
}

export interface EnrolledStudent {
  studentId: number;
  fullName: string;
  email: string;
  enrolledOn: string;
}

/** A single course with its roster. */
export interface CourseDetail extends Course {
  students: EnrolledStudent[];
}

export interface CourseRequest {
  code: string;
  title: string;
  description: string | null;
  credits: number;
  capacity: number;
}

/** Validation limits shared by the form; they mirror the backend's Bean Validation rules. */
export const COURSE_RULES = {
  codePattern: /^[A-Za-z]{2,4}\d{3}$/,
  titleMaxLength: 100,
  descriptionMaxLength: 500,
  minCredits: 1,
  maxCredits: 6,
  minCapacity: 1,
  maxCapacity: 500,
} as const;
