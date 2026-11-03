import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { AttitudeConfiguration, AttitudeConfigurationOptions, AttitudeConfigurationRequest, AttitudePeriodConfiguration } from '../models/attitude-configuration.model';

@Injectable({ providedIn: 'root' })
export class AttitudeConfigurationService {
  private readonly base = `${environment.apiBaseUrl}/attitude-configurations`;
  private readonly options = { withCredentials: true, headers: new HttpHeaders({ 'X-Skip-Error-Handler': 'true' }) };
  constructor(private http: HttpClient) {}
  list() { return this.http.get<AttitudeConfiguration[]>(this.base, this.options); }
  optionsList() { return this.http.get<AttitudeConfigurationOptions>(`${this.base}/options`, this.options); }
  create(request: AttitudeConfigurationRequest) { return this.http.post<AttitudeConfiguration>(this.base, request, this.options); }
  update(id: number, request: AttitudeConfigurationRequest) { return this.http.put<AttitudeConfiguration>(`${this.base}/${id}`, request, this.options); }
  copy(id: number) { return this.http.post<AttitudeConfiguration>(`${this.base}/${id}/copy`, {}, this.options); }
  publish(id: number) { return this.http.post<AttitudeConfiguration>(`${this.base}/${id}/publish`, {}, this.options); }
  period(id: number) { return this.http.get<AttitudePeriodConfiguration>(`${this.base}/periods/${id}`, this.options); }
  bindInitially(periodId: number, configurationId: number) {
    return this.http.post<AttitudePeriodConfiguration>(`${this.base}/periods/${periodId}/bind`, { configurationId }, this.options);
  }
}
