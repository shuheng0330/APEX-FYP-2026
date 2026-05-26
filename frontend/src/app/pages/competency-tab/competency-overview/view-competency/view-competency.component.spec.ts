import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewCompetencyComponent } from './view-competency.component';

describe('ViewCompetencyComponent', () => {
  let component: ViewCompetencyComponent;
  let fixture: ComponentFixture<ViewCompetencyComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewCompetencyComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewCompetencyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
