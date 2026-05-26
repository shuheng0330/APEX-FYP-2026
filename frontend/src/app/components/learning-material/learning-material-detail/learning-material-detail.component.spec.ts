import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningMaterialDetailComponent } from './learning-material-detail.component';

describe('LearningMaterialDetailComponent', () => {
  let component: LearningMaterialDetailComponent;
  let fixture: ComponentFixture<LearningMaterialDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LearningMaterialDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LearningMaterialDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
