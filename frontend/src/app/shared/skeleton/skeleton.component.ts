import { Component, input } from '@angular/core';

/** A shimmering placeholder block shown while the first data loads. */
@Component({
  selector: 'app-skeleton',
  template: '',
  host: {
    'aria-hidden': 'true',
    '[style.width]': 'width()',
    '[style.height]': 'height()',
    '[style.border-radius]': 'radius()',
  },
  styles: `
    :host {
      display: block;
      background: linear-gradient(
        90deg,
        var(--mat-sys-surface-container-high) 0%,
        var(--mat-sys-surface-container-lowest) 50%,
        var(--mat-sys-surface-container-high) 100%
      );
      background-size: 200% 100%;
      animation: shimmer 1.4s ease-in-out infinite;
    }

    @keyframes shimmer {
      from {
        background-position: 100% 0;
      }
      to {
        background-position: -100% 0;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      :host {
        animation: none;
      }
    }
  `,
})
export class SkeletonComponent {
  readonly width = input('100%');
  readonly height = input('14px');
  readonly radius = input('6px');
}
