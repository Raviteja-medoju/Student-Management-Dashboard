import { BreakpointObserver } from '@angular/cdk/layout';
import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, finalize, map } from 'rxjs';
import {
  Student,
  StudentQuery,
  StudentSortField,
  StudentStatusFilter,
} from '../../../core/models/student.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UndoService } from '../../../core/services/undo.service';
import { CourseStore } from '../../../core/state/course.store';
import { StudentStore } from '../../../core/state/student.store';
import { AvatarComponent } from '../../../shared/avatar/avatar.component';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { SkeletonComponent } from '../../../shared/skeleton/skeleton.component';
import { ageFrom } from '../../../shared/utils/date';
import { saveDownload } from '../../../shared/utils/download';
import { paramsToQuery, queryToParams } from './student-query-params';

/** Table column -> backend sort field. */
const SORT_FIELDS: Record<string, StudentSortField> = {
  name: 'lastName',
  email: 'email',
  dateOfBirth: 'dateOfBirth',
  createdAt: 'createdAt',
};

@Component({
  selector: 'app-student-list',
  imports: [
    AsyncPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    EmptyStateComponent,
    AvatarComponent,
    SkeletonComponent,
  ],
  templateUrl: './student-list.component.html',
  styleUrl: './student-list.component.scss',
})
export class StudentListComponent {
  protected readonly store = inject(StudentStore);
  private readonly courseStore = inject(CourseStore);
  private readonly confirm = inject(ConfirmService);
  private readonly undo = inject(UndoService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly courses$ = this.courseStore.courses$;
  protected readonly exporting = signal(false);
  protected readonly skeletonRows = Array.from({ length: 6 }, (_, i) => i);
  protected readonly ageFrom = ageFrom;

  protected readonly statuses: { value: StudentStatusFilter; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: 'enrolled', label: 'Enrolled' },
    { value: 'none', label: 'Not enrolled' },
    { value: 'fulltime', label: 'Full-time (12+ credits)' },
  ];

  /** Fewer columns on small screens, so the table never scrolls sideways on a phone. */
  protected readonly columns = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 799px)')
      .pipe(
        map((state) =>
          state.matches
            ? ['name', 'courses'] // rows open the student page, which has Delete
            : ['name', 'email', 'dateOfBirth', 'courses', 'credits', 'actions'],
        ),
      ),
    { initialValue: ['name', 'email', 'dateOfBirth', 'courses', 'credits', 'actions'] },
  );

  constructor() {
    this.courseStore.ensureLoaded();

    // URL -> store: first visit, back/forward and shared links all restore the same view.
    this.route.queryParamMap.pipe(map(paramsToQuery), takeUntilDestroyed()).subscribe((query) => {
      if (this.searchControl.value.trim() !== query.search) {
        this.searchControl.setValue(query.search, { emitEvent: false });
      }
      this.store.applyQuery(query);
    });

    // Store -> URL: every search, filter, sort or page change is reflected in the address bar.
    this.store.query$
      .pipe(
        map((query) => queryToParams(query)),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(),
      )
      .subscribe((queryParams) =>
        this.router.navigate([], { relativeTo: this.route, queryParams, replaceUrl: true }),
      );

    // Typing waits for a 300ms pause and skips repeats before hitting the API.
    this.searchControl.valueChanges
      .pipe(
        map((term) => term.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((term) => this.store.setSearch(term));
  }

  protected onPage(event: PageEvent): void {
    this.store.setPage(event.pageIndex, event.pageSize);
  }

  protected onSort(sort: Sort): void {
    const field = SORT_FIELDS[sort.active] ?? 'lastName';
    this.store.setSort(sort.direction ? field : 'lastName', sort.direction || 'asc');
  }

  protected sortColumn(field: StudentSortField): string {
    return Object.keys(SORT_FIELDS).find((key) => SORT_FIELDS[key] === field) ?? 'name';
  }

  protected hasFilters(query: StudentQuery | null): boolean {
    return !!query && (!!query.search || query.courseId !== null || query.status !== 'all');
  }

  protected clearSearch(): void {
    this.searchControl.setValue('');
  }

  protected clearFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.store.clearFilters();
  }

  protected export(): void {
    this.exporting.set(true);
    this.store
      .exportCsv()
      .pipe(finalize(() => this.exporting.set(false)))
      .subscribe((response) => {
        saveDownload(response, 'students.csv');
        this.notifications.success('CSV downloaded');
      });
  }

  protected open(student: Student): void {
    this.router.navigate(['/students', student.id]);
  }

  /** Confirm first, then hide the row with an "Undo" window before the server delete. */
  protected delete(student: Student, event: Event): void {
    event.stopPropagation();
    const courses = student.enrolledCourseCount;
    this.confirm
      .confirm({
        title: `Delete ${student.fullName}?`,
        message:
          courses > 0
            ? `This also removes their ${courses} course enrollment${courses === 1 ? '' : 's'}. You can undo for a few seconds.`
            : 'You can undo for a few seconds.',
        confirmText: 'Delete student',
        tone: 'danger',
      })
      .pipe(filter(Boolean))
      .subscribe(() =>
        this.undo.run({
          message: `${student.fullName} deleted`,
          hide: () => this.store.hide(student.id),
          restore: () => this.store.unhide(student.id),
          commit: () => this.store.remove(student.id),
        }),
      );
  }

  protected trackById(_: number, student: Student): number {
    return student.id;
  }
}
