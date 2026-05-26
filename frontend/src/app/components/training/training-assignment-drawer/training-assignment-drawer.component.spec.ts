import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TrainingAssignmentDrawerComponent } from './training-assignment-drawer.component';

describe('TrainingAssignmentDrawerComponent', () => {
  let component: TrainingAssignmentDrawerComponent;
  let fixture: ComponentFixture<TrainingAssignmentDrawerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TrainingAssignmentDrawerComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TrainingAssignmentDrawerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
