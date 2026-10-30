import { FormGroup } from '@angular/forms';
import { ApiError } from '../../core/models/api-error.model';

/**
 * Puts backend field errors (400 validation, 409 duplicate) onto the matching form controls
 * as a `server` error, so they show in the same <mat-error> as client-side validation.
 * Returns true if at least one field error was applied.
 */
export function applyServerErrors(form: FormGroup, error: ApiError): boolean {
  let applied = false;
  for (const fieldError of error.fieldErrors ?? []) {
    const control = form.get(fieldError.field);
    if (control) {
      control.setErrors({ ...control.errors, server: fieldError.message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}
