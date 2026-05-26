import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyOverviewComponent } from './competency-overview.component';

describe('CompetencyOverviewComponent', () => {
  let component: CompetencyOverviewComponent;
  let fixture: ComponentFixture<CompetencyOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompetencyOverviewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CompetencyOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
