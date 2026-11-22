import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthService } from './auth.service';
import { ToastService } from './toast.service';

/** Attaches the JWT to API calls and signs the user out when the backend rejects the token. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);
  const isApi = req.url.startsWith(API_BASE_URL);
  const isAuthCall =
    req.url.startsWith(`${API_BASE_URL}/auth/login`) || req.url.startsWith(`${API_BASE_URL}/auth/register`);
  const token = auth.token();

  const request =
    isApi && token && !isAuthCall ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isApi && !isAuthCall) {
        auth.logout(false);
        toast.error('Your session has ended. Please sign in again.');
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
