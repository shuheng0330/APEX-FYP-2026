import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpRequest, HttpEvent, HttpHandlerFn } from '@angular/common/http';
import { Observable, throwError, switchMap, catchError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

export const JwtInterceptor: HttpInterceptorFn = (req: HttpRequest<any>, next: HttpHandlerFn): Observable<HttpEvent<any>> => {
    const authService = inject(AuthService);

    const token = authService.getAccessToken();
    let authReq = req;

    if (token) {
        authReq = req.clone({
            setHeaders: { Authorization: `Bearer ${token}` }
        });
    }

    return next(authReq).pipe(
        catchError((err) => {
            const url = req.url.split('?')[0];
            const authEndpoint = url.startsWith(`${environment.authBaseUrl}/`);
            // A failed refresh/login is terminal; never recursively refresh a refresh request.
            if (err.status === 401 && (!authEndpoint || url === `${environment.authBaseUrl}/me`)) {
                return authService.refresh(false).pipe(
                    switchMap(() => {
                        const newToken = authService.getAccessToken();
                        if (newToken) {
                            const newReq = req.clone({
                                setHeaders: { Authorization: `Bearer ${newToken}` }
                            });
                            return next(newReq);
                        }
                        return throwError(() => err);
                    })
                );
            }
            return throwError(() => err);
        })
    );
};
