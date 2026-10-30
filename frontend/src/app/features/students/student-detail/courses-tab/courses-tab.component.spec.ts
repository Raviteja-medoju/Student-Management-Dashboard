import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Course } from '../../../../core/models/course.model';
import { course, studentDetail } from '../../../../testing/fixtures';
import { CoursesTabComponent } from './courses-tab.component';

/** Exposes the protected API the template uses, so tests can drive it directly. */
type CoursesTabApi = {
  toggle(course: Course): void;
  reset(): void;
  isLocked(course: Course): boolean;
  hasChanges(): boolean;
  totalCredits(): number;
};

describe('CoursesTabComponent', () => {
  let fixture: ComponentFixture<CoursesTabComponent>;
  let api: CoursesTabApi;
  const enrolled = course(1, 'CS101');
  const open = course(2, 'CS201');
  const full = course(3, 'CS450', 4, 4);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CoursesTabComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(CoursesTabComponent);
    fixture.componentRef.setInput('student', studentDetail(7, 'Ada', [1]));
    fixture.componentRef.setInput('courses', [enrolled, open, full]);
    await fixture.whenStable();
    api = fixture.componentInstance as unknown as CoursesTabApi;
  });

  it('starts with the student’s current courses and no unsaved changes', () => {
    expect(api.hasChanges()).toBe(false);
    expect(api.totalCredits()).toBe(3);
  });

  it('tells the parent about unsaved changes immediately when a course is toggled', () => {
    const emitted: boolean[] = [];
    fixture.componentInstance.dirtyChange.subscribe((dirty) => emitted.push(dirty));

    api.toggle(open);
    expect(emitted).toEqual([true]); // synchronous: the route guard sees it even on a fast click
    expect(api.totalCredits()).toBe(6);

    api.reset();
    expect(emitted).toEqual([true, false]);
  });

  it('locks a full course unless the student is already in it', () => {
    expect(api.isLocked(full)).toBe(true);
    api.toggle(full);
    expect(api.hasChanges()).toBe(false);

    expect(api.isLocked(enrolled)).toBe(false);
  });

  it('shows enrolled courses on the right, the rest on the left, and locks the full one', async () => {
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelectorAll('.course-card')).toHaveLength(3);
    expect(element.querySelector('[data-testid=enrolled-CS101]')).not.toBeNull();
    expect(element.querySelector('[data-testid=course-CS201]')).not.toBeNull();
    expect(element.querySelectorAll('.course-card.locked')).toHaveLength(1);
    expect(element.querySelector('.course-card.locked')?.textContent).toContain('CS450');
  });

  it('moves a card to the other column when + is pressed', async () => {
    const element: HTMLElement = fixture.nativeElement;
    const addButton = element.querySelector<HTMLButtonElement>(
      '[data-testid=course-CS201] button',
    )!;

    addButton.click();
    await fixture.whenStable();

    expect(element.querySelector('[data-testid=enrolled-CS201]')).not.toBeNull();
    expect(element.querySelector('[data-testid=enrolled-CS201]')?.classList).toContain('new');
    expect(api.hasChanges()).toBe(true);
  });

  it('resets the selection when a new version of the student arrives (e.g. after saving)', async () => {
    api.toggle(open);
    expect(api.hasChanges()).toBe(true);

    fixture.componentRef.setInput('student', studentDetail(7, 'Ada', [1, 2]));
    await fixture.whenStable();

    expect(api.hasChanges()).toBe(false);
    expect(api.totalCredits()).toBe(6);
  });
});
