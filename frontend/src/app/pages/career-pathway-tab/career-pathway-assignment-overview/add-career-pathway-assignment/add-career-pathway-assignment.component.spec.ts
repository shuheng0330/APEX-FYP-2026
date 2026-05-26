import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddCareerPathwayAssignmentComponent } from './add-career-pathway-assignment.component';

describe('AddCareerPathwayAssignmentComponent', () => {
  let component: AddCareerPathwayAssignmentComponent;
  let fixture: ComponentFixture<AddCareerPathwayAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddCareerPathwayAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddCareerPathwayAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
