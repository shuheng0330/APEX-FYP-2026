import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpRequest, HttpEvent, HttpHandlerFn } from '@angular/common/http';
import { Observable, throwError, switchMap, catchError } from 'rxjs';
import { AuthService } from '../services/auth.service';

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
            if (err.status === 401) {
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