import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError, Observable, BehaviorSubject, filter, take } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Set API Gateway Base URL dynamically
  const hostname = window.location.hostname;
  const gatewayUrl = hostname === 'localhost' || hostname === '127.0.0.1'
    ? 'http://localhost:8080'
    : `http://${hostname}:8080`;
  let apiReq = req;
  
  if (req.url.startsWith('/api')) {
    apiReq = req.clone({
      url: `${gatewayUrl}${req.url}`
    });
  }

  // Inject Access Token if user is logged in
  const token = authService.getAccessToken();
  if (token) {
    apiReq = apiReq.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(apiReq).pipe(
    catchError((error: any) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        // Skip refresh token flow if we are already trying to refresh or if we are logging in/out
        if (req.url.includes('/api/auth/login') || req.url.includes('/api/auth/register')) {
          return throwError(() => error);
        }

        if (req.url.includes('/api/auth/refresh')) {
          // If refresh request fails, perform logout and reject
          authService.clearSession();
          router.navigate(['/login']);
          return throwError(() => error);
        }

        // Try to refresh token
        const refreshToken = authService.getRefreshToken();
        if (refreshToken) {
          return authService.refreshSession(refreshToken).pipe(
            switchMap((response) => {
              // Retry original request with the new token
              const retryReq = apiReq.clone({
                setHeaders: {
                  Authorization: `Bearer ${response.accessToken}`
                }
              });
              return next(retryReq);
            }),
            catchError((refreshErr) => {
              // If refreshing fails (e.g. account suspended), logout and fail
              authService.clearSession();
              router.navigate(['/login']);
              return throwError(() => refreshErr);
            })
          );
        } else {
          // No refresh token, redirect to login
          authService.clearSession();
          router.navigate(['/login']);
        }
      }
      return throwError(() => error);
    })
  );
};
