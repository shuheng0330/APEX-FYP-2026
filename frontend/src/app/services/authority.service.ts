import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { DepartmentPermission } from '../models/access-control.model';

@Injectable({
    providedIn: 'root',
})
export class AuthorityService {
    private baseUrl = environment.apiBaseUrl + '/auth';

    constructor(private http: HttpClient) { }

    getStaffAccessControlOverview(): Observable<DepartmentPermission> {
        return this.http.get<DepartmentPermission>(`${this.baseUrl}/access-control-overview`, { withCredentials: true });
    }

    grantAccess(orgChartId: number, roleId: number, selectedAuthorities: number[]): Observable<void> {
        return this.http.post<void>(`${this.baseUrl}/grant-access`, { orgChartId, roleId, selectedAuthorities }, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    editGrantedAccess(orgChartId: number, roleId: number, selectedAuthorities: string[]): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/edit-granted-access`, { orgChartId, roleId, selectedAuthorities }, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    deleteGrantedAccess(orgChartId: number, roleId: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/delete`, {
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' }),
            params: { orgChartId, roleId }
        });
    }

    bulkDeleteGrantedAccess(roleIds: number[]): Observable<void> {
        return this.http.request<void>('delete', `${this.baseUrl}/bulk-delete`, {
            body: roleIds,
            withCredentials: true,
            headers: new HttpHeaders({ 'X-Show-Success': 'true' })
        });
    }

    export(): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.baseUrl}/export`, {
            withCredentials: true,
            responseType: 'blob',
            observe: 'response'
        });
    }

    import(file: File): Observable<void> {
        const formData = new FormData();
        formData.append('file', file);

        return this.http.post<void>(`${this.baseUrl}/import`, formData, {
            withCredentials: true,
            headers: new HttpHeaders({
                'X-Show-Success': 'true'
            })
        });
    }
}
