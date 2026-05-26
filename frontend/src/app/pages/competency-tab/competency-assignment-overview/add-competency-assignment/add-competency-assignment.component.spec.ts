import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddCompetencyAssignmentComponent } from './add-competency-assignment.component';

describe('AddCompetencyAssignmentComponent', () => {
  let component: AddCompetencyAssignmentComponent;
  let fixture: ComponentFixture<AddCompetencyAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddCompetencyAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddCompetencyAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
