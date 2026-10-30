import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationService } from '../../services/notification.service';
import { AppNotification } from '../../models/notification.model';

describe('Notification bell navigation and read state', () => {
  let bell: NotificationBellComponent; let api: jasmine.SpyObj<NotificationService>; let router: Router;
  const item = (link: string | null = '/my-performance/my-assessments?reviewPeriodId=2&tab=attitude'): AppNotification => ({
    id: 7, type: 'ATTITUDE_ASSESSMENT', title: 'Evaluation ready', body: 'Open your evaluation.', link, createdAt: '2026-10-26T00:00:00Z', read: false
  });
  beforeEach(() => {
    api = jasmine.createSpyObj('notifications', ['markRead', 'markAllRead', 'list', 'unreadCount']);
    api.markRead.and.returnValue(of(void 0)); api.markAllRead.and.returnValue(of(void 0));
    api.list.and.returnValue(of({ unread: 1, items: [item()] })); api.unreadCount.and.returnValue(of(1));
    TestBed.configureTestingModule({ imports: [NotificationBellComponent, TranslateModule.forRoot()], providers: [
      provideRouter([]), { provide: NotificationService, useValue: api }
    ] });
    bell = TestBed.createComponent(NotificationBellComponent).componentInstance; router = TestBed.inject(Router); bell.unread = 1;
  });
  it('marks a linked item read only after successful navigation', async () => {
    let finish!: (value: boolean) => void;
    const navigate = spyOn(router, 'navigateByUrl').and.returnValue(new Promise(resolve => finish = resolve));
    const notice = item(); const opening = bell.openItem(notice);
    expect(api.markRead).not.toHaveBeenCalled(); expect(notice.read).toBeFalse(); expect(bell.navigating).toBeTrue();
    finish(true); await opening;
    expect(api.markRead).toHaveBeenCalledOnceWith(7); expect(notice.read).toBeTrue(); expect(bell.unread).toBe(0);
    expect(router.serializeUrl(navigate.calls.mostRecent().args[0] as ReturnType<Router['parseUrl']>)).toContain('reviewPeriodId=2');
  });
  it('leaves cancelled/permission-denied navigation unread', async () => {
    spyOn(router, 'navigateByUrl').and.resolveTo(false); const notice = item(); await bell.openItem(notice);
    expect(api.markRead).not.toHaveBeenCalled(); expect(notice.read).toBeFalse(); expect(bell.unread).toBe(1);
  });
  it('keeps unread state on a navigation failure or invalid URL', async () => {
    const navigate = spyOn(router, 'navigateByUrl').and.rejectWith(new Error('unavailable'));
    await bell.openItem(item()); expect(bell.navigationError).toBeTruthy();
    await bell.openItem(item('//external.test')); expect(navigate).toHaveBeenCalledTimes(1); expect(api.markRead).not.toHaveBeenCalled();
  });
  it('prevents duplicate clicks while navigation is pending', async () => {
    let finish!: (value: boolean) => void;
    const navigate = spyOn(router, 'navigateByUrl').and.returnValue(new Promise(resolve => finish = resolve));
    const notice = item(); const opening = bell.openItem(notice); await bell.openItem(notice);
    expect(navigate).toHaveBeenCalledTimes(1); finish(true); await opening; expect(api.markRead).toHaveBeenCalledTimes(1);
  });
  it('does not optimistically mark a notification read before the API succeeds', async () => {
    const saved = new Subject<void>(); api.markRead.and.returnValue(saved); const notice = item(null);
    await bell.openItem(notice); expect(notice.read).toBeFalse(); saved.next(); saved.complete(); expect(notice.read).toBeTrue();
  });
  it('reloads server read state after a failed mark-read request', async () => {
    spyOn(router, 'navigateByUrl').and.resolveTo(true); api.markRead.and.returnValue(throwError(() => new Error('offline')));
    await bell.openItem(item()); expect(api.list).toHaveBeenCalled(); expect(bell.unread).toBe(1);
  });
  it('preserves explicit mark-all-read', () => {
    const notice = item(); bell.items = [notice]; bell.markAllRead(); expect(api.markAllRead).toHaveBeenCalledTimes(1);
    expect(notice.read).toBeTrue(); expect(bell.unread).toBe(0);
  });
});
