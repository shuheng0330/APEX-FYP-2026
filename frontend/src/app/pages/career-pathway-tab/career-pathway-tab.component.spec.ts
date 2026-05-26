import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CareerPathwayTabComponent } from './career-pathway-tab.component';

describe('CareerPathwayPageComponent', () => {
  let component: CareerPathwayTabComponent;
  let fixture: ComponentFixture<CareerPathwayTabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CareerPathwayTabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CareerPathwayTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
