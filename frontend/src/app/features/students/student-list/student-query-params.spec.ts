import { convertToParamMap } from '@angular/router';
import { DEFAULT_STUDENT_QUERY } from '../../../core/state/student.store';
import { paramsToQuery, queryToParams } from './student-query-params';

describe('student list URL state', () => {
  it('writes only non-default values, with 1-based pages', () => {
    expect(queryToParams(DEFAULT_STUDENT_QUERY)).toEqual({
      q: null,
      course: null,
      status: null,
      page: null,
      size: null,
      sort: null,
      dir: null,
    });

    expect(
      queryToParams({
        ...DEFAULT_STUDENT_QUERY,
        search: 'ann',
        courseId: 3,
        status: 'enrolled',
        page: 1,
        sort: 'email',
        direction: 'desc',
      }),
    ).toEqual({
      q: 'ann',
      course: 3,
      status: 'enrolled',
      page: 2,
      size: null,
      sort: 'email',
      dir: 'desc',
    });
  });

  it('reads the URL back into the same query (round trip)', () => {
    const query = {
      ...DEFAULT_STUDENT_QUERY,
      search: 'lee',
      courseId: 7,
      status: 'fulltime' as const,
      page: 2,
      size: 25,
    };
    const params = Object.fromEntries(
      Object.entries(queryToParams(query)).filter(([, value]) => value !== null),
    ) as Record<string, string>;

    expect(paramsToQuery(convertToParamMap(params))).toEqual(query);
  });

  it('falls back to defaults for anything invalid in a hand-edited URL', () => {
    const query = paramsToQuery(
      convertToParamMap({
        page: '-3',
        size: '7',
        sort: 'password',
        dir: 'up',
        status: 'vip',
        course: 'abc',
      }),
    );

    expect(query).toEqual(DEFAULT_STUDENT_QUERY);
  });
});
