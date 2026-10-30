import { FormControl, FormGroup } from '@angular/forms';
import { errorMessage } from './error-message';
import { applyServerErrors } from './server-errors';
import { notBlankValidator, pastDateValidator } from './validators';

describe('form helpers', () => {
  describe('applyServerErrors', () => {
    it('puts backend field errors on the matching controls', () => {
      const form = new FormGroup({
        email: new FormControl('ada@university.edu'),
        name: new FormControl('Ada'),
      });

      const applied = applyServerErrors(form, {
        status: 409,
        message: 'Duplicate',
        fieldErrors: [
          { field: 'email', message: 'A student with this email already exists' },
          { field: 'unknownField', message: 'ignored' },
        ],
      });

      expect(applied).toBe(true);
      expect(form.controls.email.errors).toEqual({
        server: 'A student with this email already exists',
      });
      expect(form.controls.email.touched).toBe(true);
      expect(form.controls.name.errors).toBeNull();
      expect(errorMessage(form.controls.email, 'Email')).toBe(
        'A student with this email already exists',
      );
    });

    it('returns false when there is nothing to map', () => {
      const form = new FormGroup({ email: new FormControl('') });
      expect(applyServerErrors(form, { status: 500, message: 'Boom' })).toBe(false);
    });
  });

  describe('pastDateValidator', () => {
    it('accepts yesterday and rejects today', () => {
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);

      expect(pastDateValidator(new FormControl(yesterday))).toBeNull();
      expect(pastDateValidator(new FormControl(new Date()))).toEqual({ pastDate: true });
      expect(pastDateValidator(new FormControl(null))).toBeNull();
    });
  });

  describe('notBlankValidator', () => {
    it('rejects whitespace-only text', () => {
      expect(notBlankValidator(new FormControl('   '))).toEqual({ required: true });
      expect(notBlankValidator(new FormControl(' Ada '))).toBeNull();
      expect(notBlankValidator(new FormControl(''))).toBeNull(); // Validators.required handles empty
    });
  });

  describe('errorMessage', () => {
    it('describes common validation errors', () => {
      const control = new FormControl('x');
      control.setErrors({ maxlength: { requiredLength: 50 } });
      expect(errorMessage(control, 'First name')).toBe('First name must be at most 50 characters');

      control.setErrors({ required: true });
      expect(errorMessage(control, 'Email')).toBe('Email is required');

      control.setErrors(null);
      expect(errorMessage(control, 'Email')).toBe('');
    });
  });
});
