import { Injectable } from "@angular/core";
import { CanActivate, Router } from "@angular/router";

import { AuthService } from "../services/auth.service";
import { catchError, map, Observable, of, take, tap } from "rxjs";

@Injectable({ providedIn: 'root' })
export class LoginGuard implements CanActivate {
    constructor(private auth: AuthService, private router: Router) { }

    canActivate(): Observable<boolean> {
        return this.auth.loggedIn$.pipe(
            take(1),
            tap(isLoggedIn => {
                if (isLoggedIn) {
                    this.router.navigate(['/org-chart']);
                }
            }),
            map(isLoggedIn => !isLoggedIn)
        );
    }
}

