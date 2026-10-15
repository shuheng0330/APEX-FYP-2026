import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, exhaustMap, filter, of, timer } from 'rxjs';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzPopoverModule } from 'ng-zorro-antd/popover';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NotificationService } from '../../services/notification.service';
import { AppNotification } from '../../models/notification.model';

const REFRESH_SECONDS = 30;

/**
 * The bell in the top bar (NFR-016, NFR-008): the unread count, refreshed every 30 seconds while the tab is
 * visible, and the newest notifications. Opening one marks it read and goes to its page.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule, NzBadgeModule, NzButtonModule, NzEmptyModule, NzIconModule, NzPopoverModule, NzSpinModule],
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

  constructor(private notifications: NotificationService, private router: Router) {}

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

  openItem(item: AppNotification): void {
    if (!item.read) {
      item.read = true;
      this.unread = Math.max(0, this.unread - 1);
      this.notifications.markRead(item.id).subscribe({ error: () => this.load() });
    }
    if (item.link) {
      this.open = false;
      this.router.navigateByUrl(item.link);
    }
  }

  markAllRead(): void {
    this.items.forEach(i => i.read = true);
    this.unread = 0;
    this.notifications.markAllRead().subscribe({ error: () => this.load() });
  }

  /** "Just now", "5 min ago", "3 h ago", "Yesterday", or the date. */
  timeAgo(value: string): string {
    const minutes = Math.floor((Date.now() - new Date(value).getTime()) / 60_000);
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes} min ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} h ago`;
    if (hours < 48) return 'Yesterday';
    return new Date(value).toLocaleDateString('en-MY', { day: 'numeric', month: 'short', year: 'numeric' });
  }
}
