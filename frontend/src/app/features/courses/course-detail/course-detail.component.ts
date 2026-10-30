import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  distinctUntilChanged,
  filter,
  map,
  merge,
  of,
  shareReplay,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { CourseApiService } from '../../../core/api/course-api.service';
import { ApiError } from '../../../core/models/api-error.model';
import { Course, CourseDetail, EnrolledStudent } from '../../../core/models/course.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UndoService } from '../../../core/services/undo.service';
import { CourseStore } from '../../../core/state/course.store';
import { StudentStore } from '../../../core/state/student.store';
import { DataEvents } from '../../../core/state/data-events';
import { AvatarComponent } from '../../../shared/avatar/avatar.component';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { SeatMeterComponent } from '../../../shared/seat-meter/seat-meter.component';
import {
  CourseFormDialogComponent,
  CourseFormDialogData,
} from '../course-form-dialog/course-form-dialog.component';
import {
  EnrollStudentsDialogComponent,
  EnrollStudentsDialogData,
  EnrollStudentsResult,
} from '../enroll-students-dialog/enroll-students-dialog.component';

type CourseState =
  | { status: 'loading' }
  | { status: 'ready'; course: CourseDetail }
  | { status: 'not-found'; message: string }
  | { status: 'error'; message: string };

@Component({
  selector: 'app-course-detail',
  imports: [
    AsyncPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    AvatarComponent,
    EmptyStateComponent,
    SeatMeterComponent,
  ],
  templateUrl: './course-detail.component.html',
  styleUrl: './course-detail.component.scss',
})
export class CourseDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(CourseApiService);
  private readonly store = inject(CourseStore);
  private readonly events = inject(DataEvents);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  private readonly title = inject(Title);
  private readonly studentStore = inject(StudentStore);
  private readonly undo = inject(UndoService);

  /** Loads the course for the route id, and reloads it when related data changes. */
  protected readonly state$: Observable<CourseState> = this.route.paramMap.pipe(
    map((params) => params.get('id')),
    distinctUntilChanged(),
    switchMap((id): Observable<CourseState> => {
      const courseId = Number(id);
      if (!Number.isInteger(courseId) || courseId <= 0) {
        return of({ status: 'not-found', message: `"${id}" is not a valid course id.` });
      }
      const changes$ = this.events.changes$.pipe(filter((e) => e.type !== 'course-deleted'));
      return merge(of(null), changes$).pipe(
        switchMap(() =>
          this.api.get(courseId).pipe(
            tap((course) =>
              this.title.setTitle(`${course.code} ${course.title} · Student Management`),
            ),
            map((course): CourseState => ({ status: 'ready', course })),
            catchError((error: ApiError) =>
              of<CourseState>(
                error.status === 404
                  ? { status: 'not-found', message: error.message }
                  : { status: 'error', message: error.message },
              ),
            ),
          ),
        ),
        startWith<CourseState>({ status: 'loading' }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  protected edit(course: Course): void {
    this.dialog
      .open<CourseFormDialogComponent, CourseFormDialogData, CourseDetail>(
        CourseFormDialogComponent,
        {
          data: { course },
          width: '560px',
          maxWidth: '95vw',
        },
      )
      .afterClosed()
      .pipe(filter((saved): saved is CourseDetail => !!saved))
      .subscribe((saved) => this.notifications.success(`${saved.code} was updated`));
  }

  protected enrollStudents(course: CourseDetail): void {
    this.dialog
      .open<EnrollStudentsDialogComponent, EnrollStudentsDialogData, EnrollStudentsResult>(
        EnrollStudentsDialogComponent,
        { data: { course }, width: '520px', maxWidth: '95vw' },
      )
      .afterClosed()
      .pipe(filter((result): result is EnrollStudentsResult => !!result))
      .subscribe(({ enrolled, failed }) => {
        if (failed.length) {
          this.notifications.error(`Enrolled ${enrolled}; could not enroll ${failed.join(', ')}`);
        } else {
          this.notifications.success(
            `Enrolled ${enrolled} student${enrolled === 1 ? '' : 's'} in ${course.code}`,
          );
        }
      });
  }

  protected unenroll(course: CourseDetail, student: EnrolledStudent, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.confirm
      .confirm({
        title: `Remove ${student.fullName} from ${course.code}?`,
        message: 'Their other enrollments are not affected.',
        confirmText: 'Remove',
        tone: 'danger',
        icon: 'person_remove',
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.studentStore.unenroll(student.studentId, course.id)),
      )
      .subscribe(() =>
        this.notifications.success(`${student.fullName} removed from ${course.code}`),
      );
  }

  /** Confirm, go back to the list, then delete with a few seconds to undo. */
  protected delete(course: CourseDetail): void {
    const enrolled = course.enrolledCount;
    this.confirm
      .confirm({
        title: `Delete ${course.code}?`,
        message:
          enrolled > 0
            ? `${enrolled} student${enrolled === 1 ? ' is' : 's are'} enrolled. Deleting the course also removes these enrollments. You can undo for a few seconds.`
            : 'You can undo for a few seconds.',
        confirmText: 'Delete course',
        tone: 'danger',
      })
      .pipe(filter(Boolean))
      .subscribe(() =>
        this.router.navigate(['/courses']).then(() =>
          this.undo.run({
            message: `${course.code} deleted`,
            hide: () => this.store.hide(course.id),
            restore: () => this.store.unhide(course.id),
            commit: () => this.store.remove(course.id),
          }),
        ),
      );
  }
}
