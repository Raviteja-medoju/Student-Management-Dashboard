import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, ErrorHandler, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import {
  ActivatedRouteSnapshot,
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withViewTransitions,
} from '@angular/router';
import { routes } from './app.routes';
import { GlobalErrorHandler } from './core/global-error-handler';
import { errorInterceptor } from './core/http/error.interceptor';
import { loadingInterceptor } from './core/http/loading.interceptor';
import { AppTitleStrategy } from './core/title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
      withViewTransitions({
        skipInitialTransition: true,
        // Cross-fade only between pages, not for search/filter/tab changes on the same page.
        onViewTransitionCreated: ({ transition, from, to }) => {
          if (pathOf(from) === pathOf(to)) {
            transition.skipTransition();
          }
        },
      }),
    ),
    // Order matters: loading wraps everything, then errors are normalized.
    provideHttpClient(withInterceptors([loadingInterceptor, errorInterceptor])),
    provideNativeDateAdapter(),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    { provide: TitleStrategy, useClass: AppTitleStrategy },
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: { appearance: 'outline', subscriptSizing: 'dynamic' },
    },
  ],
};

/** The URL path of a route snapshot (without query params), e.g. "students/6". */
function pathOf(snapshot: ActivatedRouteSnapshot): string {
  const segments: string[] = [];
  for (let route: ActivatedRouteSnapshot | null = snapshot; route; route = route.firstChild) {
    segments.push(...route.url.map((segment) => segment.path));
  }
  return segments.join('/');
}
