import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Returns the first validation message for a touched/dirty control, or null.
 * Called from the owning component's template so it re-evaluates with that component.
 */
export function fieldError(control: AbstractControl | null | undefined, name = 'This field'): string | null {
  if (!control || !control.errors || !(control.touched || control.dirty)) {
    return null;
  }
  const e = control.errors;
  if (e['required']) return `${name} is required`;
  if (e['email']) return 'Enter a valid email address';
  if (e['minlength']) return `${name} must be at least ${e['minlength'].requiredLength} characters`;
  if (e['maxlength']) return `${name} must be at most ${e['maxlength'].requiredLength} characters`;
  if (e['min'] !== undefined) return `${name} must be at least ${e['min'].min}`;
  if (e['max'] !== undefined) return `${name} must be at most ${e['max'].max}`;
  if (e['pattern']) return `${name} has an invalid format`;
  if (e['dateOrder']) return e['dateOrder'] as string;
  if (e['passwordMismatch']) return 'Passwords do not match';
  return `${name} is invalid`;
}

/** Group validator: the `later` date control must not be before the `earlier` one. */
export function dateOrderValidator(earlier: string, later: string, message: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const a = group.get(earlier)?.value as string | null;
    const b = group.get(later)?.value as string | null;
    const target = group.get(later);
    if (!target) return null;
    const errors = { ...(target.errors ?? {}) };
    if (a && b && b < a) {
      errors['dateOrder'] = message;
      target.setErrors(errors);
      target.markAsTouched();
    } else if (errors['dateOrder']) {
      delete errors['dateOrder'];
      target.setErrors(Object.keys(errors).length ? errors : null);
    }
    return null;
  };
}
