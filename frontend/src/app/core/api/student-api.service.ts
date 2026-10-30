import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { silentErrors } from '../http/http-context';
import { Page } from '../models/page.model';
import {
  EmailAvailability,
  FULL_TIME_CREDITS,
  Student,
  StudentDetail,
  StudentQuery,
  StudentRequest,
} from '../models/student.model';

/** Thin HTTP wrapper around /api/students. State lives in StudentStore, not here. */
@Injectable({ providedIn: 'root' })
export class StudentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/students';

  list(query: StudentQuery): Observable<Page<Student>> {
    const params = filterParams(query).set('page', query.page).set('size', query.size);
    return this.http.get<Page<Student>>(this.baseUrl, { params });
  }

  /** CSV of every student matching the query (filters and sort, no paging). */
  exportCsv(query: StudentQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/export`, {
      params: filterParams(query),
      responseType: 'blob',
      observe: 'response',
    });
  }

  /** Live duplicate check for the form. Silent: the form shows the result itself. */
  checkEmail(email: string, excludeId?: number | null): Observable<EmailAvailability> {
    let params = new HttpParams().set('email', email);
    if (excludeId != null) {
      params = params.set('excludeId', excludeId);
    }
    return this.http.get<EmailAvailability>(`${this.baseUrl}/email-availability`, {
      params,
      context: silentErrors(),
    });
  }

  /** Errors are silent: the detail page renders its own "not found" state. */
  get(id: number): Observable<StudentDetail> {
    return this.http.get<StudentDetail>(`${this.baseUrl}/${id}`, { context: silentErrors() });
  }

  create(request: StudentRequest): Observable<StudentDetail> {
    return this.http.post<StudentDetail>(this.baseUrl, request);
  }

  update(id: number, request: StudentRequest): Observable<StudentDetail> {
    return this.http.put<StudentDetail>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Replaces the student's whole course set in one all-or-nothing call. */
  replaceCourses(id: number, courseIds: number[]): Observable<StudentDetail> {
    return this.http.put<StudentDetail>(`${this.baseUrl}/${id}/courses`, { courseIds });
  }

  enroll(id: number, courseId: number): Observable<StudentDetail> {
    return this.http.post<StudentDetail>(`${this.baseUrl}/${id}/courses/${courseId}`, null);
  }

  unenroll(id: number, courseId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/courses/${courseId}`);
  }
}

/** Search, filters and sort as query parameters (shared by the list and the export). */
function filterParams(query: StudentQuery): HttpParams {
  let params = new HttpParams().set('sort', query.sort).set('direction', query.direction);
  if (query.search) params = params.set('search', query.search);
  if (query.courseId != null) params = params.set('courseId', query.courseId);
  if (query.status === 'enrolled') params = params.set('enrolled', true);
  if (query.status === 'none') params = params.set('enrolled', false);
  if (query.status === 'fulltime') params = params.set('minCredits', FULL_TIME_CREDITS);
  return params;
}
