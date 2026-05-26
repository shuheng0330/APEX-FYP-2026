import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class PermissionGuard implements CanActivate {

  constructor(private authService: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const requiredRoles = route.data['requiredRoles'] as string[];

    if (!requiredRoles || requiredRoles.length === 0) {
      return true;
    }

    const hasPermission = requiredRoles.some(role =>
      this.authService.hasRole(role)
    );

    if (!hasPermission) {
      this.router.navigate(['/access-denied']); // or to homepage
      return false;
    }

    return true;
  }
}
