import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CareerPathwayAssignmentOverviewComponent } from './career-pathway-assignment-overview.component';

describe('CareerPathwayAssignmentOverviewComponent', () => {
  let component: CareerPathwayAssignmentOverviewComponent;
  let fixture: ComponentFixture<CareerPathwayAssignmentOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CareerPathwayAssignmentOverviewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CareerPathwayAssignmentOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
