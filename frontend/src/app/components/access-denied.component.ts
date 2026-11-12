import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzResultModule } from 'ng-zorro-antd/result';

@Component({
  standalone: true,
  imports: [RouterLink, TranslateModule, NzButtonModule, NzResultModule],
  template: `<nz-result nzStatus="403" [nzTitle]="'ACCESS_DENIED.TITLE' | translate" [nzSubTitle]="'ACCESS_DENIED.HELP' | translate">
    <div nz-result-extra><a nz-button nzType="primary" routerLink="/org-chart">{{ 'ACCESS_DENIED.BACK' | translate }}</a></div>
  </nz-result>`
})
export class AccessDeniedComponent {}
