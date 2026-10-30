import { Routes } from '@angular/router';

/** Every feature is lazy-loaded, so the first page only downloads what it needs. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'students',
    loadChildren: () => import('./features/students/students.routes').then((m) => m.STUDENT_ROUTES),
  },
  {
    path: 'courses',
    loadChildren: () => import('./features/courses/courses.routes').then((m) => m.COURSE_ROUTES),
  },
  {
    path: '**',
    title: 'Page not found',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
];
