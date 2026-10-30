import { Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { StudentApiService } from '../../../../core/api/student-api.service';
import { ApiError } from '../../../../core/models/api-error.model';
import {
  STUDENT_RULES,
  StudentDetail,
  StudentRequest,
} from '../../../../core/models/student.model';
import { StudentStore } from '../../../../core/state/student.store';
import { errorMessage } from '../../../../shared/forms/error-message';
import { emailAvailableValidator } from '../../../../shared/forms/email-available.validator';
import { applyServerErrors } from '../../../../shared/forms/server-errors';
import { notBlankValidator, pastDateValidator } from '../../../../shared/forms/validators';
import { fromIsoDate, toIsoDate } from '../../../../shared/utils/date';

interface ProfileFormValue {
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: Date | null;
}

/**
 * Profile form (create and edit). Reactive, typed, with validators that mirror the backend.
 * Input: the student (null when creating). Outputs: `saved` and `dirtyChange` for the parent.
 */
@Component({
  selector: 'app-profile-tab',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './profile-tab.component.html',
  styleUrl: './profile-tab.component.scss',
})
export class ProfileTabComponent {
  readonly student = input<StudentDetail | null>(null);
  readonly saved = output<StudentDetail>();
  readonly dirtyChange = output<boolean>();

  private readonly store = inject(StudentStore);
  private readonly api = inject(StudentApiService);
  protected readonly rules = STUDENT_RULES;
  /** Live result of the "is this email free?" check, shown under the field. */
  protected readonly emailStatus = signal<'idle' | 'checking' | 'available' | 'taken'>('idle');
  protected readonly errorMessage = errorMessage;
  protected readonly saving = signal(false);
  protected readonly changed = signal(false);
  protected readonly maxDate = yesterday();
  protected readonly startAt = new Date(new Date().getFullYear() - 20, 0, 1);

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: [
      '',
      [Validators.required, notBlankValidator, Validators.maxLength(STUDENT_RULES.nameMaxLength)],
    ],
    lastName: [
      '',
      [Validators.required, notBlankValidator, Validators.maxLength(STUDENT_RULES.nameMaxLength)],
    ],
    email: [
      '',
      [Validators.required, Validators.email, Validators.maxLength(STUDENT_RULES.emailMaxLength)],
      [
        emailAvailableValidator(this.api, () => ({
          id: this.student()?.id ?? null,
          email: this.student()?.email ?? null,
        })),
      ],
    ],
    dateOfBirth: [null as Date | null, [Validators.required, pastDateValidator]],
  });

  /** Fingerprint of the last saved values; "dirty" means the form differs from it. */
  private savedFingerprint = fingerprint(this.form.getRawValue());

  constructor() {
    // Whenever the parent hands us a (new version of the) student, show its values.
    // untracked: the reset itself reads signals that must not re-trigger this effect.
    effect(() => {
      const student = this.student();
      untracked(() => this.resetTo(student));
    });

    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.refreshChanged());

    // Turn the email control's async status into a signal, so the hint updates (zoneless).
    // "Available" is shown only right after a real server check, never for unchanged values.
    const email = this.form.controls.email;
    let previous = email.status;
    email.statusChanges.pipe(takeUntilDestroyed()).subscribe((status) => {
      this.emailStatus.set(
        status === 'PENDING'
          ? 'checking'
          : email.hasError('emailTaken')
            ? 'taken'
            : status === 'VALID' && previous === 'PENDING'
              ? 'available'
              : 'idle',
      );
      if (email.hasError('emailTaken')) {
        email.markAsTouched(); // show the server's answer now, not only after leaving the field
      }
      previous = status;
    });
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
    const request: StudentRequest = {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      email: value.email.trim(),
      dateOfBirth: toIsoDate(value.dateOfBirth!),
    };
    const existing = this.student();
    const save$ = existing ? this.store.update(existing.id, request) : this.store.create(request);

    this.saving.set(true);
    save$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (student) => {
        // Clear "dirty" before telling the parent, so a follow-up navigation isn't blocked.
        this.savedFingerprint = fingerprint(this.form.getRawValue());
        this.form.markAsPristine();
        this.refreshChanged();
        this.saved.emit(student);
      },
      // 400 field errors / 409 duplicate email land on the matching field.
      error: (error: ApiError) => applyServerErrors(this.form, error),
    });
  }

  protected discard(): void {
    this.resetTo(this.student());
  }

  private resetTo(student: StudentDetail | null): void {
    const value: ProfileFormValue = {
      firstName: student?.firstName ?? '',
      lastName: student?.lastName ?? '',
      email: student?.email ?? '',
      dateOfBirth: student ? fromIsoDate(student.dateOfBirth) : null,
    };
    this.savedFingerprint = fingerprint(value);
    this.form.reset(value);
    this.refreshChanged();
  }

  private refreshChanged(): void {
    const changed = fingerprint(this.form.getRawValue()) !== this.savedFingerprint;
    if (changed !== this.changed()) {
      this.changed.set(changed);
      this.dirtyChange.emit(changed);
    }
  }
}

function fingerprint(value: ProfileFormValue): string {
  return JSON.stringify([
    value.firstName.trim(),
    value.lastName.trim(),
    value.email.trim().toLowerCase(),
    value.dateOfBirth ? toIsoDate(value.dateOfBirth) : null,
  ]);
}

function yesterday(): Date {
  const date = new Date();
  date.setDate(date.getDate() - 1);
  return date;
}
