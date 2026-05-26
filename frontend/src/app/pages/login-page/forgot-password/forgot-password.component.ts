import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';

import { NzCardModule } from 'ng-zorro-antd/card';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzAlertModule } from 'ng-zorro-antd/alert';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';

@Component({
  selector: 'app-forgot-password',
  imports: [CommonModule, ReactiveFormsModule, NzCardModule, NzFormModule,
    NzIconModule, NzButtonModule, NzCheckboxModule, NzInputModule, TranslateModule,
    NzAlertModule],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  private loginPage: any = '/login';
  private resetPage: any = '/reset-password';
  private profileResetPage: any = '/profile/reset-password';
  private errorTitleKey: any = "NOTIFICATION.ERR.TITLE";
  private errorMessageKey: any = "NOTIFICATION.ERR.MESSAGE";

  hasError: boolean = false;
  errorTitle: any = "";
  errorMessage: any = "";

  private fb = inject(NonNullableFormBuilder);
  validateForm = this.fb.group({
    email: this.fb.control('', [Validators.required]),
  });

  constructor(private authService: AuthService, private loadingService: LoadingService,
    private router: Router, private translateService: TranslateService, private route: ActivatedRoute,) { }

  ngOnInit(): void {
    // Check if email param exists — if yes, redirect directly to reset password page
    this.route.queryParams.subscribe(params => {
      const email = params['email'];
      if (email) {
        this.authService.forgotPassword(email, false)
          .subscribe({
            next: () => {
              this.router.navigate([this.profileResetPage], { queryParams: { email } });
            },
            error: err => {
              this.errorTitle = err.error?.title ?
                err.error?.title : this.translateService.instant(this.errorTitleKey);

              this.errorMessage = err.error?.title ?
                err.error?.message : this.translateService.instant(this.errorMessageKey);

              this.hasError = true;
            },
            complete: () => this.loadingService.hide()
          });
      }
    });
  }

  backToLogin(): void {
    this.router.navigate([this.loginPage]);
  }

  submitForm(): void {
    if (this.validateForm.valid) {
      const { email } = this.validateForm.value;
      if (!email) return;

      this.loadingService.show();
      this.authService.forgotPassword(email, false)
        .subscribe({
          next: () => {
            this.router.navigate([this.resetPage], { queryParams: { email } });
          },
          error: err => {
            this.errorTitle = err.error?.title ?
              err.error?.title : this.translateService.instant(this.errorTitleKey);

            this.errorMessage = err.error?.title ?
              err.error?.message : this.translateService.instant(this.errorMessageKey);

            this.hasError = true;
          },
          complete: () => this.loadingService.hide()
        });
    } else {
      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
    }
  }
}
