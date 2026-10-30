import { AsyncPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  distinctUntilChanged,
  filter,
  map,
  of,
  shareReplay,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { ApiError } from '../../../core/models/api-error.model';
import { StudentDetail } from '../../../core/models/student.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UndoService } from '../../../core/services/undo.service';
import { CourseStore } from '../../../core/state/course.store';
import { StudentStore } from '../../../core/state/student.store';
import { AvatarComponent } from '../../../shared/avatar/avatar.component';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { ageFrom } from '../../../shared/utils/date';
import { queryToParams } from '../student-list/student-query-params';
import { CoursesTabComponent } from './courses-tab/courses-tab.component';
import { ProfileTabComponent } from './profile-tab/profile-tab.component';
import { SummaryTabComponent } from './summary-tab/summary-tab.component';

export type TabId = 'profile' | 'courses' | 'summary';

type DetailState =
  | { status: 'loading' }
  | { status: 'create' }
  | { status: 'ready'; student: StudentDetail }
  | { status: 'not-found'; message: string }
  | { status: 'error'; message: string };

const TAB_IDS: TabId[] = ['profile', 'courses', 'summary'];

/**
 * Container for one student. Owns the tab bar and the "unsaved changes" rules:
 *  - passes the student down to each tab (inputs),
 *  - hears back from tabs through `saved` and `dirtyChange` (outputs),
 *  - asks before switching away from a tab with unsaved edits,
 *  - tells the route guard and the browser whether leaving would lose work.
 *
 * The tab bar is a Material tab *nav* whose active tab is fully controlled here,
 * so a switch can be confirmed before it happens instead of being undone after.
 */
@Component({
  selector: 'app-student-detail',
  imports: [
    AsyncPipe,
    RouterLink,
    MatTabsModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    AvatarComponent,
    EmptyStateComponent,
    ProfileTabComponent,
    CoursesTabComponent,
    SummaryTabComponent,
  ],
  templateUrl: './student-detail.component.html',
  styleUrl: './student-detail.component.scss',
  host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
})
export class StudentDetailComponent implements HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(StudentStore);
  private readonly courseStore = inject(CourseStore);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  private readonly title = inject(Title);
  private readonly undo = inject(UndoService);

  protected readonly tabs: { id: TabId; label: string; icon: string }[] = [
    { id: 'profile', label: 'Profile', icon: 'badge' },
    { id: 'courses', label: 'Courses', icon: 'menu_book' },
    { id: 'summary', label: 'Summary', icon: 'insights' },
  ];

  protected readonly activeTab = signal<TabId>(this.initialTab());
  protected readonly dirtyTabs = signal<Record<TabId, boolean>>(cleanTabs());
  protected readonly courses$ = this.courseStore.courses$;
  protected readonly coursesLoading$ = this.courseStore.loading$;
  protected readonly ageFrom = ageFrom;
  /** "All students" goes back to the list with the same search, filters and page. */
  protected readonly listParams = queryToParams(this.store.currentQuery);

  /** Route param -> student. switchMap drops the previous student if the id changes. */
  protected readonly state$: Observable<DetailState> = this.route.paramMap.pipe(
    map((params) => params.get('id')),
    distinctUntilChanged(),
    tap(() => this.dirtyTabs.set(cleanTabs())),
    switchMap((id): Observable<DetailState> => {
      if (id === null) {
        return of({ status: 'create' });
      }
      const studentId = Number(id);
      if (!Number.isInteger(studentId) || studentId <= 0) {
        return of({ status: 'not-found', message: `"${id}" is not a valid student id.` });
      }
      return this.store.loadStudent(studentId).pipe(
        switchMap(() => this.store.student$(studentId)),
        tap((student) => this.title.setTitle(`${student.fullName} · Student Management`)),
        map((student): DetailState => ({ status: 'ready', student })),
        startWith<DetailState>({ status: 'loading' }),
        catchError((error: ApiError) =>
          of<DetailState>(
            error.status === 404
              ? { status: 'not-found', message: error.message }
              : { status: 'error', message: error.message },
          ),
        ),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  constructor() {
    this.courseStore.ensureLoaded();
  }

  // ---------- tabs ----------

  protected selectTab(next: TabId, isCreate = false): void {
    const current = this.activeTab();
    if (next === current || (isCreate && next !== 'profile')) {
      return;
    }
    if (!this.dirtyTabs()[current]) {
      this.activate(next);
      return;
    }
    this.confirm
      .discardChanges()
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.setDirty(current, false); // the tab is destroyed on switch, which drops its edits
        this.activate(next);
      });
  }

  protected setDirty(tab: TabId, dirty: boolean): void {
    this.dirtyTabs.update((tabs) => (tabs[tab] === dirty ? tabs : { ...tabs, [tab]: dirty }));
  }

  // ---------- events from child tabs ----------

  protected onProfileSaved(student: StudentDetail, created: boolean): void {
    this.setDirty('profile', false);
    if (created) {
      this.notifications.success(`${student.fullName} was added. Now choose their courses.`);
      this.router.navigate(['/students', student.id], {
        queryParams: { tab: 'courses' },
        replaceUrl: true,
      });
    } else {
      this.notifications.success('Profile saved');
    }
  }

  protected onCoursesSaved(student: StudentDetail): void {
    this.setDirty('courses', false);
    const count = student.enrolledCourseCount;
    this.notifications.success(
      `Enrollment saved: ${count} course${count === 1 ? '' : 's'}, ${student.totalCredits} credits`,
    );
  }

  // ---------- page actions ----------

  /** Confirm, go back to the list, then delete with a few seconds to undo. */
  protected delete(student: StudentDetail): void {
    const courses = student.enrolledCourseCount;
    this.confirm
      .confirm({
        title: `Delete ${student.fullName}?`,
        message:
          courses > 0
            ? `This also removes their ${courses} course enrollment${courses === 1 ? '' : 's'}. You can undo for a few seconds.`
            : 'You can undo for a few seconds.',
        confirmText: 'Delete student',
        tone: 'danger',
      })
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.dirtyTabs.set(cleanTabs()); // nothing left to save
        this.router.navigate(['/students'], { queryParams: this.listParams }).then(() =>
          this.undo.run({
            message: `${student.fullName} deleted`,
            hide: () => this.store.hide(student.id),
            restore: () => this.store.unhide(student.id),
            commit: () => this.store.remove(student.id),
          }),
        );
      });
  }

  /** Used by unsavedChangesGuard when navigating away inside the app. */
  hasUnsavedChanges(): boolean {
    return Object.values(this.dirtyTabs()).some(Boolean);
  }

  /** Browser refresh/close: show the native "leave site?" prompt if work would be lost. */
  protected onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges()) {
      event.preventDefault();
    }
  }

  private activate(tab: TabId): void {
    this.activeTab.set(tab);
    // Keep the tab in the URL so it survives refresh and can be shared.
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tab === 'profile' ? null : tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private initialTab(): TabId {
    const tab = this.route.snapshot.queryParamMap.get('tab') as TabId | null;
    return tab && TAB_IDS.includes(tab) ? tab : 'profile';
  }
}

function cleanTabs(): Record<TabId, boolean> {
  return { profile: false, courses: false, summary: false };
}
