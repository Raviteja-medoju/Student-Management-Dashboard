import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Observable, firstValueFrom, of } from 'rxjs';
import { ConfirmService } from '../services/confirm.service';
import { HasUnsavedChanges, unsavedChangesGuard } from './unsaved-changes.guard';

describe('unsavedChangesGuard', () => {
  const confirm = { discardChanges: vi.fn<() => Observable<boolean>>() };

  beforeEach(() => {
    confirm.discardChanges.mockReset();
    TestBed.configureTestingModule({ providers: [{ provide: ConfirmService, useValue: confirm }] });
  });

  function run(component: HasUnsavedChanges) {
    return TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(
        component,
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot,
      ),
    );
  }

  it('lets the user leave when nothing is unsaved, without opening a dialog', () => {
    expect(run({ hasUnsavedChanges: () => false })).toBe(true);
    expect(confirm.discardChanges).not.toHaveBeenCalled();
  });

  it('keeps the user on the page when they choose to keep editing', async () => {
    confirm.discardChanges.mockReturnValue(of(false));

    const result = run({ hasUnsavedChanges: () => true }) as Observable<boolean>;

    expect(await firstValueFrom(result)).toBe(false);
  });

  it('lets the user leave when they confirm discarding changes', async () => {
    confirm.discardChanges.mockReturnValue(of(true));

    const result = run({ hasUnsavedChanges: () => true }) as Observable<boolean>;

    expect(await firstValueFrom(result)).toBe(true);
  });
});
