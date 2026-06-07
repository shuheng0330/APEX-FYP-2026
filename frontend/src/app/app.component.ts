import { Component, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';

import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzFloatButtonModule } from 'ng-zorro-antd/float-button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';

import { SideMenuComponent } from './components/side-menu/side-menu.component';
import { EvaluationCycleDrawerComponent } from './components/evaluation-cycle-drawer/evaluation-cycle-drawer.component';
import { AppTopHeaderComponent } from './components/app-top-header/app-top-header.component';

import { AuthService } from './services/auth.service';
import { LoadingService } from './services/loading.service';
import { filter } from 'rxjs';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [RouterOutlet, CommonModule, NzSpinModule, NzDrawerModule,
        NzIconModule, NzFloatButtonModule, SideMenuComponent, AppTopHeaderComponent, EvaluationCycleDrawerComponent],
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})

export class AppComponent implements OnInit {
    isLoading$: any;
    isLoggedIn$: any;
    isCollapsed = true;
    isDrawerVisible = false;
    isEvaluationDrawerVisible = false;
    isAuthRoute = false;

    constructor(private loadingService: LoadingService, private auth: AuthService,
        public router: Router) {
        this.isLoading$ = this.loadingService.loading$;
        this.isLoggedIn$ = this.auth.loggedIn$;
    }

    ngOnInit(): void {
        this.updateAuthRoute(this.router.url);
        this.router.events.pipe(
            filter(event => event instanceof NavigationEnd)
        ).subscribe(event => this.updateAuthRoute((event as NavigationEnd).urlAfterRedirects));
    }

    openDrawer(): void {
        this.isDrawerVisible = true;
        this.isCollapsed = false;
    }

    closeDrawer(): void {
        this.isDrawerVisible = false;
        this.isCollapsed = true;
    }

    onOpenEvaluation(): void {
        this.isEvaluationDrawerVisible = true;
    }

    private updateAuthRoute(url: string): void {
        this.isAuthRoute = ['/login', '/first-time-login', '/forgot-password', '/reset-password',
            '/profile/forgot-password', '/profile/reset-password']
            .some(route => url.startsWith(route));
    }
}
