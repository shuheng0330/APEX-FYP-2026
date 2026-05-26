import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MyCareerPathwayComponent } from './my-career-pathway.component';

describe('MyCareerPathwayComponent', () => {
  let component: MyCareerPathwayComponent;
  let fixture: ComponentFixture<MyCareerPathwayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyCareerPathwayComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MyCareerPathwayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
