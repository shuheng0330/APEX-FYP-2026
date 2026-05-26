import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MapsLoaderService {
  private scriptLoaded = false;

  load(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.scriptLoaded) return resolve();

      const script = document.createElement('script');
      const params = new URLSearchParams({
        key: environment.googleMaps.apiKey,
        libraries: environment.googleMaps.libraries.join(','),
        v: environment.googleMaps.version,
        loading: 'async'
      });

      script.src = `https://maps.googleapis.com/maps/api/js?${params.toString()}`;
      script.async = true;
      script.defer = true;
      script.onload = () => {
        this.scriptLoaded = true;
        resolve();
      };
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }
}
