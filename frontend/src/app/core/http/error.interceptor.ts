import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiError, isApiError } from '../models/api-error.model';
import { NotificationService } from '../services/notification.service';
import { SILENT_ERRORS } from './http-context';

/**
 * Every HTTP error goes through here:
 *  1. normalize it into the backend's ApiError shape (even network failures),
 *  2. show a snackbar (unless the request opted out with SILENT_ERRORS),
 *  3. rethrow the ApiError so forms can map fieldErrors onto their controls.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      const apiError = toApiError(error, req);
      if (!req.context.get(SILENT_ERRORS)) {
        notifications.error(userMessage(apiError));
      }
      return throwError(() => apiError);
    }),
  );
};

export function toApiError(error: unknown, req: HttpRequest<unknown>): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, message: 'Something went wrong. Please try again.', path: req.url };
  }
  if (error.status === 0) {
    return {
      status: 0,
      message: 'Cannot reach the server. Is the backend running on port 8080?',
      path: req.url,
    };
  }
  if (isApiError(error.error)) {
    return error.error;
  }
  return {
    status: error.status,
    error: error.statusText,
    message: fallbackMessage(error.status),
    path: req.url,
  };
}

function userMessage(error: ApiError): string {
  if (error.status === 400 && error.fieldErrors?.length) {
    return 'Please fix the highlighted fields and try again.';
  }
  return error.message;
}

function fallbackMessage(status: number): string {
  if (status === 404) return 'The requested item was not found.';
  if (status >= 500) return 'The server had a problem. Please try again in a moment.';
  return `Request failed (HTTP ${status}).`;
}
