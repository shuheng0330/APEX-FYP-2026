import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { permittedTab } from './review-task-directory';

/** Keeps same-route query navigation behind the workspace's existing edit/busy checks. */
export class ReviewTabNavigation<T extends string> {
  pending = false;
  private acceptedUrl: string;
  private acceptedTab: T | null = null;
  private connected = false;
  private destroyed = false;

  constructor(private router: Router, private route: ActivatedRoute, private destroyRef: DestroyRef,
    private available: () => readonly T[], private current: () => T | null,
    private accept: (tab: T | null) => void, private canLeave: () => boolean | Promise<boolean>) {
    this.acceptedUrl = router.url;
    destroyRef.onDestroy(() => this.destroyed = true);
  }

  initial() {
    this.acceptedTab = permittedTab(this.route.snapshot.queryParamMap.get('tab'), this.available());
    return this.acceptedTab;
  }

  connect() {
    if (this.connected) return;
    this.connected = true;
    // Wait for navigation to finish before normalising a URL or restoring a cancelled tab.
    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(event => {
      if (event instanceof NavigationEnd) this.readUrl();
    });
    if (this.router.navigated && !this.router.getCurrentNavigation()) this.readUrl();
  }

  select(tab: T) {
    if (!this.available().includes(tab) || this.pending || tab === this.current()) return;
    this.change(tab, false, false);
  }

  private readUrl() {
    if (this.pending) return;
    const requested = this.route.snapshot.queryParamMap.get('tab');
    const tab = permittedTab(requested, this.available());
    if (tab === this.current()) {
      this.acceptedUrl = this.router.url;
      this.acceptedTab = tab;
      if (requested !== null && requested !== tab) this.write(tab, true);
    } else this.change(tab, true, requested !== null && requested !== tab);
  }

  private change(tab: T | null, fromUrl: boolean, normalise: boolean) {
    if (this.pending) return;
    this.pending = true;
    const leave = this.canLeave();
    if (typeof leave === 'boolean') this.finish(leave, tab, fromUrl, normalise);
    else void leave.then(allowed => this.finish(allowed, tab, fromUrl, normalise), () => this.finish(false, tab, fromUrl, normalise));
  }

  private finish(allowed: boolean, tab: T | null, fromUrl: boolean, normalise: boolean) {
    if (this.destroyed) return;
    if (allowed) {
      this.accept(tab);
      if (!fromUrl || normalise || permittedTab(this.route.snapshot.queryParamMap.get('tab'), this.available()) !== tab) this.write(tab, fromUrl);
      else { this.acceptedUrl = this.router.url; this.acceptedTab = tab; this.pending = false; }
    } else if (fromUrl) {
      void this.router.navigateByUrl(this.acceptedUrl, { replaceUrl: true }).finally(() => this.pending = false);
    } else this.pending = false;
  }

  private write(tab: T | null, replaceUrl: boolean) {
    this.pending = true;
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab }, queryParamsHandling: 'merge', replaceUrl })
      .then(navigated => {
        if (navigated) { this.acceptedUrl = this.router.url; this.acceptedTab = tab; }
        else this.accept(this.acceptedTab);
      }, () => this.accept(this.acceptedTab))
      .finally(() => this.pending = false);
  }
}
