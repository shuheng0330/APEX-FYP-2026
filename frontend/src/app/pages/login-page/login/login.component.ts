import { Component, ElementRef, inject, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, Validators, NonNullableFormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

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
  selector: 'app-login',
  imports: [CommonModule, ReactiveFormsModule, NzCardModule, NzFormModule,
    NzIconModule, NzButtonModule, NzCheckboxModule, NzInputModule,
    TranslateModule, NzAlertModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})

export class LoginComponent implements OnInit {
  private landingPage: any = '/org-chart';
  private forgotPasswordPage: any = '/forgot-password';
  private firstTimeLoginPage: any = '/first-time-login';
  private defaultErrorTitleKey: any = "NOTIFICATION.ERR.TITLE";
  private defaultErrorMsgKey: any = "NOTIFICATION.ERR.MESSAGE";

  isPasswordVisible: boolean = false;
  hasError: boolean = false;
  errorMessage: any = '';
  errorTitle: any = '';

  private fb = inject(NonNullableFormBuilder);
  validateForm = this.fb.group({
    email: this.fb.control('', [Validators.required]),
    password: this.fb.control('', [Validators.required]),
  });

  constructor(private authService: AuthService, private loadingService: LoadingService,
    private router: Router, private translateService: TranslateService) { }

  ngOnInit(): void {

  }

  submitForm(): void {
    if (this.validateForm.valid) {
      this.isPasswordVisible = false;
      const { email, password } = this.validateForm.value;
      if (!email || !password) return;

      this.loadingService.show();

      this.authService.login(email, password)
        .subscribe({
          next: () => {
            const redirect = this.authService.redirectUrl || this.landingPage;
            console.log('Redirecting to:', redirect);
            this.authService.redirectUrl = undefined;
            this.router.navigateByUrl(redirect);
          },
          error: err => {
            this.hasError = true;
            this.errorTitle = err.error?.title || this.translateService.instant(this.defaultErrorTitleKey);
            this.errorMessage = err.error?.message || this.translateService.instant(this.defaultErrorMsgKey);
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

  forgotPassword(): void {
    this.router.navigate([this.forgotPasswordPage]);
  }

  firstTimeLogin(): void {
    this.router.navigate([this.firstTimeLoginPage]);
  }
}