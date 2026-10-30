import { Routes } from '@angular/router';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const STUDENT_ROUTES: Routes = [
  {
    path: '',
    title: 'Students',
    loadComponent: () =>
      import('./student-list/student-list.component').then((m) => m.StudentListComponent),
  },
  {
    path: 'new',
    title: 'New student',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () =>
      import('./student-detail/student-detail.component').then((m) => m.StudentDetailComponent),
  },
  {
    path: ':id',
    title: 'Student',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () =>
      import('./student-detail/student-detail.component').then((m) => m.StudentDetailComponent),
  },
];
