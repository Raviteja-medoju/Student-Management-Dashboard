/** Formats a Date as yyyy-MM-dd in local time (no UTC shift, unlike toISOString). */
export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Parses yyyy-MM-dd as a local date (new Date('2001-05-14') would be UTC midnight). */
export function fromIsoDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** Whole years between a yyyy-MM-dd birth date and today. */
export function ageFrom(dateOfBirth: string, today = new Date()): number {
  const dob = fromIsoDate(dateOfBirth);
  let age = today.getFullYear() - dob.getFullYear();
  const beforeBirthday =
    today.getMonth() < dob.getMonth() ||
    (today.getMonth() === dob.getMonth() && today.getDate() < dob.getDate());
  if (beforeBirthday) {
    age--;
  }
  return age;
}
