import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Stats } from '../models/stats.model';

@Injectable({ providedIn: 'root' })
export class StatsApiService {
  private readonly http = inject(HttpClient);

  get(): Observable<Stats> {
    return this.http.get<Stats>('/api/stats');
  }
}
