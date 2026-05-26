import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCareerPathwayAssignmentComponent } from './edit-career-pathway-assignment.component';

describe('EditCareerPathwayAssignmentComponent', () => {
  let component: EditCareerPathwayAssignmentComponent;
  let fixture: ComponentFixture<EditCareerPathwayAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditCareerPathwayAssignmentComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditCareerPathwayAssignmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
