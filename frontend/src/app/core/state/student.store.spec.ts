import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { page, student, studentDetail } from '../../testing/fixtures';
import { errorInterceptor } from '../http/error.interceptor';
import { NotificationService } from '../services/notification.service';
import { DataChange, DataEvents } from './data-events';
import { StudentStore } from './student.store';

describe('StudentStore', () => {
  let store: StudentStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { error: vi.fn(), success: vi.fn() } },
      ],
    });
    store = TestBed.inject(StudentStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the first page with the default query', async () => {
    store.load();

    const req = http.expectOne((r) => r.url === '/api/students');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('lastName');
    expect(req.request.params.get('direction')).toBe('asc');
    expect(req.request.params.has('search')).toBe(false);
    expect(req.request.params.has('courseId')).toBe(false);
    expect(req.request.params.has('enrolled')).toBe(false);
    expect(await firstValueFrom(store.loading$)).toBe(true);

    req.flush(page([student(1, 'Ada')]));

    expect(await firstValueFrom(store.students$)).toHaveLength(1);
    expect(await firstValueFrom(store.total$)).toBe(1);
    expect(await firstValueFrom(store.loading$)).toBe(false);
  });

  it('cancels a stale search request when a newer one starts (switchMap)', async () => {
    store.setSearch('a');
    const stale = http.expectOne((r) => r.params.get('search') === 'a');

    store.setSearch(' ab ');
    const fresh = http.expectOne((r) => r.params.get('search') === 'ab');

    expect(stale.cancelled).toBe(true);
    fresh.flush(page([student(2, 'Abby')]));
    expect((await firstValueFrom(store.students$)).map((s) => s.firstName)).toEqual(['Abby']);
  });

  it('resets to the first page when the search or sort changes', () => {
    store.setPage(2, 10);
    http.expectOne((r) => r.params.get('page') === '2').flush(page([], 2));

    store.setSort('email', 'desc');

    const req = http.expectOne((r) => r.params.get('sort') === 'email');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('direction')).toBe('desc');
    req.flush(page([]));
  });

  it('turns course and status filters into API parameters', () => {
    store.setCourse(5);
    http.expectOne((r) => r.params.get('courseId') === '5').flush(page([]));

    store.setStatus('fulltime');
    const fullTime = http.expectOne((r) => r.params.get('minCredits') === '12');
    expect(fullTime.request.params.get('courseId')).toBe('5');
    fullTime.flush(page([]));

    store.setStatus('none');
    http.expectOne((r) => r.params.get('enrolled') === 'false').flush(page([]));

    store.clearFilters();
    const cleared = http.expectOne((r) => r.url === '/api/students');
    expect(cleared.request.params.keys().sort()).toEqual(['direction', 'page', 'size', 'sort']);
    cleared.flush(page([]));
  });

  it('hides a row during the undo window and brings it back on undo', async () => {
    store.load();
    http
      .expectOne((r) => r.url === '/api/students')
      .flush(page([student(1, 'Ada'), student(2, 'Alan')], 0, 10, 2));

    store.hide(1);
    expect((await firstValueFrom(store.students$)).map((s) => s.id)).toEqual([2]);
    expect(await firstValueFrom(store.total$)).toBe(1);

    store.unhide(1);
    expect((await firstValueFrom(store.students$)).map((s) => s.id)).toEqual([1, 2]);
    http.expectNone({ method: 'DELETE' });
  });

  it('keeps the current rows and exposes the message when loading fails', async () => {
    store.load();
    http.expectOne((r) => r.url === '/api/students').flush(page([student(1, 'Ada')]));

    store.setPage(1, 10);
    http
      .expectOne((r) => r.params.get('page') === '1')
      .flush(
        { status: 500, message: 'Database is down' },
        { status: 500, statusText: 'Server Error' },
      );

    expect(await firstValueFrom(store.error$)).toBe('Database is down');
    expect(await firstValueFrom(store.students$)).toHaveLength(1);
    expect(await firstValueFrom(store.loading$)).toBe(false);
  });

  it('patches the cached row after an update instead of refetching the list', async () => {
    store.load();
    http
      .expectOne((r) => r.url === '/api/students')
      .flush(page([student(1, 'Ada'), student(2, 'Alan')]));

    const updated = { ...studentDetail(2, 'Alan', [1, 2]), fullName: 'Alan Turing' };
    const result = firstValueFrom(
      store.update(2, {
        firstName: 'Alan',
        lastName: 'Turing',
        email: 'a@b.edu',
        dateOfBirth: '2000-01-01',
      }),
    );
    http.expectOne({ method: 'PUT', url: '/api/students/2' }).flush(updated);
    await result;

    const rows = await firstValueFrom(store.students$);
    expect(rows[1].fullName).toBe('Alan Turing');
    expect(rows[1].enrolledCourseCount).toBe(2);
    expect('courses' in rows[1]).toBe(false);
    expect((await firstValueFrom(store.selected$))?.id).toBe(2);
    http.expectNone((r) => r.method === 'GET' && r.url === '/api/students');
  });

  it('announces enrollment changes so other views (course seats, dashboard) can refresh', async () => {
    const events: DataChange[] = [];
    TestBed.inject(DataEvents).changes$.subscribe((e) => events.push(e));

    const result = firstValueFrom(store.updateEnrollments(3, [10, 20]));
    const req = http.expectOne({ method: 'PUT', url: '/api/students/3/courses' });
    expect(req.request.body).toEqual({ courseIds: [10, 20] });
    req.flush(studentDetail(3, 'Mia', [10, 20]));
    await result;

    expect(events).toEqual([
      expect.objectContaining({ type: 'enrollments-changed', studentId: 3 }),
    ]);
  });
});
