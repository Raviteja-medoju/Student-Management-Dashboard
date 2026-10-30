import { ErrorHandler, Injectable, Injector, inject } from '@angular/core';
import { isApiError } from './models/api-error.model';
import { NotificationService } from './services/notification.service';

/**
 * Last line of defence for errors nobody caught (e.g. a bug in a template).
 * HTTP errors were already shown by the error interceptor, so they are only logged.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  // Resolved lazily: ErrorHandler is created very early, before Material services.
  private readonly injector = inject(Injector);

  handleError(error: unknown): void {
    console.error(error);
    if (!isApiError(error)) {
      this.injector
        .get(NotificationService)
        .error('Something went wrong. Please refresh the page.');
    }
  }
}
