import { Component, OnInit } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';

import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzFloatButtonModule } from 'ng-zorro-antd/float-button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';

import { SideMenuComponent } from './components/side-menu/side-menu.component';
import { MobileHeaderComponent } from './components/mobile-header/mobile-header.component';
import { EvaluationCycleDrawerComponent } from './components/evaluation-cycle-drawer/evaluation-cycle-drawer.component';

import { AuthService } from './services/auth.service';
import { LoadingService } from './services/loading.service';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [RouterOutlet, CommonModule, NzSpinModule, NzDrawerModule,
        NzIconModule, NzFloatButtonModule, SideMenuComponent, MobileHeaderComponent, EvaluationCycleDrawerComponent],
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})

export class AppComponent implements OnInit {
    isLoading$: any;
    isLoggedIn$: any;
    isCollapsed = true;
    isDrawerVisible = false;
    isEvaluationDrawerVisible = false;

    constructor(private loadingService: LoadingService, private auth: AuthService,
        public router: Router) {
        this.isLoading$ = this.loadingService.loading$;
        this.isLoggedIn$ = this.auth.loggedIn$;
    }

    ngOnInit(): void {

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
}