import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningEngagementPageComponent } from './learning-engagement-page.component';

describe('LearningEngagementPageComponent', () => {
  let component: LearningEngagementPageComponent;
  let fixture: ComponentFixture<LearningEngagementPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LearningEngagementPageComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LearningEngagementPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
