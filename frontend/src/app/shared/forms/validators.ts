import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Mirrors the backend's @Past: the date must be before today. */
export const pastDateValidator: ValidatorFn = (
  control: AbstractControl<Date | null>,
): ValidationErrors | null => {
  const value = control.value;
  if (!value) {
    return null;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return value.getTime() < today.getTime() ? null : { pastDate: true };
};

/** Rejects values that are only whitespace (Validators.required lets "   " through). */
export const notBlankValidator: ValidatorFn = (
  control: AbstractControl<string | null>,
): ValidationErrors | null => {
  const value = control.value;
  return typeof value === 'string' && value.length > 0 && value.trim().length === 0
    ? { required: true }
    : null;
};
