import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyAssignmentOverviewComponent } from './competency-assignment-overview.component';

describe('CompetencyAssignmentOverviewComponent', () => {
  let component: CompetencyAssignmentOverviewComponent;
  let fixture: ComponentFixture<CompetencyAssignmentOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompetencyAssignmentOverviewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CompetencyAssignmentOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
