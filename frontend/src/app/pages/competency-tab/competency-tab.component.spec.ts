import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyTabComponent } from './competency-tab.component';

describe('CompetencyTabComponent', () => {
  let component: CompetencyTabComponent;
  let fixture: ComponentFixture<CompetencyTabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompetencyTabComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(CompetencyTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
