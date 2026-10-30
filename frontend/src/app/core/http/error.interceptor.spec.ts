import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError } from '../models/api-error.model';
import { NotificationService } from '../services/notification.service';
import { errorInterceptor } from './error.interceptor';
import { silentErrors } from './http-context';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  const notifications = { error: vi.fn() };

  beforeEach(() => {
    notifications.error.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notifications },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  function request(options: { silent?: boolean } = {}): { error?: ApiError } {
    const result: { error?: ApiError } = {};
    http
      .get('/api/things', options.silent ? { context: silentErrors() } : {})
      .subscribe({ error: (error: ApiError) => (result.error = error) });
    return result;
  }

  it('passes the backend ApiError through and shows its message', () => {
    const result = request();
    const body: ApiError = {
      status: 409,
      error: 'Conflict',
      message: 'Email already exists',
      path: '/api/things',
    };

    controller.expectOne('/api/things').flush(body, { status: 409, statusText: 'Conflict' });

    expect(result.error).toEqual(body);
    expect(notifications.error).toHaveBeenCalledWith('Email already exists');
  });

  it('shows a generic hint for field validation errors (the form shows the details)', () => {
    const result = request();

    controller.expectOne('/api/things').flush(
      {
        status: 400,
        message: 'Validation failed',
        fieldErrors: [{ field: 'email', message: 'Invalid' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(result.error?.fieldErrors?.[0].field).toBe('email');
    expect(notifications.error).toHaveBeenCalledWith(
      'Please fix the highlighted fields and try again.',
    );
  });

  it('turns a network failure into a friendly status-0 error', () => {
    const result = request();

    controller.expectOne('/api/things').error(new ProgressEvent('error'), { status: 0 });

    expect(result.error?.status).toBe(0);
    expect(result.error?.message).toContain('Cannot reach the server');
  });

  it('builds an ApiError when the body is not one (e.g. a proxy error page)', () => {
    const result = request();

    controller
      .expectOne('/api/things')
      .flush('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' });

    expect(result.error).toEqual(expect.objectContaining({ status: 502, path: '/api/things' }));
    expect(result.error?.message).toContain('server had a problem');
  });

  it('stays quiet when the request opted out with SILENT_ERRORS', () => {
    const result = request({ silent: true });

    controller
      .expectOne('/api/things')
      .flush({ status: 404, message: 'Not found' }, { status: 404, statusText: 'Not Found' });

    expect(result.error?.status).toBe(404);
    expect(notifications.error).not.toHaveBeenCalled();
  });
});
