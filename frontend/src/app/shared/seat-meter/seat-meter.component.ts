import { Component, computed, input } from '@angular/core';

/** "12 / 30 seats" with a bar that turns amber when nearly full and red when full. */
@Component({
  selector: 'app-seat-meter',
  template: `
    <div class="meter" [class]="level()">
      <div class="label">
        <span>{{ enrolled() }} / {{ capacity() }} seats</span>
        @if (level() === 'full') {
          <span class="status">Full</span>
        } @else if (showRemaining()) {
          <span class="status">{{ capacity() - enrolled() }} left</span>
        }
      </div>
      <div
        class="track"
        role="progressbar"
        [attr.aria-valuenow]="enrolled()"
        aria-valuemin="0"
        [attr.aria-valuemax]="capacity()"
        [attr.aria-label]="'Seats taken: ' + enrolled() + ' of ' + capacity()"
      >
        <div class="fill" [style.width.%]="percent()"></div>
      </div>
    </div>
  `,
  styles: `
    .meter {
      --bar: var(--mat-sys-primary);
      min-width: 120px;
    }

    .meter.high {
      --bar: var(--app-warning);
    }

    .meter.full {
      --bar: var(--mat-sys-error);
    }

    .label {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
      margin-bottom: 4px;
    }

    .status {
      color: var(--bar);
      font-weight: 600;
    }

    .track {
      height: 6px;
      border-radius: 999px;
      background: var(--mat-sys-surface-container-highest);
      overflow: hidden;
    }

    .fill {
      height: 100%;
      border-radius: inherit;
      background: var(--bar);
      transition: width 300ms ease;
    }
  `,
})
export class SeatMeterComponent {
  readonly enrolled = input.required<number>();
  readonly capacity = input.required<number>();
  readonly showRemaining = input(true);

  protected readonly percent = computed(() =>
    this.capacity() > 0 ? Math.min(100, (this.enrolled() / this.capacity()) * 100) : 0,
  );

  protected readonly level = computed(() => {
    const percent = this.percent();
    return percent >= 100 ? 'full' : percent >= 80 ? 'high' : 'normal';
  });
}
