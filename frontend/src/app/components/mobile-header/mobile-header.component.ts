import { Component, EventEmitter, Output } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';

import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMenuModule } from 'ng-zorro-antd/menu';

@Component({
  selector: 'app-mobile-header',
  imports: [NzIconModule, NzMenuModule, RouterModule, TranslateModule, CommonModule],
  templateUrl: './mobile-header.component.html',
  styleUrl: './mobile-header.component.scss'
})
export class MobileHeaderComponent {
  @Output() toggleDrawer = new EventEmitter<void>();

  constructor(private router: Router, private authService: AuthService) { }

  onProfileClick() {
    this.router.navigate(['/profile']);
  }

  onLogout() {
    this.authService.logout().subscribe(() => this.router.navigate(['/login']));
  }
}

