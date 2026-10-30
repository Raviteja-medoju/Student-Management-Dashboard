import { HttpContext, HttpContextToken } from '@angular/common/http';

/**
 * Set on a request when the caller shows its own error UI (e.g. a "not found" page),
 * so the global error interceptor does not also pop a snackbar.
 */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

export const silentErrors = () => new HttpContext().set(SILENT_ERRORS, true);
