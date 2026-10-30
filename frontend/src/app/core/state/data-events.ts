import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { CourseDetail } from '../models/course.model';
import { StudentDetail } from '../models/student.model';

/** Something changed on the server. Stores and pages react to the events they care about. */
export type DataChange =
  | { type: 'student-saved'; student: StudentDetail }
  | { type: 'student-deleted'; id: number }
  | { type: 'enrollments-changed'; studentId: number; student?: StudentDetail }
  | { type: 'course-saved'; course: CourseDetail }
  | { type: 'course-deleted'; id: number };

/**
 * A tiny RxJS event bus that keeps independent views in sync without knowing about each other:
 * e.g. saving enrollments in the student page refreshes course seat counts and the dashboard.
 */
@Injectable({ providedIn: 'root' })
export class DataEvents {
  private readonly events = new Subject<DataChange>();

  readonly changes$ = this.events.asObservable();

  emit(change: DataChange): void {
    this.events.next(change);
  }
}
