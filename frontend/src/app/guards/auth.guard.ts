import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from "@angular/router";

import { AuthService } from "../services/auth.service";
import { catchError, map, Observable, of, take, tap } from "rxjs";

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
    constructor(private auth: AuthService, private router: Router) { }

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
        return this.auth.loggedIn$.pipe(
            take(1),
            tap(isLoggedIn => {
                if (!isLoggedIn) {
                    this.auth.redirectUrl = state.url;
                    this.router.navigate(['/login']);
                }
            }),
            map(isLoggedIn => isLoggedIn)
        );
    }
}

