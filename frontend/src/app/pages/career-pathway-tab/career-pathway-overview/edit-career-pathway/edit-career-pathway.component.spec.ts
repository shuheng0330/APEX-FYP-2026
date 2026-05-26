import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCareerPathwayComponent } from './edit-career-pathway.component';

describe('EditCareerPathwayComponent', () => {
  let component: EditCareerPathwayComponent;
  let fixture: ComponentFixture<EditCareerPathwayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditCareerPathwayComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditCareerPathwayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
