import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/confirm-dialog/confirm-dialog.component';

/** Opens the shared ConfirmDialog. Resolves to true only when the user confirms. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(MatDialog);

  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data,
        width: '440px',
        maxWidth: '92vw',
        autoFocus: 'first-tabbable',
        restoreFocus: true,
      })
      .afterClosed()
      .pipe(map((result) => result === true));
  }

  /** The "you have unsaved changes" dialog used by tabs, dialogs and the route guard. */
  discardChanges(): Observable<boolean> {
    return this.confirm({
      title: 'Discard unsaved changes?',
      message: 'You have changes that have not been saved. If you continue, they will be lost.',
      confirmText: 'Discard changes',
      cancelText: 'Keep editing',
      tone: 'warning',
      icon: 'edit_off',
    });
  }
}
