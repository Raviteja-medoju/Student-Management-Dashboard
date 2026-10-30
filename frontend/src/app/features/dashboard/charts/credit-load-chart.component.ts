import { Component, computed, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CreditBand } from '../../../core/models/stats.model';

/**
 * Part-to-whole: students per credit-load band as one stacked bar (2px surface gaps,
 * ordinal blue ramp, gray for "not enrolled") plus a legend with counts and shares,
 * so identity never depends on color alone.
 */
@Component({
  selector: 'app-credit-load-chart',
  imports: [MatTooltipModule],
  template: `
    <div class="bar" role="img" [attr.aria-label]="summary()">
      @for (band of bands(); track band.label; let i = $index) {
        @if (band.students > 0) {
          <span
            class="segment"
            tabindex="0"
            [style.flex-grow]="band.students"
            [style.background]="'var(--viz-band-' + i + ')'"
            [matTooltip]="band.tooltip"
            [attr.aria-label]="band.tooltip"
          ></span>
        }
      }
    </div>
    <ul class="legend">
      @for (band of bands(); track band.label; let i = $index) {
        <li [class.empty]="band.students === 0">
          <span class="swatch" [style.background]="'var(--viz-band-' + i + ')'"></span>
          <span class="name">{{ band.label }}</span>
          <span class="count">{{ band.students }}</span>
          <span class="share">{{ band.percent }}%</span>
        </li>
      }
    </ul>
  `,
  styles: `
    :host {
      display: block;
    }

    .bar {
      display: flex;
      gap: 2px;
      height: 28px;
      margin: 8px 0 20px;
    }

    .segment {
      min-width: 6px;
      outline: none;
      transition: filter 120ms ease;

      &:first-child {
        border-radius: 4px 0 0 4px;
      }

      &:last-child {
        border-radius: 0 4px 4px 0;
      }

      &:only-child {
        border-radius: 4px;
      }

      &:hover,
      &:focus-visible {
        filter: brightness(1.12);
      }

      &:focus-visible {
        box-shadow: 0 0 0 2px var(--mat-sys-primary);
      }
    }

    .legend {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 10px;
    }

    li {
      display: grid;
      grid-template-columns: 12px 1fr auto 44px;
      align-items: center;
      gap: 10px;

      &.empty {
        opacity: 0.55;
      }
    }

    .swatch {
      width: 12px;
      height: 12px;
      border-radius: 3px;
    }

    .name {
      color: var(--mat-sys-on-surface);
    }

    .count {
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .share {
      text-align: right;
      color: var(--mat-sys-on-surface-variant);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class CreditLoadChartComponent {
  readonly data = input.required<CreditBand[]>();

  protected readonly bands = computed(() => {
    const total = this.data().reduce((sum, band) => sum + band.students, 0) || 1;
    return this.data().map((band) => {
      const percent = Math.round((band.students / total) * 100);
      return {
        ...band,
        percent,
        tooltip: `${band.label}: ${band.students} student${band.students === 1 ? '' : 's'} (${percent}%)`,
      };
    });
  });

  protected readonly summary = computed(
    () =>
      'Students by credit load: ' +
      this.bands()
        .map((b) => `${b.label} ${b.students}`)
        .join(', '),
  );
}
