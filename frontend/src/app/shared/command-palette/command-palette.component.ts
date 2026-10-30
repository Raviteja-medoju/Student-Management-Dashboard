import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { StudentApiService } from '../../core/api/student-api.service';
import { Student } from '../../core/models/student.model';
import { ThemeService } from '../../core/services/theme.service';
import { CourseStore } from '../../core/state/course.store';
import { DEFAULT_STUDENT_QUERY } from '../../core/state/student.store';
import { AvatarComponent } from '../avatar/avatar.component';

export interface PaletteItem {
  id: string;
  group: 'Actions' | 'Students' | 'Courses';
  icon: string;
  label: string;
  hint?: string;
  avatar?: string;
  run: () => void;
}

/**
 * Ctrl+K / ⌘K: jump to any page, student or course from anywhere.
 * Students are searched on the server as you type (debounced; switchMap drops stale
 * requests); courses and actions are filtered locally. Full keyboard support.
 */
@Component({
  selector: 'app-command-palette',
  imports: [MatIconModule, MatProgressSpinnerModule, AvatarComponent],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent {
  private readonly router = inject(Router);
  private readonly api = inject(StudentApiService);
  private readonly theme = inject(ThemeService);
  private readonly courseStore = inject(CourseStore);
  private readonly dialogRef = inject(MatDialogRef<CommandPaletteComponent>);

  protected readonly query = signal('');
  protected readonly active = signal(0);
  protected readonly searching = signal(false);

  private readonly courses = toSignal(this.courseStore.courses$, { initialValue: [] });

  private readonly students = toSignal(
    toObservable(this.query).pipe(
      map((q) => q.trim()),
      debounceTime(150),
      distinctUntilChanged(),
      tap((q) => this.searching.set(!!q)),
      switchMap((search) =>
        search
          ? this.api.list({ ...DEFAULT_STUDENT_QUERY, search, size: 5 }).pipe(
              map((page) => page.content),
              catchError(() => of([] as Student[])),
              finalize(() => this.searching.set(false)),
            )
          : of([] as Student[]),
      ),
      takeUntilDestroyed(),
    ),
    { initialValue: [] as Student[] },
  );

  private readonly actions: PaletteItem[] = [
    {
      id: 'a-dashboard',
      group: 'Actions',
      icon: 'space_dashboard',
      label: 'Go to dashboard',
      run: () => this.go(['/dashboard']),
    },
    {
      id: 'a-students',
      group: 'Actions',
      icon: 'group',
      label: 'Go to students',
      run: () => this.go(['/students']),
    },
    {
      id: 'a-courses',
      group: 'Actions',
      icon: 'menu_book',
      label: 'Go to courses',
      run: () => this.go(['/courses']),
    },
    {
      id: 'a-new-student',
      group: 'Actions',
      icon: 'person_add',
      label: 'Add a student',
      run: () => this.go(['/students/new']),
    },
    {
      id: 'a-new-course',
      group: 'Actions',
      icon: 'add_circle',
      label: 'Add a course',
      run: () => this.go(['/courses'], { new: 1 }),
    },
    {
      id: 'a-unenrolled',
      group: 'Actions',
      icon: 'person_off',
      label: 'Students not enrolled in any course',
      run: () => this.go(['/students'], { status: 'none' }),
    },
    {
      id: 'a-theme',
      group: 'Actions',
      icon: 'contrast',
      label: 'Toggle dark mode',
      run: () => this.theme.toggle(),
    },
  ];

  /** Everything to show, in display order: actions, then courses, then students. */
  protected readonly items = computed<PaletteItem[]>(() => {
    const q = this.query().trim().toLowerCase();
    const actions = q
      ? this.actions.filter((a) => a.label.toLowerCase().includes(q))
      : this.actions;
    const courses = q
      ? this.courses()
          .filter((c) => c.code.toLowerCase().includes(q) || c.title.toLowerCase().includes(q))
          .slice(0, 5)
          .map<PaletteItem>((c) => ({
            id: `c-${c.id}`,
            group: 'Courses',
            icon: 'menu_book',
            label: `${c.code} · ${c.title}`,
            hint: c.availableSeats === 0 ? 'Full' : `${c.availableSeats} seats left`,
            run: () => this.go(['/courses', c.id]),
          }))
      : [];
    const students = this.students().map<PaletteItem>((s) => ({
      id: `s-${s.id}`,
      group: 'Students',
      icon: 'person',
      label: s.fullName,
      hint: s.email,
      avatar: s.fullName,
      run: () => this.go(['/students', s.id]),
    }));
    return [...actions, ...courses, ...students];
  });

  protected readonly groups = computed(() => {
    const items = this.items();
    return (['Actions', 'Courses', 'Students'] as const)
      .map((name) => ({ name, items: items.filter((i) => i.group === name) }))
      .filter((g) => g.items.length > 0);
  });

  constructor() {
    this.courseStore.ensureLoaded();
    // Keep the highlighted row in range and visible.
    effect(() => {
      const count = this.items().length;
      if (this.active() >= count) {
        this.active.set(Math.max(0, count - 1));
      }
    });
    effect(() => {
      const item = this.items()[this.active()];
      if (item) {
        document.getElementById(`palette-${item.id}`)?.scrollIntoView({ block: 'nearest' });
      }
    });
  }

  protected index(item: PaletteItem): number {
    return this.items().indexOf(item);
  }

  protected onInput(value: string): void {
    this.query.set(value);
    this.active.set(0);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const count = this.items().length;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.active.update((i) => (count ? (i + 1) % count : 0));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.active.update((i) => (count ? (i - 1 + count) % count : 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const item = this.items()[this.active()];
      if (item) {
        this.select(item);
      }
    }
  }

  protected select(item: PaletteItem): void {
    this.dialogRef.close();
    item.run();
  }

  private go(commands: unknown[], queryParams?: Record<string, unknown>): void {
    this.router.navigate(commands, { queryParams });
  }
}
