import { Course } from '../core/models/course.model';
import { Page } from '../core/models/page.model';
import { Student, StudentDetail } from '../core/models/student.model';

export function student(id: number, firstName: string, lastName = 'Test'): Student {
  return {
    id,
    firstName,
    lastName,
    fullName: `${firstName} ${lastName}`,
    email: `${firstName.toLowerCase()}@university.edu`,
    dateOfBirth: '2002-05-14',
    enrolledCourseCount: 0,
    totalCredits: 0,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
  };
}

export function studentDetail(
  id: number,
  firstName: string,
  courseIds: number[] = [],
): StudentDetail {
  return {
    ...student(id, firstName),
    enrolledCourseCount: courseIds.length,
    totalCredits: courseIds.length * 3,
    courses: courseIds.map((courseId) => ({
      courseId,
      code: `CS${100 + courseId}`,
      title: `Course ${courseId}`,
      credits: 3,
      enrolledOn: '2026-09-01',
    })),
  };
}

export function course(id: number, code: string, enrolledCount = 0, capacity = 30): Course {
  return {
    id,
    code,
    title: `Course ${code}`,
    description: null,
    credits: 3,
    capacity,
    enrolledCount,
    availableSeats: capacity - enrolledCount,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
  };
}

export function page<T>(
  content: T[],
  pageIndex = 0,
  size = 10,
  totalElements = content.length,
): Page<T> {
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  return {
    content,
    page: pageIndex,
    size,
    totalElements,
    totalPages,
    first: pageIndex === 0,
    last: pageIndex >= totalPages - 1,
  };
}
