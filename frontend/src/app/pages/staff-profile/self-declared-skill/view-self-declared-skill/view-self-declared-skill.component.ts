import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzEmptyModule } from 'ng-zorro-antd/empty';

import { AuthService } from '../../../../services/auth.service';
import { SkillProficiency, StaffSelfDeclaredSkill } from '../../../../models/staff.model';

@Component({
  selector: 'app-view-self-declared-skill',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, TranslateModule, NzIconModule, NzTagModule, NzEmptyModule],
  templateUrl: './view-self-declared-skill.component.html',
  styleUrl: './view-self-declared-skill.component.scss'
})
export class ViewSelfDeclaredSkillComponent {
  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';
  proficiencyColors: Record<SkillProficiency, string> = {
    [SkillProficiency.BEGINNER]: '#3E6D9C',
    [SkillProficiency.INTERMEDIATE]: '#FD841F',
    [SkillProficiency.ADVANCED]: '#E14D2A'
  };

  getProficiencyColor(proficiency: SkillProficiency): string {
    return this.proficiencyColors[proficiency] || '#808080';
  }

  data?: StaffSelfDeclaredSkill;

  constructor(private authService: AuthService) { }

  @HostListener('window:resize', ['$event'])
  onResize(event: any) {
    this.adjustDrawerWidth();
  }

  adjustDrawerWidth() {
    if (window.innerWidth <= 768) {
      this.drawerWidth = '100%';
    } else {
      this.drawerWidth = '736px';
    }
  }

  initialize(row: StaffSelfDeclaredSkill): void {
    this.adjustDrawerWidth();

    this.data = row;
    this.initialized$.next(true);
  }

  uninitialize(): void {
    this.initialized$.next(false);
  }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}




