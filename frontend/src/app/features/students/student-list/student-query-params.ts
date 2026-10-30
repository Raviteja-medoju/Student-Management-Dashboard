import { ParamMap, Params } from '@angular/router';
import {
  SortDirection,
  StudentQuery,
  StudentSortField,
  StudentStatusFilter,
} from '../../../core/models/student.model';
import { DEFAULT_STUDENT_QUERY } from '../../../core/state/student.store';

const SORT_FIELDS: StudentSortField[] = [
  'firstName',
  'lastName',
  'email',
  'dateOfBirth',
  'createdAt',
];
const STATUSES: StudentStatusFilter[] = ['all', 'enrolled', 'none', 'fulltime'];
const PAGE_SIZES = [5, 10, 25, 50];

/**
 * The list's state as URL query parameters, e.g. /students?q=ann&status=enrolled&page=2.
 * Only non-default values are written, so the plain /students URL stays clean.
 * Pages are 1-based in the URL (for people) and 0-based in the store (for the API).
 */
export function queryToParams(query: StudentQuery): Params {
  const d = DEFAULT_STUDENT_QUERY;
  return {
    q: query.search || null,
    course: query.courseId ?? null,
    status: query.status !== d.status ? query.status : null,
    page: query.page !== d.page ? query.page + 1 : null,
    size: query.size !== d.size ? query.size : null,
    sort: query.sort !== d.sort ? query.sort : null,
    dir: query.direction !== d.direction ? query.direction : null,
  };
}

/** Reads the URL back into a query. Anything invalid falls back to the default. */
export function paramsToQuery(params: ParamMap): StudentQuery {
  const d = DEFAULT_STUDENT_QUERY;
  const page = Number(params.get('page'));
  const size = Number(params.get('size'));
  const course = Number(params.get('course'));
  const sort = params.get('sort') as StudentSortField;
  const status = params.get('status') as StudentStatusFilter;
  const dir = params.get('dir') as SortDirection;
  return {
    search: (params.get('q') ?? '').trim(),
    courseId: Number.isInteger(course) && course > 0 ? course : null,
    status: STATUSES.includes(status) ? status : d.status,
    page: Number.isInteger(page) && page >= 1 ? page - 1 : d.page,
    size: PAGE_SIZES.includes(size) ? size : d.size,
    sort: SORT_FIELDS.includes(sort) ? sort : d.sort,
    direction: dir === 'asc' || dir === 'desc' ? dir : d.direction,
  };
}
