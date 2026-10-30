import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { silentErrors } from '../http/http-context';
import { Course, CourseDetail, CourseRequest } from '../models/course.model';

/** Thin HTTP wrapper around /api/courses. State lives in CourseStore, not here. */
@Injectable({ providedIn: 'root' })
export class CourseApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/courses';

  list(): Observable<Course[]> {
    return this.http.get<Course[]>(this.baseUrl);
  }

  /** Errors are silent: the detail page renders its own "not found" state. */
  get(id: number): Observable<CourseDetail> {
    return this.http.get<CourseDetail>(`${this.baseUrl}/${id}`, { context: silentErrors() });
  }

  create(request: CourseRequest): Observable<CourseDetail> {
    return this.http.post<CourseDetail>(this.baseUrl, request);
  }

  update(id: number, request: CourseRequest): Observable<CourseDetail> {
    return this.http.put<CourseDetail>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
