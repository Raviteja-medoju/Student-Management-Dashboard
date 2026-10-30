import { Component, computed, input } from '@angular/core';

const HUES = [210, 262, 330, 20, 150, 190, 45, 290];

/** Initials in a colored circle; the color is stable per name. */
@Component({
  selector: 'app-avatar',
  template: `<span
    class="avatar"
    [style.--size]="size() + 'px'"
    [style.--hue]="hue()"
    [attr.aria-hidden]="true"
    >{{ initials() }}</span
  >`,
  styles: `
    .avatar {
      --size: 36px;
      width: var(--size);
      height: var(--size);
      flex: none;
      display: inline-grid;
      place-items: center;
      border-radius: 50%;
      font-weight: 600;
      font-size: calc(var(--size) * 0.38);
      letter-spacing: 0.02em;
      color: light-dark(hsl(var(--hue) 55% 30%), hsl(var(--hue) 80% 88%));
      background: light-dark(hsl(var(--hue) 75% 90%), hsl(var(--hue) 35% 28%));
      user-select: none;
    }
  `,
})
export class AvatarComponent {
  readonly name = input.required<string>();
  readonly size = input(36);

  protected readonly initials = computed(() =>
    this.name()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join(''),
  );

  protected readonly hue = computed(() => {
    let hash = 0;
    for (const char of this.name()) {
      hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
    }
    return HUES[hash % HUES.length];
  });
}
