import { HttpResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  BehaviorSubject,
  Observable,
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { StudentApiService } from '../api/student-api.service';
import { ApiError } from '../models/api-error.model';
import { Page } from '../models/page.model';
import {
  SortDirection,
  Student,
  StudentDetail,
  StudentQuery,
  StudentRequest,
  StudentSortField,
  StudentStatusFilter,
} from '../models/student.model';
import { DataEvents } from './data-events';

export interface StudentState {
  query: StudentQuery;
  page: Page<Student> | null;
  loading: boolean;
  error: string | null;
  /** The student open in the detail page; tabs read it through inputs. */
  selected: StudentDetail | null;
  /** Rows deleted but still inside the undo window: hidden, not yet removed on the server. */
  hiddenIds: number[];
}

export const DEFAULT_STUDENT_QUERY: StudentQuery = {
  search: '',
  courseId: null,
  status: 'all',
  page: 0,
  size: 10,
  sort: 'lastName',
  direction: 'asc',
};

const initialState: StudentState = {
  query: DEFAULT_STUDENT_QUERY,
  page: null,
  loading: false,
  error: null,
  selected: null,
  hiddenIds: [],
};

/**
 * Single source of truth for students, built on a BehaviorSubject.
 * Components read slices with the async pipe and call methods to change state;
 * they never hold their own copies of server data.
 */
@Injectable({ providedIn: 'root' })
export class StudentStore {
  private readonly api = inject(StudentApiService);
  private readonly events = inject(DataEvents);
  private readonly state = new BehaviorSubject<StudentState>(initialState);
  private readonly load$ = new Subject<StudentQuery>();

  readonly query$ = this.select((s) => s.query);
  readonly page$ = this.select((s) => s.page);
  readonly students$ = this.select((s) =>
    (s.page?.content ?? []).filter((student) => !s.hiddenIds.includes(student.id)),
  );
  readonly total$ = this.select((s) => {
    const hiddenOnPage = (s.page?.content ?? []).filter((x) => s.hiddenIds.includes(x.id)).length;
    return (s.page?.totalElements ?? 0) - hiddenOnPage;
  });
  readonly loading$ = this.select((s) => s.loading);
  readonly error$ = this.select((s) => s.error);
  readonly selected$ = this.select((s) => s.selected);
  /** True until the first page has arrived (drives the skeleton rows). */
  readonly firstLoad$ = combineLatest([this.loading$, this.page$]).pipe(
    map(([loading, page]) => loading && page === null),
    distinctUntilChanged(),
  );

  constructor() {
    const destroyRef = inject(DestroyRef);

    // Each load cancels the one in flight (switchMap), so a slow response for an old
    // search term can never overwrite the results of a newer one.
    this.load$
      .pipe(
        tap(() => this.patch({ loading: true, error: null })),
        switchMap((query) =>
          this.api.list(query).pipe(
            map((page) => ({ page, error: null })),
            catchError((error: ApiError) => of({ page: this.snapshot.page, error: error.message })),
          ),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => this.patch({ loading: false, ...result }));

    // Editing or deleting a course changes course counts and credits shown for students.
    this.events.changes$
      .pipe(
        filter((e) => e.type === 'course-saved' || e.type === 'course-deleted'),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.refresh());
  }

  // ---------- list ----------

  get currentQuery(): StudentQuery {
    return this.snapshot.query;
  }

  load(): void {
    this.load$.next(this.snapshot.query);
  }

  /** Replaces the whole query (e.g. from the URL). Loads only if something changed. */
  applyQuery(query: StudentQuery): void {
    this.updateQuery(query);
  }

  setSearch(search: string): void {
    this.updateQuery({ search: search.trim(), page: 0 });
  }

  setCourse(courseId: number | null): void {
    this.updateQuery({ courseId, page: 0 });
  }

  setStatus(status: StudentStatusFilter): void {
    this.updateQuery({ status, page: 0 });
  }

  clearFilters(): void {
    this.updateQuery({ search: '', courseId: null, status: 'all', page: 0 });
  }

  setPage(page: number, size: number): void {
    this.updateQuery({ page, size });
  }

  setSort(sort: StudentSortField, direction: SortDirection): void {
    this.updateQuery({ sort, direction, page: 0 });
  }

  /** CSV download of everything matching the current filters and sort. */
  exportCsv(): Observable<HttpResponse<Blob>> {
    return this.api.exportCsv(this.snapshot.query);
  }

  // ---------- single student ----------

  /** Fetches a student and makes it the selected one. */
  loadStudent(id: number): Observable<StudentDetail> {
    return this.api.get(id).pipe(tap((student) => this.patch({ selected: student })));
  }

  /** Live view of one student: emits again whenever the store's copy changes. */
  student$(id: number): Observable<StudentDetail> {
    return this.selected$.pipe(filter((s): s is StudentDetail => s?.id === id));
  }

  // ---------- writes ----------

  create(request: StudentRequest): Observable<StudentDetail> {
    return this.api.create(request).pipe(
      tap((student) => {
        this.patch({ selected: student });
        this.events.emit({ type: 'student-saved', student });
        if (this.snapshot.page) {
          this.load(); // the new row may belong on the page currently shown
        }
      }),
    );
  }

  update(id: number, request: StudentRequest): Observable<StudentDetail> {
    return this.api.update(id, request).pipe(
      tap((student) => {
        this.applySaved(student);
        this.events.emit({ type: 'student-saved', student });
      }),
    );
  }

  updateEnrollments(id: number, courseIds: number[]): Observable<StudentDetail> {
    return this.api.replaceCourses(id, courseIds).pipe(
      tap((student) => {
        this.applySaved(student);
        this.events.emit({ type: 'enrollments-changed', studentId: id, student });
      }),
    );
  }

  /** Enrolls one student in one course (used from the course page). */
  enroll(studentId: number, courseId: number): Observable<StudentDetail> {
    return this.api.enroll(studentId, courseId).pipe(
      tap((student) => {
        this.applySaved(student);
        this.events.emit({ type: 'enrollments-changed', studentId, student });
      }),
    );
  }

  unenroll(studentId: number, courseId: number): Observable<void> {
    return this.api.unenroll(studentId, courseId).pipe(
      tap(() => {
        this.events.emit({ type: 'enrollments-changed', studentId });
        this.refresh();
      }),
    );
  }

  /** Hides a row immediately (undo window). */
  hide(id: number): void {
    this.patch({ hiddenIds: [...this.snapshot.hiddenIds, id] });
  }

  /** Shows a hidden row again (undo pressed or delete failed). */
  unhide(id: number): void {
    this.patch({ hiddenIds: this.snapshot.hiddenIds.filter((hidden) => hidden !== id) });
  }

  remove(id: number): Observable<void> {
    return this.api.delete(id).pipe(
      tap(() => {
        const { page, selected } = this.snapshot;
        this.patch({
          hiddenIds: this.snapshot.hiddenIds.filter((hidden) => hidden !== id),
          selected: selected?.id === id ? null : selected,
          page: page && { ...page, content: page.content.filter((row) => row.id !== id) },
        });
        this.events.emit({ type: 'student-deleted', id });
        if (page) {
          // Step back a page if we just removed the only row on the last page.
          const emptiedPage = page.content.length === 1 && page.page > 0;
          this.updateQuery({ page: emptiedPage ? page.page - 1 : page.page }, true);
        }
      }),
    );
  }

  /** Re-fetches whatever is on screen (current list page and open student). */
  refresh(): void {
    const { page, selected } = this.snapshot;
    if (page) {
      this.load();
    }
    if (selected) {
      this.api.get(selected.id).subscribe({
        next: (student) => this.patch({ selected: student }),
        error: () => this.patch({ selected: null }),
      });
    }
  }

  // ---------- internals ----------

  /** Patch the cached list row in place so the list is current without a refetch. */
  private applySaved(student: StudentDetail): void {
    const page = this.snapshot.page;
    const summary = toSummary(student);
    this.patch({
      selected:
        this.snapshot.selected?.id === student.id || !this.snapshot.selected
          ? student
          : this.snapshot.selected,
      page: page && {
        ...page,
        content: page.content.map((row) => (row.id === student.id ? summary : row)),
      },
    });
  }

  private updateQuery(changes: Partial<StudentQuery>, force = false): void {
    const query = { ...this.snapshot.query, ...changes };
    if (!force && this.snapshot.page && sameQuery(query, this.snapshot.query)) {
      return;
    }
    this.patch({ query });
    this.load$.next(query);
  }

  private select<T>(project: (state: StudentState) => T): Observable<T> {
    return this.state.pipe(map(project), distinctUntilChanged());
  }

  private get snapshot(): StudentState {
    return this.state.getValue();
  }

  private patch(changes: Partial<StudentState>): void {
    this.state.next({ ...this.snapshot, ...changes });
  }
}

function toSummary({ courses: _courses, ...summary }: StudentDetail): Student {
  return summary;
}

export function sameQuery(a: StudentQuery, b: StudentQuery): boolean {
  return (
    a.search === b.search &&
    a.courseId === b.courseId &&
    a.status === b.status &&
    a.page === b.page &&
    a.size === b.size &&
    a.sort === b.sort &&
    a.direction === b.direction
  );
}
