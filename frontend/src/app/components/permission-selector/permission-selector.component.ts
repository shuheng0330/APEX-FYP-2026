import { Component, forwardRef, inject, Input, OnChanges } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzInputModule } from 'ng-zorro-antd/input';
import { TranslatedAuthority } from '../../models/access-control.model';
import { isHiddenPermission, permissionCatalogue, PermissionGroup, permissionGroups, permissionPresentation } from './permission-presentation';

type PermissionId = string | number;

@Component({
  selector: 'app-permission-selector',
  imports: [FormsModule, TranslateModule, NzCheckboxModule, NzCollapseModule, NzInputModule],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PermissionSelectorComponent), multi: true }],
  templateUrl: './permission-selector.component.html',
  styleUrl: './permission-selector.component.scss'
})
export class PermissionSelectorComponent implements ControlValueAccessor, OnChanges {
  @Input() permissions: TranslatedAuthority[] = [];
  @Input() idType: 'number' | 'string' = 'string';
  private readonly translate = inject(TranslateService);
  readonly groups = permissionGroups;
  values: PermissionId[] = [];
  query = '';
  disabled = false;
  expanded = new Set<PermissionGroup>();
  private onChange: (value: PermissionId[]) => void = () => {};
  private onTouched: () => void = () => {};

  ngOnChanges(): void { this.expandSelected(); }
  writeValue(value: PermissionId[] | null): void {
    this.values = [...(value ?? [])];
    this.query = '';
    this.expandSelected();
  }
  registerOnChange(fn: (value: PermissionId[]) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.disabled = disabled; }
  get presented(): TranslatedAuthority[] {
    return this.permissions.filter(p => !isHiddenPermission(p)).map(p => permissionPresentation(p, this.translate));
  }
  get visibleSelectedCount(): number { return this.presented.filter(p => this.selected(p)).length; }
  get hasHiddenGrants(): boolean { return this.permissions.some(p => isHiddenPermission(p) && this.selected(p)); }
  members(group: PermissionGroup): TranslatedAuthority[] {
    return this.presented.filter(p => (permissionCatalogue[p.name] ?? 'OTHER') === group);
  }
  visible(group: PermissionGroup): TranslatedAuthority[] {
    const query = this.query.trim().toLocaleLowerCase();
    return this.members(group).filter(p => !query || `${p.label} ${p.description}`.toLocaleLowerCase().includes(query));
  }
  selected(permission: TranslatedAuthority): boolean { return this.values.some(id => String(id) === String(permission.id)); }
  count(group: PermissionGroup): number { return this.members(group).filter(p => this.selected(p)).length; }
  get hasMatches(): boolean { return this.groups.some(g => this.visible(g).length > 0); }
  toggle(permission: TranslatedAuthority, checked: boolean): void {
    if (this.disabled || permission.id === undefined || isHiddenPermission(permission)) return;
    if (checked && !this.selected(permission)) {
      this.values = [...this.values, this.idType === 'number' ? permission.id : String(permission.id)];
    } else if (!checked) {
      this.values = this.values.filter(id => String(id) !== String(permission.id));
    }
    this.onTouched();
    this.onChange([...this.values]);
  }
  setExpanded(group: PermissionGroup, active: boolean): void {
    if (this.query.trim()) return;
    if (active) this.expanded.add(group); else this.expanded.delete(group);
  }
  private expandSelected(): void {
    this.expanded = new Set(this.groups.filter(g => this.count(g) > 0));
  }
}
