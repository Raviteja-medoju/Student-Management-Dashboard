import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <div class="page">
      <div class="not-found surface-card">
        <span class="code">404</span>
        <h1>Page not found</h1>
        <p class="muted">The page you are looking for does not exist or was moved.</p>
        <div class="actions">
          <a matButton="filled" routerLink="/dashboard"><mat-icon>home</mat-icon>Go to dashboard</a>
          <a matButton="outlined" routerLink="/students">Students</a>
        </div>
      </div>
    </div>
  `,
  styles: `
    .not-found {
      text-align: center;
      padding: 64px 24px;
      margin-top: 32px;
    }

    .code {
      font-size: 96px;
      font-weight: 800;
      line-height: 1;
      letter-spacing: -0.04em;
      background: linear-gradient(135deg, var(--mat-sys-primary), var(--mat-sys-tertiary));
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
    }

    h1 {
      font: var(--mat-sys-headline-small);
      margin: 16px 0 8px;
    }

    .actions {
      display: flex;
      justify-content: center;
      gap: 8px;
      margin-top: 24px;
    }
  `,
})
export class NotFoundComponent {}
