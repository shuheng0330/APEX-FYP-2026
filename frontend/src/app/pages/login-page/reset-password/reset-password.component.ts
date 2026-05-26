import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { NzCardModule } from 'ng-zorro-antd/card';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzInputOtpComponent } from 'ng-zorro-antd/input';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzMessageService } from 'ng-zorro-antd/message';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';


interface PasswordValidator {
  id: number;
  text: string;
  isPassed: boolean;
  isLoading?: boolean;
}

@Component({
  selector: 'app-reset-password',
  imports: [CommonModule, ReactiveFormsModule, NzCardModule, NzFormModule, NzIconModule,
    NzButtonModule, NzCheckboxModule, NzInputModule, NzInputOtpComponent,
    TranslateModule, NzAlertModule],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})

export class ResetPasswordComponent {
  private readonly oneSec: number = 1000;
  private readonly countDownTime: number = 120;
  private readonly loginPage: any = '/login';
  private readonly errorTitleKey: any = 'NOTIFICATION.ERR.TITLE';
  private readonly errorMessageKey: any = 'NOTIFICATION.ERR.MESSAGE';
  private readonly passwordMinValidatorKey: any = 'AUTH.RESET_PASSWORD.VALIDATOR.MIN';
  private readonly passwordUpperCaseValidatorKey: any = 'AUTH.RESET_PASSWORD.VALIDATOR.UPPER_CASE';
  private readonly passwordLowerCaseValidatorKey: any = 'AUTH.RESET_PASSWORD.VALIDATOR.LOWER_CASE';
  private readonly passwordNumValidatorKey: any = 'AUTH.RESET_PASSWORD.VALIDATOR.NUM';
  private readonly invalidPasswordTitleKey: any = 'AUTH.RESET_PASSWORD.ERR.INVALID_PASSWORD_TITLE';
  private readonly invalidPasswordMessageKey: any = 'AUTH.RESET_PASSWORD.ERR.INVALID_PASSWORD_MESSAGE';
  private readonly successMessageKey: any = 'AUTH.RESET_PASSWORD.SUCCESS';
  private passwordMinValidator: string = "";
  private passwordUpperCaseValidator: string = "";
  private passwordLowerCaseValidator: string = "";
  private passwordNumValidator: string = "";
  private typingTimeout: any;
  private resendInterval: any;
  private fb = inject(NonNullableFormBuilder);

  validateForm = this.fb.group({
    password: this.fb.control('', [Validators.required]),
    otp: this.fb.control('', [Validators.required]),
    email: this.fb.control('', [Validators.required]),
  });

  email: string = "-";
  isResent: boolean = false;
  isValidate: boolean = false;
  listOfValidator: Array<PasswordValidator> = [];
  isCountDown: boolean = true;
  remainder: number = this.countDownTime;
  isPasswordVisible: boolean = false;
  resendHasError: boolean = false;
  resendErrorMessage: any = '';
  resendErrorTitle: any = '';
  newPasswordHasError: boolean = false;
  newPasswordErrorMessage: any = '';
  newPasswordErrorTitle: any = '';
  redColorCode: any = '#E6173F';

  constructor(private authService: AuthService, private loadingService: LoadingService,
    private router: Router, private route: ActivatedRoute,
    private translateService: TranslateService, private message: NzMessageService) { }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const email = params['email'];
      this.validateForm.get('email')?.setValue(email);
    });

    this.validateForm.get('otp')?.reset();
    this.validateForm.get('password')?.reset();

    this.passwordMinValidator = this.translateService.instant(this.passwordMinValidatorKey);
    this.passwordUpperCaseValidator = this.translateService.instant(this.passwordUpperCaseValidatorKey);
    this.passwordLowerCaseValidator = this.translateService.instant(this.passwordLowerCaseValidatorKey);
    this.passwordNumValidator = this.translateService.instant(this.passwordNumValidatorKey);

    this.listOfValidator = [
      { id: 1, text: this.passwordMinValidator, isPassed: false, isLoading: false },
      { id: 2, text: this.passwordUpperCaseValidator, isPassed: false, isLoading: false },
      { id: 3, text: this.passwordLowerCaseValidator, isPassed: false, isLoading: false },
      { id: 4, text: this.passwordNumValidator, isPassed: false, isLoading: false },
    ];

    this.startCountdown();
  }

  startCountdown(): void {
    clearInterval(this.resendInterval);
    this.remainder = this.countDownTime;
    this.isCountDown = true;

    this.resendInterval = setInterval(() => {
      if (this.remainder > 0) {
        this.remainder--;
      } else {
        clearInterval(this.resendInterval);
        this.isCountDown = false;
      }
    }, this.oneSec);
  }

  checkPassword(password: string): boolean {
    return password.length >= 6 && /[A-Z]/.test(password)
      && /[a-z]/.test(password) && /[0-9]/.test(password);
  }

  submitForm(): void {
    if (this.validateForm.valid) {
      const { password, otp, email } = this.validateForm.value;

      if (!password || !otp || !email) return;

      if (this.checkPassword(password)) {
        this.loadingService.show();

        this.authService.resetPassword(email, password, otp, false)
          .subscribe({
            next: () => {
              this.message.create('success', this.translateService.instant(this.successMessageKey), {
                nzDuration: 6000
              });

              this.router.navigate([this.loginPage]);
              this.newPasswordHasError = false;
            },
            error: err => {
              this.newPasswordHasError = true;
              this.newPasswordErrorTitle = err.error?.title || this.translateService.instant(this.errorTitleKey);
              this.newPasswordErrorMessage = err.error?.message || this.translateService.instant(this.errorMessageKey);
            },
            complete: () => this.loadingService.hide()
          });
      } else {
        this.newPasswordHasError = true;
        this.newPasswordErrorTitle = this.translateService.instant(this.invalidPasswordTitleKey);
        this.newPasswordErrorMessage = this.translateService.instant(this.invalidPasswordMessageKey);
      }
    } else {
      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
    }
  }

  resendOtp(): void {
    const email = this.validateForm.get('email')?.value;

    if (email) {
      this.loadingService.show();
      this.authService.forgotPassword(email, false)
        .subscribe({
          next: () => {
            this.resendHasError = false;
            this.isResent = true;
          },
          error: err => {
            this.resendErrorTitle = err.error?.title ?
              err.error?.title : this.translateService.instant(this.errorTitleKey);

            this.resendErrorMessage = err.error?.title ?
              err.error?.message : this.translateService.instant(this.errorMessageKey);

            this.resendHasError = true;
          },
          complete: () => this.loadingService.hide()
        });

    } else {
      this.resendErrorTitle = this.translateService.instant(this.errorTitleKey);
      this.resendErrorMessage = this.translateService.instant(this.errorMessageKey);

      this.resendHasError = true;
    }
  }

  onPasswordInputChange(): void {
    const password = this.validateForm!.get("password")!.value;

    this.listOfValidator.forEach(v => v.isLoading = true);

    clearTimeout(this.typingTimeout);

    this.typingTimeout = setTimeout(() => {
      this.listOfValidator = [
        { id: 1, text: this.passwordMinValidator, isPassed: password.length >= 6, isLoading: false },
        { id: 2, text: this.passwordUpperCaseValidator, isPassed: /[A-Z]/.test(password), isLoading: false },
        { id: 3, text: this.passwordLowerCaseValidator, isPassed: /[a-z]/.test(password), isLoading: false },
        { id: 4, text: this.passwordNumValidator, isPassed: /[0-9]/.test(password), isLoading: false },
      ];
    }, 200);
  }

}
