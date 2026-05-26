import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddCareerPathwayComponent } from './add-career-pathway.component';

describe('AddCareerPathwayComponent', () => {
  let component: AddCareerPathwayComponent;
  let fixture: ComponentFixture<AddCareerPathwayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddCareerPathwayComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddCareerPathwayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
