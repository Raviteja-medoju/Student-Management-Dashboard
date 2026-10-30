import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { StudentDetail } from '../../../../core/models/student.model';
import { EmptyStateComponent } from '../../../../shared/empty-state/empty-state.component';
import { ageFrom } from '../../../../shared/utils/date';

/** Read-only overview. Emits events so the parent can jump to the right tab. */
@Component({
  selector: 'app-summary-tab',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, EmptyStateComponent],
  templateUrl: './summary-tab.component.html',
  styleUrl: './summary-tab.component.scss',
})
export class SummaryTabComponent {
  readonly student = input.required<StudentDetail>();
  readonly editProfile = output<void>();
  readonly manageCourses = output<void>();

  protected readonly age = computed(() => ageFrom(this.student().dateOfBirth));
}
