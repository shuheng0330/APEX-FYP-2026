import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewCompetencyAssignmentCollabComponent } from './view-competency-assignment-collab.component';

describe('ViewCompetencyAssignmentCollabComponent', () => {
  let component: ViewCompetencyAssignmentCollabComponent;
  let fixture: ComponentFixture<ViewCompetencyAssignmentCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewCompetencyAssignmentCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewCompetencyAssignmentCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
