import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  catchError,
  concatMap,
  debounceTime,
  distinctUntilChanged,
  finalize,
  from,
  map,
  of,
  startWith,
  switchMap,
  toArray,
} from 'rxjs';
import { StudentApiService } from '../../../core/api/student-api.service';
import { CourseDetail } from '../../../core/models/course.model';
import { DEFAULT_STUDENT_QUERY, StudentStore } from '../../../core/state/student.store';
import { Student } from '../../../core/models/student.model';
import { AvatarComponent } from '../../../shared/avatar/avatar.component';

export interface EnrollStudentsDialogData {
  course: CourseDetail;
}

/** Result: how many students were enrolled, and which ones failed (e.g. the course filled up). */
export interface EnrollStudentsResult {
  enrolled: number;
  failed: string[];
}

/**
 * Pick students with a live search (debounced, switchMap-cancelled) and enroll them in this course.
 * The picker never offers students already on the roster, and stops at the number of free seats.
 */
@Component({
  selector: 'app-enroll-students-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatAutocompleteModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    AvatarComponent,
  ],
  templateUrl: './enroll-students-dialog.component.html',
  styleUrl: './enroll-students-dialog.component.scss',
})
export class EnrollStudentsDialogComponent {
  private readonly api = inject(StudentApiService);
  private readonly store = inject(StudentStore);
  private readonly dialogRef =
    inject<MatDialogRef<EnrollStudentsDialogComponent, EnrollStudentsResult>>(MatDialogRef);
  protected readonly course = inject<EnrollStudentsDialogData>(MAT_DIALOG_DATA).course;

  protected readonly searchControl = new FormControl<string | Student>('', { nonNullable: true });
  protected readonly picked = signal<Student[]>([]);
  protected readonly saving = signal(false);
  protected readonly searching = signal(false);

  private readonly rosterIds = new Set(this.course.students.map((s) => s.studentId));
  protected readonly seatsLeft = computed(() => this.course.availableSeats - this.picked().length);

  /** Suggestions: debounce typing, cancel stale requests, hide students already chosen or enrolled. */
  private readonly results = toSignal(
    this.searchControl.valueChanges.pipe(
      startWith(''),
      map((value) => (typeof value === 'string' ? value.trim() : '')),
      debounceTime(200),
      distinctUntilChanged(),
      switchMap((search) => {
        this.searching.set(true);
        return this.api.list({ ...DEFAULT_STUDENT_QUERY, search, size: 20, sort: 'lastName' }).pipe(
          map((page) => page.content),
          catchError(() => of([] as Student[])),
          finalize(() => this.searching.set(false)),
        );
      }),
      takeUntilDestroyed(),
    ),
    { initialValue: [] as Student[] },
  );

  protected readonly suggestions = computed(() => {
    const pickedIds = new Set(this.picked().map((s) => s.id));
    return this.results().filter((s) => !this.rosterIds.has(s.id) && !pickedIds.has(s.id));
  });

  protected displayNothing = () => '';

  protected pick(event: MatAutocompleteSelectedEvent): void {
    if (this.seatsLeft() > 0) {
      this.picked.update((list) => [...list, event.option.value as Student]);
    }
    this.searchControl.setValue('');
  }

  protected unpick(student: Student): void {
    this.picked.update((list) => list.filter((s) => s.id !== student.id));
  }

  /** One request per student, in order; keeps going if one fails and reports it at the end. */
  protected enroll(): void {
    if (!this.picked().length || this.saving()) {
      return;
    }
    this.saving.set(true);
    from(this.picked())
      .pipe(
        concatMap((student) =>
          this.store.enroll(student.id, this.course.id).pipe(
            map(() => null),
            catchError(() => of(student.fullName)),
          ),
        ),
        toArray(),
        finalize(() => this.saving.set(false)),
      )
      .subscribe((outcomes) => {
        const failed = outcomes.filter((name): name is string => name !== null);
        this.dialogRef.close({ enrolled: outcomes.length - failed.length, failed });
      });
  }
}
