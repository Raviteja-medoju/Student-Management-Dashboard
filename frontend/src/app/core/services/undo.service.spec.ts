import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of, throwError } from 'rxjs';
import { UndoService } from './undo.service';

describe('UndoService', () => {
  let dismissed: Subject<{ dismissedByAction: boolean }>;
  const snackBar = { open: vi.fn() };

  beforeEach(() => {
    dismissed = new Subject();
    snackBar.open.mockReset().mockReturnValue({ afterDismissed: () => dismissed });
    TestBed.configureTestingModule({ providers: [{ provide: MatSnackBar, useValue: snackBar }] });
  });

  function action(commitResult = of(null)) {
    return {
      message: 'Ada deleted',
      hide: vi.fn(),
      restore: vi.fn(),
      commit: vi.fn(() => commitResult),
    };
  }

  it('hides at once and only calls the server after the undo window closes', () => {
    const a = action();
    TestBed.inject(UndoService).run(a);

    expect(a.hide).toHaveBeenCalled();
    expect(snackBar.open).toHaveBeenCalledWith('Ada deleted', 'Undo', expect.anything());
    expect(a.commit).not.toHaveBeenCalled();

    dismissed.next({ dismissedByAction: false });

    expect(a.commit).toHaveBeenCalled();
    expect(a.restore).not.toHaveBeenCalled();
  });

  it('restores and never calls the server when Undo is pressed', () => {
    const a = action();
    TestBed.inject(UndoService).run(a);

    dismissed.next({ dismissedByAction: true });

    expect(a.restore).toHaveBeenCalled();
    expect(a.commit).not.toHaveBeenCalled();
  });

  it('restores the item if the server call fails', () => {
    const a = action(throwError(() => new Error('409')));
    TestBed.inject(UndoService).run(a);

    dismissed.next({ dismissedByAction: false });

    expect(a.restore).toHaveBeenCalled();
  });
});
