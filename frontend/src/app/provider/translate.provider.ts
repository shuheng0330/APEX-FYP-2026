import { APP_INITIALIZER, importProvidersFrom, makeEnvironmentProviders, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateLoader, TranslateService } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

export function initTranslateFactory() {
  return () => {
    const translate = inject(TranslateService);
    translate.setDefaultLang('en');
    return translate.use('en').toPromise();
  };
}

export function provideTranslate() {
  return makeEnvironmentProviders([
    importProvidersFrom(
      TranslateModule.forRoot({
        loader: {
          provide: TranslateLoader,
          useFactory: HttpLoaderFactory,
          deps: [HttpClient]
        }
      })
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: initTranslateFactory,
      multi: true
    }
  ]);
}
