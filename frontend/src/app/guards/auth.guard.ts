import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  const expectedRole = route.data['role'];
  if (expectedRole) {
    const userRole = authService.getUserRole();
    if (userRole !== expectedRole) {
      // If the user's role does not match, redirect to their proper dashboard
      if (userRole === 'USER') {
        router.navigate(['/dashboard/user']);
      } else if (userRole === 'MERCHANT') {
        router.navigate(['/dashboard/merchant']);
      } else if (userRole === 'ADMIN') {
        router.navigate(['/dashboard/admin']);
      } else {
        router.navigate(['/login']);
      }
      return false;
    }
  }

  return true;
};
