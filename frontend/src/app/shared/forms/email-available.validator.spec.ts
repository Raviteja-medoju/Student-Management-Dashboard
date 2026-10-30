import { FormControl } from '@angular/forms';
import { firstValueFrom, Observable, of } from 'rxjs';
import { StudentApiService } from '../../core/api/student-api.service';
import { emailAvailableValidator } from './email-available.validator';

describe('emailAvailableValidator', () => {
  const api = { checkEmail: vi.fn() };
  const validator = (own: string | null = null) =>
    emailAvailableValidator(api as unknown as StudentApiService, () => ({
      id: own ? 4 : null,
      email: own,
    }));

  beforeEach(() => api.checkEmail.mockReset());

  function run(email: string, own: string | null = null) {
    return firstValueFrom(validator(own)(new FormControl(email)) as Observable<unknown>);
  }

  it('flags an email that belongs to someone else', async () => {
    api.checkEmail.mockReturnValue(of({ email: 'ada@university.edu', available: false }));

    expect(await run(' Ada@University.edu ')).toEqual({ emailTaken: true });
    expect(api.checkEmail).toHaveBeenCalledWith('ada@university.edu', null);
  });

  it('accepts a free email', async () => {
    api.checkEmail.mockReturnValue(of({ email: 'new@university.edu', available: true }));

    expect(await run('new@university.edu')).toBeNull();
  });

  it("does not call the API for the student's own email or an empty value", async () => {
    expect(await run('ADA@university.edu', 'ada@university.edu')).toBeNull();
    expect(await run('')).toBeNull();
    expect(api.checkEmail).not.toHaveBeenCalled();
  });
});
