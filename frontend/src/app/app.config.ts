import { APP_INITIALIZER, ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideNzI18n, en_US } from 'ng-zorro-antd/i18n';

import { routes } from './app.routes';
import { LoadingInterceptor } from './interceptors/loading.interceptor';
import { ErrorInterceptor } from './interceptors/error.interceptor';
import { provideTranslate } from './provider/translate.provider';
import { JwtInterceptor } from './interceptors/jwtInterceptor.interceptor';
import { AuthService } from './services/auth.service';

export function initAppFactory(auth: AuthService) {
  return () => auth.getCurrentUser(false).toPromise();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([LoadingInterceptor, ErrorInterceptor, JwtInterceptor])
    ),
    provideAnimations(),
    provideNzI18n(en_US),
    provideTranslate(),
    {
      provide: APP_INITIALIZER,
      useFactory: initAppFactory,
      deps: [AuthService],
      multi: true
    }
  ]
};
