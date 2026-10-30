import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  /** danger = destructive (red), warning = losing work, primary = neutral. */
  tone?: 'danger' | 'warning' | 'primary';
  icon?: string;
}

/**
 * The one confirmation modal used everywhere: deletes, discarding unsaved changes
 * on tab switch, closing a dirty dialog and leaving a dirty page.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="header" [class]="tone">
      <mat-icon aria-hidden="true">{{ data.icon ?? defaultIcon }}</mat-icon>
      <h2 mat-dialog-title>{{ data.title }}</h2>
    </div>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton [mat-dialog-close]="false">{{ data.cancelText ?? 'Cancel' }}</button>
      <button
        matButton="filled"
        [class.danger-button]="tone === 'danger'"
        [mat-dialog-close]="true"
        data-testid="confirm-button"
      >
        {{ data.confirmText ?? 'Confirm' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .header {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 20px 24px 0;

      mat-icon {
        flex: none;
        width: 40px;
        height: 40px;
        font-size: 22px;
        display: grid;
        place-items: center;
        border-radius: 50%;
        background: var(--mat-sys-primary-container);
        color: var(--mat-sys-on-primary-container);
      }

      &.danger mat-icon {
        background: var(--mat-sys-error-container);
        color: var(--mat-sys-on-error-container);
      }

      &.warning mat-icon {
        background: var(--app-warning-container);
        color: var(--app-warning);
      }

      h2 {
        flex: 1;
        min-width: 0;
        padding: 0 0 0 12px;
        margin: 0;
        white-space: normal;
        overflow-wrap: anywhere;
        line-height: 1.3;
      }
    }

    p {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  protected readonly tone = this.data.tone ?? 'primary';
  protected readonly defaultIcon =
    this.tone === 'danger' ? 'delete' : this.tone === 'warning' ? 'warning' : 'help';
}
