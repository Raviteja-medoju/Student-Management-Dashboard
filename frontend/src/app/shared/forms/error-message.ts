import { AbstractControl } from '@angular/forms';

/** Turns a control's first error into a readable sentence for <mat-error>. */
export function errorMessage(control: AbstractControl | null, label: string): string {
  const errors = control?.errors;
  if (!errors) {
    return '';
  }
  if (errors['server']) return errors['server'];
  if (errors['emailTaken']) return 'This email is already used by another student';
  if (errors['required']) return `${label} is required`;
  if (errors['email']) return 'Enter a valid email address';
  if (errors['maxlength'])
    return `${label} must be at most ${errors['maxlength'].requiredLength} characters`;
  if (errors['min']) return `${label} must be at least ${errors['min'].min}`;
  if (errors['max']) return `${label} must be at most ${errors['max'].max}`;
  if (errors['pastDate']) return `${label} must be in the past`;
  if (errors['matDatepickerParse']) return 'Enter a valid date';
  if (errors['matDatepickerMax']) return `${label} must be in the past`;
  if (errors['pattern']) return `${label} has an invalid format`;
  return `${label} is invalid`;
}
