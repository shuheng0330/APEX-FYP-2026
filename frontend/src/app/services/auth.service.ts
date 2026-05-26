import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { catchError, Observable, of, tap, throwError } from 'rxjs';
import { BehaviorSubject } from "rxjs";

import { LoginResponse } from '../models/authenticaton.model';

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

  userId$ = new BehaviorSubject<string | null>(null);

  constructor(private http: HttpClient) {
    this.userId$.next(this.currentUser$.value?.userId ?? null);
  }

  login(email: string, password: string): Observable<any> {
    email = email.trim().toLocaleLowerCase();

    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, { email, password }, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' })
    })
      .pipe(tap(res => {
        this.setCurrentUser(res);
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
    return this.http.post<void>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(tap(res => {
        this.setCurrentUser(null);
      }))
  }

  refresh(requireErrorHandler: boolean): Observable<any> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/refresh`, {}, {
      withCredentials: true,
      headers: new HttpHeaders({
        'X-Skip-Error-Handler': requireErrorHandler ? 'false' : 'true'
      })
    })
      .pipe(tap(res => {
          this.setCurrentUser(res)
      }),
        catchError((error: HttpErrorResponse) => {
          this.setCurrentUser(null);
          return throwError(() => error);
        }));
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
    return this.http.get<LoginResponse>(`${this.baseUrl}/me`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Error-Handler': requireErrorHandler ? 'false' : 'true' })
    }).pipe(
      tap(res => {
        this.setCurrentUser(res)
      }),
      catchError((error: HttpErrorResponse) => {
        this.setCurrentUser(null);
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
