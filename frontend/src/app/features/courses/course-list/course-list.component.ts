import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  startWith,
  switchMap,
} from 'rxjs';
import { Course, CourseDetail } from '../../../core/models/course.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UndoService } from '../../../core/services/undo.service';
import { CourseStore } from '../../../core/state/course.store';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { SkeletonComponent } from '../../../shared/skeleton/skeleton.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { SeatMeterComponent } from '../../../shared/seat-meter/seat-meter.component';
import {
  CourseFormDialogComponent,
  CourseFormDialogData,
} from '../course-form-dialog/course-form-dialog.component';

@Component({
  selector: 'app-course-list',
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    MatTooltipModule,
    MatProgressBarModule,
    PageHeaderComponent,
    EmptyStateComponent,
    SeatMeterComponent,
    SkeletonComponent,
  ],
  templateUrl: './course-list.component.html',
  styleUrl: './course-list.component.scss',
})
export class CourseListComponent {
  protected readonly store = inject(CourseStore);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  private readonly undo = inject(UndoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly skeletonCards = Array.from({ length: 6 }, (_, i) => i);

  protected readonly filterControl = new FormControl('', { nonNullable: true });

  private readonly term$ = this.filterControl.valueChanges.pipe(
    debounceTime(150),
    startWith(''),
    map((term) => term.trim().toLowerCase()),
    distinctUntilChanged(),
  );

  /** Catalogue from the store, filtered client-side (the list is small). */
  protected readonly courses$ = combineLatest([this.store.courses$, this.term$]).pipe(
    map(([courses, term]) =>
      term
        ? courses.filter(
            (c) =>
              c.code.toLowerCase().includes(term) ||
              c.title.toLowerCase().includes(term) ||
              (c.description ?? '').toLowerCase().includes(term),
          )
        : courses,
    ),
  );

  constructor() {
    this.store.load(); // always show fresh seat counts when the page opens

    // /courses?new=1 (e.g. from the Ctrl+K menu) opens the "New course" dialog.
    if (this.route.snapshot.queryParamMap.get('new')) {
      this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
      queueMicrotask(() => this.create());
    }
  }

  protected create(): void {
    this.openForm(null);
  }

  protected edit(course: Course): void {
    this.openForm(course);
  }

  /** Confirm first, then hide the card with an "Undo" window before the server delete. */
  protected delete(course: Course): void {
    const enrolled = course.enrolledCount;
    this.confirm
      .confirm({
        title: `Delete ${course.code}?`,
        message:
          enrolled > 0
            ? `${enrolled} student${enrolled === 1 ? ' is' : 's are'} enrolled in “${course.title}”. Deleting the course also removes these enrollments. You can undo for a few seconds.`
            : `“${course.title}” will be removed. You can undo for a few seconds.`,
        confirmText: 'Delete course',
        tone: 'danger',
      })
      .pipe(filter(Boolean))
      .subscribe(() =>
        this.undo.run({
          message: `${course.code} deleted`,
          hide: () => this.store.hide(course.id),
          restore: () => this.store.unhide(course.id),
          commit: () => this.store.remove(course.id),
        }),
      );
  }

  protected clearFilter(): void {
    this.filterControl.setValue('');
  }

  private openForm(course: Course | null): void {
    this.dialog
      .open<CourseFormDialogComponent, CourseFormDialogData, CourseDetail>(
        CourseFormDialogComponent,
        {
          data: { course },
          width: '560px',
          maxWidth: '95vw',
          autoFocus: 'first-tabbable',
        },
      )
      .afterClosed()
      .pipe(filter((saved): saved is CourseDetail => !!saved))
      .subscribe((saved) =>
        this.notifications.success(`${saved.code} was ${course ? 'updated' : 'created'}`),
      );
  }
}
