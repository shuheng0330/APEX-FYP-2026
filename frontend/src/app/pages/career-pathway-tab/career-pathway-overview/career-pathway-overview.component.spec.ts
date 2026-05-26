import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CareerPathwayOverviewComponent } from './career-pathway-overview.component';

describe('CareerPathwayOverviewComponent', () => {
  let component: CareerPathwayOverviewComponent;
  let fixture: ComponentFixture<CareerPathwayOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CareerPathwayOverviewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CareerPathwayOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
