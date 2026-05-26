import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewRoleAssignmentComponent } from './view-role-assignment.component';

describe('ViewRoleAssignmentComponent', () => {
  let component: ViewRoleAssignmentComponent;
  let fixture: ComponentFixture<ViewRoleAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewRoleAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewRoleAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
