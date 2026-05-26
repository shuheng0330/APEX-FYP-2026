import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from "@angular/common/http";
import { inject } from "@angular/core";
import { catchError, tap, throwError } from "rxjs";
import { TranslateService } from "@ngx-translate/core";

import { NzNotificationPlacement, NzNotificationService } from 'ng-zorro-antd/notification';

export const ErrorInterceptor: HttpInterceptorFn = (req, next) => {
    const notification = inject(NzNotificationService);
    const translate = inject(TranslateService);
    const errorTitleKey = 'NOTIFICATION.ERR.TITLE';
    const errorMessageKey = 'NOTIFICATION.ERR.MESSAGE';
    const successTitleKey = 'NOTIFICATION.SUCCESS.TITLE';
    const successMessageKey = 'NOTIFICATION.SUCCESS.MESSAGE';
    const position: NzNotificationPlacement = 'topLeft';

    const skipErrorHandler = req.headers.get('X-Skip-Error-Handler');
    const showSuccess = req.headers.get('X-Show-Success');

    return next(req).pipe(
        tap((event) => {
            if (event instanceof HttpResponse && showSuccess === 'true') {
                let successTitle = translate.instant(successTitleKey);
                let successMessage = translate.instant(successMessageKey);

                const body: any = event.body;

                if (body?.title) {
                    successTitle = body.title;
                }

                if (body?.message) {
                    successMessage = body.message;
                }

                notification.create(
                    'success',
                    successTitle,
                    successMessage,
                    {
                        nzPlacement: position,
                        nzPauseOnHover: true,
                        nzDuration: 5000
                    }
                );
            }
        }),
        catchError((error: HttpErrorResponse) => {

            if (!skipErrorHandler || skipErrorHandler === 'false') {
                let errorTitle = translate.instant(errorTitleKey);
                let errorMessage = translate.instant(errorMessageKey);

                if (error.error?.message) {
                    errorMessage = error.error?.message;
                }

                if (error.error?.title) {
                    errorTitle = error.error?.title;
                }

                notification.create(
                    'error',
                    errorTitle,
                    errorMessage,
                    {
                        nzPlacement: position,
                        nzPauseOnHover: true,
                        nzDuration: 10000
                    }
                );
            }
            return throwError(() => error);
        }
        ));
}