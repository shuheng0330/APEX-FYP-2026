import { Component, Input, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';

@Component({
  selector: 'app-notification-target', standalone: true, imports: [TranslateModule, NzButtonModule],
  template: `@if (unavailable) {
    <p role="status">{{ 'NOTIFICATIONS.TARGET_UNAVAILABLE' | translate }}</p>
    <button nz-button type="button" (click)="returnToWorkspace()">{{ 'NOTIFICATIONS.RETURN_WORKSPACE' | translate }}</button>
  }`
})
export class NotificationTargetComponent {
  @Input() unavailable = false;
  private router = inject(Router, { optional: true });
  returnToWorkspace() {
    if (!this.router) return;
    const tree = this.router.parseUrl(this.router.url); tree.queryParams = { notice: `workspace-${Date.now()}` }; tree.fragment = null;
    void this.router.navigateByUrl(tree, { onSameUrlNavigation: 'reload' });
  }
}
