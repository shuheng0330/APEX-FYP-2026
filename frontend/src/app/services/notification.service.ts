import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { NotificationList } from '../models/notification.model';

/**
 * Client for the notification bell (/api/notifications, NFR-016). The bell works in the background, so its
 * requests skip the page-wide loading overlay and error pop-ups.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private baseUrl = environment.apiBaseUrl + '/notifications';
  private quiet = new HttpHeaders({ 'X-Skip-Loading': 'true', 'X-Skip-Error-Handler': 'true' });

  constructor(private http: HttpClient) {}

  list(): Observable<NotificationList> {
    return this.http.get<NotificationList>(this.baseUrl, { headers: this.quiet, withCredentials: true });
  }

  unreadCount(): Observable<number> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/unread-count`, { headers: this.quiet, withCredentials: true })
      .pipe(map(res => res.count));
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/read`, {}, { headers: this.quiet, withCredentials: true });
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/read-all`, {}, { headers: this.quiet, withCredentials: true });
  }
}
