import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { Subject, catchError, map, merge, of, startWith, switchMap } from 'rxjs';
import { StatsApiService } from '../../core/api/stats-api.service';
import { Stats } from '../../core/models/stats.model';
import { DataEvents } from '../../core/state/data-events';
import { AvatarComponent } from '../../shared/avatar/avatar.component';
import { CountUpDirective } from '../../shared/count-up/count-up.directive';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { SkeletonComponent } from '../../shared/skeleton/skeleton.component';
import { CourseSeatsChartComponent } from './charts/course-seats-chart.component';
import { CreditLoadChartComponent } from './charts/credit-load-chart.component';
import { MonthlyEnrollmentsChartComponent } from './charts/monthly-enrollments-chart.component';

type DashboardState =
  { status: 'loading' } | { status: 'ready'; stats: Stats } | { status: 'error' };

@Component({
  selector: 'app-dashboard',
  imports: [
    AsyncPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    PageHeaderComponent,
    EmptyStateComponent,
    AvatarComponent,
    SkeletonComponent,
    CountUpDirective,
    MonthlyEnrollmentsChartComponent,
    CreditLoadChartComponent,
    CourseSeatsChartComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly statsApi = inject(StatsApiService);
  private readonly retry$ = new Subject<void>();

  protected readonly skeletonTiles = [0, 1, 2, 3];

  /** Loads on open, on retry, and whenever data changes anywhere in the app. */
  protected readonly state$ = merge(of(null), this.retry$, inject(DataEvents).changes$).pipe(
    switchMap(() =>
      this.statsApi.get().pipe(
        map((stats): DashboardState => ({ status: 'ready', stats })),
        catchError(() => of<DashboardState>({ status: 'error' })),
      ),
    ),
    startWith<DashboardState>({ status: 'loading' }),
  );

  protected retry(): void {
    this.retry$.next();
  }
}
