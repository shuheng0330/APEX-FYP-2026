import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RoleAssignmentOverviewComponent } from './role-assignment-overview.component';

describe('RoleAssignmentOverviewComponent', () => {
  let component: RoleAssignmentOverviewComponent;
  let fixture: ComponentFixture<RoleAssignmentOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoleAssignmentOverviewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RoleAssignmentOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
