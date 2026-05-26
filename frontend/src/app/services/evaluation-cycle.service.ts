import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface EvaluationCycleDto {
    id?: number;
    startDate: string;
    endDate: string;
    status?: 'UPCOMING' | 'OPEN' | 'CLOSED';
    allDepartments?: boolean;
    createdAt?: string;
    createdBy?: string;
    openedAt?: string;
    closedAt?: string;
    updatedAt?: string;
    updatedBy?: string;
}

@Injectable({
    providedIn: 'root'
})
export class EvaluationCycleService {
    private apiUrl = `${environment.apiBaseUrl}/evaluation-cycle`;

    constructor(private http: HttpClient) { }

    getCurrentCycle(): Observable<EvaluationCycleDto> {
        return this.http.get<EvaluationCycleDto>(`${this.apiUrl}/current`);
    }

    openNewCycle(dto: EvaluationCycleDto): Observable<EvaluationCycleDto> {
        return this.http.post<EvaluationCycleDto>(`${this.apiUrl}`, dto);
    }

    updateCycle(id: number, dto: EvaluationCycleDto): Observable<EvaluationCycleDto> {
        return this.http.put<EvaluationCycleDto>(`${this.apiUrl}/update/${id}`, dto);
    }

    getHistory(): Observable<EvaluationCycleDto[]> {
        return this.http.get<EvaluationCycleDto[]>(`${this.apiUrl}/history`);
    }
}
