import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Friendly placeholder for empty lists, missing records and errors. Actions are projected. */
@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  template: `
    <div class="empty" [class.error]="tone() === 'error'">
      <div class="icon">
        <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      </div>
      <h3>{{ title() }}</h3>
      @if (message()) {
        <p class="muted">{{ message() }}</p>
      }
      <div class="actions"><ng-content /></div>
    </div>
  `,
  styles: `
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: 48px 24px;
    }

    .icon {
      width: 64px;
      height: 64px;
      display: grid;
      place-items: center;
      border-radius: 50%;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      margin-bottom: 16px;

      mat-icon {
        font-size: 30px;
        width: 30px;
        height: 30px;
      }
    }

    .error .icon {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }

    h3 {
      margin: 0 0 4px;
      font: var(--mat-sys-title-medium);
    }

    p {
      margin: 0 0 16px;
      max-width: 420px;
    }

    .actions {
      display: flex;
      gap: 8px;
    }
  `,
})
export class EmptyStateComponent {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly message = input<string>();
  readonly tone = input<'neutral' | 'error'>('neutral');
}
