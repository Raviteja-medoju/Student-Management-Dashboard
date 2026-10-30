import { AbstractControl, AsyncValidatorFn, ValidationErrors } from '@angular/forms';
import { Observable, catchError, map, of, switchMap, timer } from 'rxjs';
import { StudentApiService } from '../../core/api/student-api.service';

/**
 * Async validator: asks the API whether an email is free while the user types.
 * - waits 400ms after the last keystroke (Angular cancels the previous check on each change,
 *   so the timer works as a debounce),
 * - skips the call when the email is the student's own current email,
 * - never blocks saving if the check itself fails (the server validates again on save).
 */
export function emailAvailableValidator(
  api: StudentApiService,
  current: () => { id: number | null; email: string | null },
): AsyncValidatorFn {
  return (control: AbstractControl<string>): Observable<ValidationErrors | null> => {
    const email = (control.value ?? '').trim().toLowerCase();
    const { id, email: ownEmail } = current();
    if (!email || email === ownEmail?.toLowerCase()) {
      return of(null);
    }
    return timer(400).pipe(
      switchMap(() => api.checkEmail(email, id)),
      map((result) => (result.available ? null : { emailTaken: true })),
      catchError(() => of(null)),
    );
  };
}
