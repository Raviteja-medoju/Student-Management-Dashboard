import { ageFrom, fromIsoDate, toIsoDate } from './date';

describe('date utils', () => {
  it('formats and parses yyyy-MM-dd in local time without shifting the day', () => {
    const date = fromIsoDate('2001-03-09');

    expect(date.getFullYear()).toBe(2001);
    expect(date.getMonth()).toBe(2);
    expect(date.getDate()).toBe(9);
    expect(toIsoDate(date)).toBe('2001-03-09');
  });

  it('computes age, counting the birthday only once it has passed', () => {
    const today = new Date(2026, 9, 29); // 29 Oct 2026

    expect(ageFrom('2000-10-29', today)).toBe(26);
    expect(ageFrom('2000-10-30', today)).toBe(25);
    expect(ageFrom('2000-01-01', today)).toBe(26);
  });
});
