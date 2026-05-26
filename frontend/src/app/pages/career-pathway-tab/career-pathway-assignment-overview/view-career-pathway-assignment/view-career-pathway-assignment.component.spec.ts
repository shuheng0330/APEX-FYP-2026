import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewCareerPathwayAssignmentComponent } from './view-career-pathway-assignment.component';

describe('ViewCareerPathwayAssignmentComponent', () => {
  let component: ViewCareerPathwayAssignmentComponent;
  let fixture: ComponentFixture<ViewCareerPathwayAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewCareerPathwayAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewCareerPathwayAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
