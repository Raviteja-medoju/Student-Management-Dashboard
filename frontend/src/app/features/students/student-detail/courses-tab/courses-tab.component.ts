import {
  CdkDrag,
  CdkDragDrop,
  CdkDragPlaceholder,
  CdkDropList,
  CdkDropListGroup,
} from '@angular/cdk/drag-drop';
import { Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Course } from '../../../../core/models/course.model';
import { StudentDetail } from '../../../../core/models/student.model';
import { CourseStore } from '../../../../core/state/course.store';
import { StudentStore } from '../../../../core/state/student.store';
import { EmptyStateComponent } from '../../../../shared/empty-state/empty-state.component';
import { SeatMeterComponent } from '../../../../shared/seat-meter/seat-meter.component';

/**
 * Enrollment board: drag courses between "Available" and "Enrolled" (or use the +/- buttons,
 * which also work from the keyboard). The selection is local until "Save enrollment", which
 * replaces the student's whole course set in one all-or-nothing API call.
 */
@Component({
  selector: 'app-courses-tab',
  imports: [
    RouterLink,
    CdkDropListGroup,
    CdkDropList,
    CdkDrag,
    CdkDragPlaceholder,
    MatTooltipModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    EmptyStateComponent,
    SeatMeterComponent,
  ],
  templateUrl: './courses-tab.component.html',
  styleUrl: './courses-tab.component.scss',
})
export class CoursesTabComponent {
  readonly student = input.required<StudentDetail>();
  readonly courses = input.required<Course[]>();
  readonly loading = input(false);
  readonly saved = output<StudentDetail>();
  readonly dirtyChange = output<boolean>();

  private readonly store = inject(StudentStore);
  private readonly courseStore = inject(CourseStore);

  protected readonly filter = signal('');
  protected readonly saving = signal(false);

  /** Course ids the student is enrolled in on the server. */
  private readonly enrolledIds = computed(
    () => new Set(this.student().courses.map((c) => c.courseId)),
  );

  /** Working selection; resets automatically whenever a new version of the student arrives. */
  protected readonly selectedIds = linkedSignal(() => new Set(this.enrolledIds()));

  protected readonly added = computed(() =>
    this.courses().filter((c) => this.selectedIds().has(c.id) && !this.enrolledIds().has(c.id)),
  );
  protected readonly removed = computed(() =>
    this.courses().filter((c) => !this.selectedIds().has(c.id) && this.enrolledIds().has(c.id)),
  );
  protected readonly hasChanges = computed(
    () => this.added().length > 0 || this.removed().length > 0,
  );
  protected readonly totalCredits = computed(() =>
    this.courses()
      .filter((c) => this.selectedIds().has(c.id))
      .reduce((sum, c) => sum + c.credits, 0),
  );
  /** Right column: the working selection. */
  protected readonly selectedCourses = computed(() =>
    this.courses().filter((c) => this.selectedIds().has(c.id)),
  );

  /** Left column: everything not selected, narrowed by the filter box. */
  protected readonly availableCourses = computed(() => {
    const term = this.filter().trim().toLowerCase();
    return this.courses().filter(
      (c) =>
        !this.selectedIds().has(c.id) &&
        (!term || c.code.toLowerCase().includes(term) || c.title.toLowerCase().includes(term)),
    );
  });

  /** Full courses can't be dropped into "Enrolled" (unless the student is already in them). */
  protected readonly canEnroll = (drag: CdkDrag<Course>): boolean => !this.isLocked(drag.data);

  private reportedDirty = false;

  constructor() {
    // Also catch changes that come from a new student input (the selection resets itself).
    toObservable(this.hasChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.reportDirty());
  }

  /** A full course can't be newly picked, but a student already in it can stay. */
  protected isLocked(course: Course): boolean {
    return course.availableSeats === 0 && !this.enrolledIds().has(course.id);
  }

  protected toggle(course: Course): void {
    if (this.isLocked(course)) {
      return;
    }
    this.selectedIds.update((ids) => {
      const next = new Set(ids);
      if (next.has(course.id)) {
        next.delete(course.id);
      } else {
        next.add(course.id);
      }
      return next;
    });
    this.reportDirty();
  }

  protected isNew(course: Course): boolean {
    return this.selectedIds().has(course.id) && !this.enrolledIds().has(course.id);
  }

  protected isDropped(course: Course): boolean {
    return !this.selectedIds().has(course.id) && this.enrolledIds().has(course.id);
  }

  /** Moving a card to the other column toggles it; reordering within a column does nothing. */
  protected onDrop(event: CdkDragDrop<Course[], Course[], Course>): void {
    if (event.previousContainer !== event.container) {
      this.toggle(event.item.data);
    }
  }

  protected reset(): void {
    this.selectedIds.set(new Set(this.enrolledIds()));
    this.reportDirty();
  }

  protected save(): void {
    if (!this.hasChanges() || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.store
      .updateEnrollments(this.student().id, [...this.selectedIds()])
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (student) => this.saved.emit(student),
        // e.g. 409 "course is full": seats changed since we loaded them; refresh the numbers.
        error: () => this.courseStore.load(),
      });
  }

  /**
   * Child -> parent: report when "has unsaved changes" flips. Called synchronously from user
   * actions, so the parent (and the route guard) always see the current state, even on a fast click.
   */
  private reportDirty(): void {
    const dirty = this.hasChanges();
    if (dirty !== this.reportedDirty) {
      this.reportedDirty = dirty;
      this.dirtyChange.emit(dirty);
    }
  }
}
