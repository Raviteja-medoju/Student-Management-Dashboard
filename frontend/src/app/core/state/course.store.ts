import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  BehaviorSubject,
  Observable,
  Subject,
  catchError,
  distinctUntilChanged,
  filter,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { CourseApiService } from '../api/course-api.service';
import { ApiError } from '../models/api-error.model';
import { Course, CourseDetail, CourseRequest } from '../models/course.model';
import { DataEvents } from './data-events';

export interface CourseState {
  courses: Course[];
  /** Courses deleted but still inside the undo window. */
  hiddenIds: number[];
  loaded: boolean;
  loading: boolean;
  error: string | null;
}

const initialState: CourseState = {
  courses: [],
  hiddenIds: [],
  loaded: false,
  loading: false,
  error: null,
};

/**
 * Single source of truth for the course catalogue (small enough to keep fully in memory).
 * Seat counts refresh automatically when enrollments change anywhere in the app.
 */
@Injectable({ providedIn: 'root' })
export class CourseStore {
  private readonly api = inject(CourseApiService);
  private readonly events = inject(DataEvents);
  private readonly state = new BehaviorSubject<CourseState>(initialState);
  private readonly load$ = new Subject<void>();

  readonly courses$ = this.select((s) => s.courses.filter((c) => !s.hiddenIds.includes(c.id)));
  readonly loaded$ = this.select((s) => s.loaded);
  readonly loading$ = this.select((s) => s.loading);
  readonly error$ = this.select((s) => s.error);

  constructor() {
    const destroyRef = inject(DestroyRef);

    this.load$
      .pipe(
        tap(() => this.patch({ loading: true, error: null })),
        switchMap(() =>
          this.api.list().pipe(
            map((courses) => ({ courses, loaded: true, error: null })),
            catchError((error: ApiError) => of({ error: error.message })),
          ),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => this.patch({ loading: false, ...result }));

    // Enrollment changes and student deletions change seat counts.
    this.events.changes$
      .pipe(
        filter((e) => e.type === 'enrollments-changed' || e.type === 'student-deleted'),
        filter(() => this.snapshot.loaded),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.load());
  }

  load(): void {
    this.load$.next();
  }

  /** Loads the catalogue once; later calls are no-ops until something changes. */
  ensureLoaded(): void {
    if (!this.snapshot.loaded && !this.snapshot.loading) {
      this.load();
    }
  }

  create(request: CourseRequest): Observable<CourseDetail> {
    return this.api.create(request).pipe(tap((course) => this.applySaved(course)));
  }

  update(id: number, request: CourseRequest): Observable<CourseDetail> {
    return this.api.update(id, request).pipe(tap((course) => this.applySaved(course)));
  }

  hide(id: number): void {
    this.patch({ hiddenIds: [...this.snapshot.hiddenIds, id] });
  }

  unhide(id: number): void {
    this.patch({ hiddenIds: this.snapshot.hiddenIds.filter((hidden) => hidden !== id) });
  }

  remove(id: number): Observable<void> {
    return this.api.delete(id).pipe(
      tap(() => {
        this.patch({
          courses: this.snapshot.courses.filter((c) => c.id !== id),
          hiddenIds: this.snapshot.hiddenIds.filter((hidden) => hidden !== id),
        });
        this.events.emit({ type: 'course-deleted', id });
      }),
    );
  }

  private applySaved(detail: CourseDetail): void {
    const { students: _students, ...course } = detail;
    const others = this.snapshot.courses.filter((c) => c.id !== course.id);
    this.patch({ courses: [...others, course].sort((a, b) => a.code.localeCompare(b.code)) });
    this.events.emit({ type: 'course-saved', course: detail });
  }

  private select<T>(project: (state: CourseState) => T): Observable<T> {
    return this.state.pipe(map(project), distinctUntilChanged());
  }

  private get snapshot(): CourseState {
    return this.state.getValue();
  }

  private patch(changes: Partial<CourseState>): void {
    this.state.next({ ...this.snapshot, ...changes });
  }
}
