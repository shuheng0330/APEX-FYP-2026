import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { Track } from '../models/track.model';

@Injectable({
  providedIn: 'root',
})
export class TrackService {
  private baseUrl = environment.apiBaseUrl + "/track";

  constructor(private http: HttpClient) { }

  getAll(): Observable<Track[]> {
    return this.http.get<Track[]>(`${this.baseUrl}`);
  }

}
