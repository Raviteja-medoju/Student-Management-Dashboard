import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Course } from '../../../core/models/course.model';

/**
 * Horizontal bars: how full each course is. The track is a lighter step of the same blue;
 * a full course turns critical red and says "Full" with an icon (never color alone).
 * Sorted by fill so the bars read top to bottom; each row links to the course.
 */
@Component({
  selector: 'app-course-seats-chart',
  imports: [RouterLink, MatIconModule, MatTooltipModule],
  template: `
    <ul class="rows" aria-label="Seats taken per course">
      @for (row of rows(); track row.id) {
        <li>
          <a
            class="row"
            [routerLink]="['/courses', row.id]"
            [matTooltip]="row.tooltip"
            matTooltipPosition="above"
            [attr.aria-label]="row.tooltip"
          >
            <span class="code">{{ row.code }}</span>
            <span class="track">
              <span class="fill" [class.full]="row.full" [style.width.%]="row.percent"></span>
            </span>
            <span class="value">
              @if (row.full) {
                <mat-icon aria-hidden="true">block</mat-icon>
                Full
              } @else {
                {{ row.enrolled }}/{{ row.capacity }}
              }
            </span>
          </a>
        </li>
      }
    </ul>
  `,
  styles: `
    .rows {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    .row {
      display: grid;
      grid-template-columns: 56px 1fr 64px;
      align-items: center;
      gap: 12px;
      padding: 6px 8px;
      border-radius: 10px;
      color: inherit;
      text-decoration: none;

      &:hover,
      &:focus-visible {
        background: var(--mat-sys-surface-container);
      }
    }

    .code {
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
    }

    .track {
      height: 12px;
      border-radius: 4px;
      background: var(--viz-track);
      overflow: hidden;
    }

    .fill {
      display: block;
      height: 100%;
      border-radius: 0 4px 4px 0;
      background: var(--viz-series);
      transition: width 500ms cubic-bezier(0.2, 0, 0, 1);

      &.full {
        border-radius: 4px;
        background: var(--viz-critical);
      }
    }

    .value {
      display: inline-flex;
      align-items: center;
      justify-content: flex-end;
      gap: 4px;
      font: var(--mat-sys-label-large);
      font-variant-numeric: tabular-nums;
      color: var(--mat-sys-on-surface);

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        color: var(--viz-critical);
      }
    }
  `,
})
export class CourseSeatsChartComponent {
  readonly data = input.required<Course[]>();

  protected readonly rows = computed(() =>
    this.data()
      .map((course) => {
        const ratio = course.capacity ? course.enrolledCount / course.capacity : 0;
        const percent = Math.round(ratio * 100);
        return {
          id: course.id,
          code: course.code,
          enrolled: course.enrolledCount,
          capacity: course.capacity,
          ratio,
          percent: Math.min(100, Math.max(ratio > 0 ? 2 : 0, ratio * 100)),
          full: course.availableSeats === 0,
          tooltip: `${course.code} ${course.title}: ${course.enrolledCount} of ${course.capacity} seats taken (${percent}%)`,
        };
      })
      .sort((a, b) => b.ratio - a.ratio || a.code.localeCompare(b.code)),
  );
}
