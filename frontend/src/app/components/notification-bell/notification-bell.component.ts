import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, exhaustMap, filter, finalize, of, timer } from 'rxjs';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzPopoverModule } from 'ng-zorro-antd/popover';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NotificationService } from '../../services/notification.service';
import { AppNotification } from '../../models/notification.model';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { localNotificationUrl } from '../../services/notification-navigation';

const REFRESH_SECONDS = 30;

/**
 * The bell in the top bar (NFR-016, NFR-008): the unread count, refreshed every 30 seconds while the tab is
 * visible, and the newest notifications. Linked items become read after successful navigation.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule, TranslateModule, NzBadgeModule, NzButtonModule, NzEmptyModule, NzIconModule, NzPopoverModule, NzSpinModule],
  templateUrl: './notification-bell.component.html',
  styleUrls: ['./notification-bell.component.scss']
})
export class NotificationBellComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  unread = 0;
  items: AppNotification[] = [];
  open = false;
  loading = false;
  failed = false;
  navigating = false;
  navigationError = '';
  private navigationSequence = 0;
  private reading = new Set<number>();

  constructor(private notifications: NotificationService, private router: Router, private translate: TranslateService) {}

  ngOnInit(): void {
    timer(0, REFRESH_SECONDS * 1000).pipe(
      filter(tick => tick === 0 || !document.hidden),
      exhaustMap(() => this.notifications.unreadCount().pipe(catchError(() => of(null)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(count => {
      if (count !== null) {
        this.unread = count;
      }
    });
  }

  onOpenChange(open: boolean): void {
    this.open = open;
    if (open) {
      this.load();
    }
  }

  load(): void {
    this.loading = true;
    this.failed = false;
    this.notifications.list().subscribe({
      next: list => {
        this.items = list.items;
        this.unread = list.unread;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.failed = true;
      }
    });
  }

  async openItem(item: AppNotification): Promise<void> {
    if (this.navigating || this.reading.has(item.id)) return;
    this.navigationError = '';
    if (item.link) {
      if (!localNotificationUrl(item.link)) { this.navigationError = this.translate.instant('NOTIFICATIONS.OPEN_ERROR'); return; }
      this.navigating = true;
      try {
        const target = this.router.parseUrl(item.link);
        target.queryParams = { ...target.queryParams, notice: `${item.id}-${Date.now()}-${++this.navigationSequence}` };
        if (item.type === 'KPI_CHECKPOINT_SCORE') target.queryParams['view'] = 'breakdown';
        const navigated = await this.router.navigateByUrl(target, { onSameUrlNavigation: 'reload' });
        if (!navigated) return;
        this.open = false;
      } catch { this.navigationError = this.translate.instant('NOTIFICATIONS.OPEN_ERROR'); return; }
      finally { this.navigating = false; }
    }
    if (item.read) return;
    this.reading.add(item.id);
    this.notifications.markRead(item.id).pipe(finalize(() => this.reading.delete(item.id)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { item.read = true; this.items.forEach(row => { if (row.id === item.id) row.read = true; }); this.unread = Math.max(0, this.unread - 1); },
      error: () => this.load()
    });
  }

  markAllRead(): void {
    this.items.forEach(i => i.read = true);
    this.unread = 0;
    this.notifications.markAllRead().subscribe({ error: () => this.load() });
  }

  /** "Just now", "5 min ago", "3 h ago", "Yesterday", or the date. */
  timeAgo(value: string): string {
    const minutes = Math.floor((Date.now() - new Date(value).getTime()) / 60_000);
    if (minutes < 1) return this.translate.instant('NOTIFICATIONS.JUST_NOW');
    if (minutes < 60) return this.translate.instant('NOTIFICATIONS.MINUTES_AGO', { count: minutes });
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return this.translate.instant('NOTIFICATIONS.HOURS_AGO', { count: hours });
    if (hours < 48) return this.translate.instant('NOTIFICATIONS.YESTERDAY');
    return new Date(value).toLocaleDateString('en-MY', { day: 'numeric', month: 'short', year: 'numeric' });
  }
}
