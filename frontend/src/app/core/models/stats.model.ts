import { Course } from './course.model';
import { Student } from './student.model';

export interface MonthlyCount {
  /** ISO year-month, e.g. "2026-09" */
  month: string;
  enrollments: number;
}

export interface CreditBand {
  label: string;
  minCredits: number;
  maxCredits: number | null;
  students: number;
}

export interface Stats {
  totalStudents: number;
  totalCourses: number;
  totalEnrollments: number;
  averageCreditsPerStudent: number;
  seatUtilizationPercent: number;
  popularCourses: Course[];
  recentStudents: Student[];
  enrollmentsPerCourse: Course[];
  enrollmentsByMonth: MonthlyCount[];
  creditLoad: CreditBand[];
}
