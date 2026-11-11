import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { catchError, finalize, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { BehaviorSubject } from "rxjs";

import { CurrentUserResponse, LoginResponse } from '../models/authenticaton.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private baseUrl = environment.authBaseUrl;
  private currentUser$ = new BehaviorSubject<LoginResponse | null>(null);
  loggedIn$ = new BehaviorSubject<boolean>(false);
  roles: string[] = [];
  accessToken: string | null = null;
  redirectUrl?: string;
  private refreshRequest$: Observable<LoginResponse> | null = null;
  private sessionRevision = 0;
  private refreshRevision = -1;
  private readonly document = inject(DOCUMENT);

  userId$ = new BehaviorSubject<string | null>(null);

  constructor(private http: HttpClient) {
    this.userId$.next(this.currentUser$.value?.userId ?? null);
  }

  login(email: string, password: string): Observable<any> {
    const revision = ++this.sessionRevision;
    email = email.trim().toLocaleLowerCase();

    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, { email, password }, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
      .pipe(tap(res => {
        if (revision === this.sessionRevision) this.setCurrentUser(res);
      }))
  }

  private setCurrentUser(user: LoginResponse | null) {
    this.currentUser$.next(user);
    this.userId$.next(user?.userId ?? null);
    this.roles = user?.roles ?? [];
    this.accessToken = user?.accessToken ?? null;
    this.loggedIn$.next(!!user);
  }

  logout(): Observable<void> {
    const revision = ++this.sessionRevision;
    return this.http.post<void>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(tap(res => {
        if (revision === this.sessionRevision) this.setCurrentUser(null);
      }))
  }

  refresh(requireErrorHandler: boolean): Observable<LoginResponse> {
    const revision = this.sessionRevision;
    if (this.refreshRequest$ && this.refreshRevision === revision) return this.refreshRequest$;
    const send = () => this.http.post<LoginResponse>(`${this.baseUrl}/refresh`, {}, {
      withCredentials: true,
      headers: new HttpHeaders({
        'X-Skip-Error-Handler': requireErrorHandler ? 'false' : 'true'
      })
    });
    // The HttpOnly refresh cookie rotates; tabs must not send its old value concurrently.
    const locks = this.document.defaultView?.navigator.locks;
    const response$ = locks ? new Observable<LoginResponse>(subscriber => {
      void locks.request(`apex-auth-refresh:${this.baseUrl}`, () => new Promise<void>(release => {
        if (subscriber.closed) { release(); return; }
        if (revision !== this.sessionRevision) { subscriber.error(new Error('The session changed before authentication refresh')); release(); return; }
        send().subscribe({
          next: value => subscriber.next(value),
          error: error => { subscriber.error(error); release(); },
          complete: () => { subscriber.complete(); release(); }
        });
      })).catch(error => subscriber.error(error));
    }) : send();
    const request$ = response$
      .pipe(tap(res => {
          if (revision !== this.sessionRevision) throw new Error('The session changed during authentication refresh');
          this.setCurrentUser(res);
      }),
        catchError((error: HttpErrorResponse) => {
          if (revision === this.sessionRevision) this.setCurrentUser(null);
          return throwError(() => error);
        }),
        finalize(() => { if (this.refreshRequest$ === request$) this.refreshRequest$ = null; }),
        shareReplay({ bufferSize: 1, refCount: false }));
    this.refreshRevision = revision;
    this.refreshRequest$ = request$;
    return request$;
  }

  forgotPassword(email: string, requireErrorHandler: boolean): Observable<void> {
    email = email.trim().toLocaleLowerCase();
    return this.http.post<void>(`${this.baseUrl}/forgot-password?email=${email}`, {}, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': requireErrorHandler ? 'false' : 'true' })
    });
  }

  firstTimeLogin(email: string, requireErrorHandler: boolean): Observable<void> {
    email = email.trim().toLocaleLowerCase();
    return this.http.post<void>(`${this.baseUrl}/first-time-login?email=${email}`, {}, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': requireErrorHandler ? 'false' : 'true' })
    });
  }

  resetPassword(email: string, password: string, otp: string, requireErrorHandler: boolean): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/reset-password`, { email, password, otp }, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': requireErrorHandler ? 'false' : 'true' })
    });
  }

  getCurrentUser(requireErrorHandler: boolean): Observable<LoginResponse | null> {
    const revision = this.sessionRevision;
    return this.http.get<CurrentUserResponse>(`${this.baseUrl}/me`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': requireErrorHandler ? 'false' : 'true' })
    }).pipe(
      // /me returns identity/permissions only. Keep the in-memory token restored by refresh.
      map(res => revision === this.sessionRevision ? { ...res, message: '', accessToken: this.accessToken ?? '' } : this.currentUser$.value),
      tap(res => {
        if (revision === this.sessionRevision) this.setCurrentUser(res);
      }),
      catchError((error: HttpErrorResponse) => {
        if (revision === this.sessionRevision) this.setCurrentUser(null);
        return of(null);
      })
    );
  }

  get userId(): string | null {
    return this.currentUser$.value?.userId ?? null;
  }

  hasRole(role: string): boolean {
    return this.roles?.includes(role);
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  

}
