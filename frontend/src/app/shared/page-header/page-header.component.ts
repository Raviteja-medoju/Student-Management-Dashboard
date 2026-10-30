import { Component, input } from '@angular/core';

/** Title row used at the top of every page. Action buttons are projected into the right side. */
@Component({
  selector: 'app-page-header',
  template: `
    <header>
      <div class="titles">
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="muted">{{ subtitle() }}</p>
        }
      </div>
      <div class="actions"><ng-content /></div>
    </header>
  `,
  styles: `
    header {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
      margin-bottom: 24px;
    }

    .titles {
      flex: 1 1 260px;
      min-width: 0;
    }

    h1 {
      margin: 0;
      font: var(--mat-sys-headline-medium);
      font-weight: 600;
      letter-spacing: -0.01em;
    }

    p {
      margin: 4px 0 0;
    }

    .actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>();
}
