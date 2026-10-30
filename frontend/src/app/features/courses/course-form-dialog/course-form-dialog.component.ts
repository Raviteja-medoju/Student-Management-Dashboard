import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { filter, finalize, merge } from 'rxjs';
import { ApiError } from '../../../core/models/api-error.model';
import {
  COURSE_RULES,
  Course,
  CourseDetail,
  CourseRequest,
} from '../../../core/models/course.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { CourseStore } from '../../../core/state/course.store';
import { errorMessage } from '../../../shared/forms/error-message';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { notBlankValidator } from '../../../shared/forms/validators';

export interface CourseFormDialogData {
  /** The course to edit, or null to create one. */
  course: Course | null;
}

/** Create/edit a course in a modal. Closing with unsaved edits asks for confirmation first. */
@Component({
  selector: 'app-course-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './course-form-dialog.component.html',
  styleUrl: './course-form-dialog.component.scss',
})
export class CourseFormDialogComponent {
  private readonly data = inject<CourseFormDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<CourseFormDialogComponent, CourseDetail>>(MatDialogRef);
  private readonly store = inject(CourseStore);
  private readonly confirm = inject(ConfirmService);

  protected readonly course = this.data.course;
  protected readonly rules = COURSE_RULES;
  protected readonly errorMessage = errorMessage;
  protected readonly saving = signal(false);
  /** Mirrors the backend rule: capacity can't drop below current enrollment. */
  protected readonly minCapacity = Math.max(
    COURSE_RULES.minCapacity,
    this.course?.enrolledCount ?? 0,
  );

  protected readonly form = inject(NonNullableFormBuilder).group({
    code: [
      this.course?.code ?? '',
      [Validators.required, Validators.pattern(COURSE_RULES.codePattern)],
    ],
    title: [
      this.course?.title ?? '',
      [Validators.required, notBlankValidator, Validators.maxLength(COURSE_RULES.titleMaxLength)],
    ],
    description: [
      this.course?.description ?? '',
      [Validators.maxLength(COURSE_RULES.descriptionMaxLength)],
    ],
    credits: [
      this.course?.credits ?? 3,
      [
        Validators.required,
        Validators.min(COURSE_RULES.minCredits),
        Validators.max(COURSE_RULES.maxCredits),
      ],
    ],
    capacity: [
      this.course?.capacity ?? 30,
      [
        Validators.required,
        Validators.min(this.minCapacity),
        Validators.max(COURSE_RULES.maxCapacity),
      ],
    ],
  });

  constructor() {
    // Escape and backdrop clicks go through cancel(), so edits are never lost silently.
    this.dialogRef.disableClose = true;
    merge(
      this.dialogRef.backdropClick(),
      this.dialogRef.keydownEvents().pipe(filter((event) => event.key === 'Escape')),
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cancel());
  }

  protected cancel(): void {
    if (!this.form.dirty || this.saving()) {
      if (!this.saving()) {
        this.dialogRef.close();
      }
      return;
    }
    this.confirm
      .discardChanges()
      .pipe(filter(Boolean))
      .subscribe(() => this.dialogRef.close());
  }

  protected uppercaseCode(): void {
    const control = this.form.controls.code;
    control.setValue(control.value.trim().toUpperCase());
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const request: CourseRequest = {
      code: value.code.trim().toUpperCase(),
      title: value.title.trim(),
      description: value.description.trim() || null,
      credits: Number(value.credits),
      capacity: Number(value.capacity),
    };
    const save$ = this.course
      ? this.store.update(this.course.id, request)
      : this.store.create(request);

    this.saving.set(true);
    save$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (error: ApiError) => applyServerErrors(this.form, error),
    });
  }
}
