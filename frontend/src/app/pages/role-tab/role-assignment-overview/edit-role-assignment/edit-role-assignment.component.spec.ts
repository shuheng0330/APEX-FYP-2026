import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditRoleAssignmentComponent } from './edit-role-assignment.component';

describe('EditRoleAssignmentComponent', () => {
  let component: EditRoleAssignmentComponent;
  let fixture: ComponentFixture<EditRoleAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditRoleAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditRoleAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
