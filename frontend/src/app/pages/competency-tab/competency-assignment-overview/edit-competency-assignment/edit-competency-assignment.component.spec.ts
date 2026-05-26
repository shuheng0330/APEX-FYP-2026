import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCompetencyAssignmentComponent } from './edit-competency-assignment.component';

describe('EditCompetencyAssignmentComponent', () => {
  let component: EditCompetencyAssignmentComponent;
  let fixture: ComponentFixture<EditCompetencyAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditCompetencyAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditCompetencyAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
