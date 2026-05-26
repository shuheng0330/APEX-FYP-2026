import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddCompetencyAssignmentCollabComponent } from './add-competency-assignment-collab.component';

describe('AddCompetencyAssignmentCollabComponent', () => {
  let component: AddCompetencyAssignmentCollabComponent;
  let fixture: ComponentFixture<AddCompetencyAssignmentCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddCompetencyAssignmentCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddCompetencyAssignmentCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
