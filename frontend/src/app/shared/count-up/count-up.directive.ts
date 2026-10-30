import { Directive, ElementRef, effect, inject, input } from '@angular/core';

/**
 * Animates a number from its previous value to the new one (e.g. dashboard totals).
 * Skips the animation for people who prefer reduced motion.
 */
@Directive({ selector: '[appCountUp]' })
export class CountUpDirective {
  readonly appCountUp = input.required<number>();
  readonly decimals = input(0);

  private readonly element = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private current = 0;
  private frame = 0;

  constructor() {
    effect((onCleanup) => {
      const target = this.appCountUp();
      const decimals = this.decimals();
      const start = this.current;
      const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      if (reduce || start === target) {
        this.render(target, decimals);
        return;
      }
      const duration = 700;
      const began = performance.now();
      const step = (now: number) => {
        const t = Math.min(1, (now - began) / duration);
        const eased = 1 - Math.pow(1 - t, 3);
        this.render(start + (target - start) * eased, decimals);
        if (t < 1) {
          this.frame = requestAnimationFrame(step);
        }
      };
      this.frame = requestAnimationFrame(step);
      onCleanup(() => cancelAnimationFrame(this.frame));
    });
  }

  private render(value: number, decimals: number): void {
    this.current = value;
    this.element.textContent = value.toFixed(decimals);
  }
}
