import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddRoleAssignmentComponent } from './add-role-assignment.component';

describe('AddRoleAssignmentComponent', () => {
  let component: AddRoleAssignmentComponent;
  let fixture: ComponentFixture<AddRoleAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddRoleAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddRoleAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
