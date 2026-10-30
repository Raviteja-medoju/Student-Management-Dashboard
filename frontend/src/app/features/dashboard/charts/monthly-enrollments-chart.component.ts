import { Component, computed, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MonthlyCount } from '../../../core/models/stats.model';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Column chart: new enrollments per month. One series, so no legend (the title names it).
 * Columns are capped at 24px with a 4px rounded top, value on the cap, hover/focus tooltip,
 * and a screen-reader table with the same numbers.
 */
@Component({
  selector: 'app-monthly-enrollments-chart',
  imports: [MatTooltipModule],
  template: `
    <div class="plot" role="img" [attr.aria-label]="summary()">
      @for (column of columns(); track column.key) {
        <div
          class="band"
          tabindex="0"
          [matTooltip]="column.tooltip"
          matTooltipPosition="above"
          [attr.aria-label]="column.tooltip"
        >
          <span class="value">{{ column.value }}</span>
          <span class="bar" [style.height.%]="column.percent"></span>
          <span class="label">{{ column.label }}</span>
        </div>
      }
    </div>
    <table class="visually-hidden">
      <caption>
        New enrollments per month
      </caption>
      <tr>
        <th scope="col">Month</th>
        <th scope="col">Enrollments</th>
      </tr>
      @for (column of columns(); track column.key) {
        <tr>
          <td>{{ column.tooltip }}</td>
          <td>{{ column.value }}</td>
        </tr>
      }
    </table>
  `,
  styles: `
    .plot {
      display: flex;
      align-items: stretch;
      height: 200px;
      padding: 8px 4px 0;
      border-bottom: 1px solid var(--viz-baseline);
    }

    .band {
      position: relative;
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      outline: none;
      border-radius: 8px;
      cursor: default;

      &:hover .bar,
      &:focus-visible .bar {
        background: var(--viz-series-hover);
      }

      &:focus-visible {
        box-shadow: inset 0 0 0 2px var(--mat-sys-primary);
      }
    }

    .value {
      margin-bottom: 6px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface);
    }

    .bar {
      width: 24px;
      min-height: 2px;
      border-radius: 4px 4px 0 0;
      background: var(--viz-series);
      transition:
        height 500ms cubic-bezier(0.2, 0, 0, 1),
        background 120ms ease;
    }

    .label {
      position: absolute;
      bottom: -26px;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    :host {
      display: block;
      padding-bottom: 28px;
    }
  `,
})
export class MonthlyEnrollmentsChartComponent {
  readonly data = input.required<MonthlyCount[]>();

  protected readonly columns = computed(() => {
    const data = this.data();
    // Leave head-room above the tallest column for its value label.
    const max = Math.max(1, ...data.map((d) => d.enrollments)) * 1.15;
    return data.map((d) => {
      const [year, month] = d.month.split('-').map(Number);
      const label = MONTHS[month - 1];
      return {
        key: d.month,
        label,
        value: d.enrollments,
        percent: (d.enrollments / max) * 100,
        tooltip: `${label} ${year}: ${d.enrollments} new enrollment${d.enrollments === 1 ? '' : 's'}`,
      };
    });
  });

  protected readonly summary = computed(
    () =>
      'New enrollments per month: ' +
      this.columns()
        .map((c) => `${c.label} ${c.value}`)
        .join(', '),
  );
}
