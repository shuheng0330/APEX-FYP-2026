import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyAssignmentCollabComponent } from './competency-assignment-collab.component';

describe('CompetencyAssignmentCollabComponent', () => {
  let component: CompetencyAssignmentCollabComponent;
  let fixture: ComponentFixture<CompetencyAssignmentCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompetencyAssignmentCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CompetencyAssignmentCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
