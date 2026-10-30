import { BreakpointObserver } from '@angular/cdk/layout';
import { AsyncPipe } from '@angular/common';
import { Component, computed, inject, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { LoadingService } from './core/services/loading.service';
import { ThemeService } from './core/services/theme.service';
import { CommandPaletteComponent } from './shared/command-palette/command-palette.component';

interface NavItem {
  label: string;
  icon: string;
  link: string;
}

/** App shell: toolbar, responsive side navigation, global progress bar and the routed page. */
@Component({
  selector: 'app-root',
  imports: [
    AsyncPipe,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatProgressBarModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  host: { '(document:keydown)': 'onKeydown($event)' },
})
export class App {
  protected readonly loading$ = inject(LoadingService).loading$;
  protected readonly theme = inject(ThemeService);
  private readonly sidenav = viewChild.required(MatSidenav);
  private readonly dialog = inject(MatDialog);
  private paletteOpen = false;
  protected readonly shortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘K' : 'Ctrl K';

  protected readonly navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'space_dashboard', link: '/dashboard' },
    { label: 'Students', icon: 'group', link: '/students' },
    { label: 'Courses', icon: 'menu_book', link: '/courses' },
  ];

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 959px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected readonly themeIcon = computed(() =>
    this.theme.mode() === 'dark' ? 'light_mode' : 'dark_mode',
  );

  /** Ctrl+K / ⌘K opens the command palette from anywhere. */
  protected onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.openPalette();
    }
  }

  protected openPalette(): void {
    if (this.paletteOpen) {
      return;
    }
    this.paletteOpen = true;
    this.dialog
      .open(CommandPaletteComponent, {
        width: '640px',
        maxWidth: '94vw',
        position: { top: '12vh' },
        panelClass: 'command-palette-panel',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe(() => (this.paletteOpen = false));
  }

  constructor() {
    // On phones the menu overlays the page, so close it after navigating.
    inject(Router)
      .events.pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        if (this.isHandset()) {
          this.sidenav().close();
        }
      });
  }
}
