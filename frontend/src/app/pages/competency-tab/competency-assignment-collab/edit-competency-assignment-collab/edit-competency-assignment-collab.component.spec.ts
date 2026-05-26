import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCompetencyAssignmentCollabComponent } from './edit-competency-assignment-collab.component';

describe('EditCompetencyAssignmentCollabComponent', () => {
  let component: EditCompetencyAssignmentCollabComponent;
  let fixture: ComponentFixture<EditCompetencyAssignmentCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditCompetencyAssignmentCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditCompetencyAssignmentCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
