import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewCompetencyAssignmentComponent } from './view-competencya-assignment.component';

describe('ViewCompetencyaAssignmentComponent', () => {
  let component: ViewCompetencyAssignmentComponent;
  let fixture: ComponentFixture<ViewCompetencyAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewCompetencyAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewCompetencyAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
