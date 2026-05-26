import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewCareerPathwayComponent } from './view-career-pathway.component';

describe('ViewCareerPathwayComponent', () => {
  let component: ViewCareerPathwayComponent;
  let fixture: ComponentFixture<ViewCareerPathwayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewCareerPathwayComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewCareerPathwayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
