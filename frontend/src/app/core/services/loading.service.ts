import { Injectable } from '@angular/core';
import { BehaviorSubject, distinctUntilChanged, map, of, switchMap, timer } from 'rxjs';

/** Counts in-flight HTTP requests; drives the global progress bar. */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly active = new BehaviorSubject(0);

  /** True while any request is running. Waits 150ms before showing to avoid flicker on fast calls. */
  readonly loading$ = this.active.pipe(
    map((count) => count > 0),
    distinctUntilChanged(),
    switchMap((busy) => (busy ? timer(150).pipe(map(() => true)) : of(false))),
  );

  start(): void {
    this.active.next(this.active.value + 1);
  }

  stop(): void {
    this.active.next(Math.max(0, this.active.value - 1));
  }
}
