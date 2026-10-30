import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';

export interface UndoableAction {
  /** Snackbar text, e.g. "Ada Lovelace deleted". */
  message: string;
  /** Hide the item right away, as if it were gone. */
  hide(): void;
  /** Bring it back (undo pressed, or the server call failed). */
  restore(): void;
  /** The real server call, made only once the undo window has passed. */
  commit(): Observable<unknown>;
}

/**
 * "Deleted · Undo" for destructive actions: the item disappears at once, but the server
 * call waits until the snackbar closes. Pressing Undo just shows it again; nothing was lost.
 * Opening another snackbar closes this one, which commits the pending action.
 */
@Injectable({ providedIn: 'root' })
export class UndoService {
  private readonly snackBar = inject(MatSnackBar);

  static readonly WINDOW_MS = 6000;

  run(action: UndoableAction): void {
    action.hide();
    const ref = this.snackBar.open(action.message, 'Undo', {
      duration: UndoService.WINDOW_MS,
      panelClass: 'snack-undo',
    });
    ref.afterDismissed().subscribe(({ dismissedByAction }) => {
      if (dismissedByAction) {
        action.restore();
        return;
      }
      // On failure the error interceptor explains why; the item comes back.
      action.commit().subscribe({ error: () => action.restore() });
    });
  }
}
