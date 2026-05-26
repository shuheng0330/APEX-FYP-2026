import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RolesCompetenciesTabComponent } from './roles-competencies-tab.component';

describe('RolesCompetenciesTabComponent', () => {
  let component: RolesCompetenciesTabComponent;
  let fixture: ComponentFixture<RolesCompetenciesTabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RolesCompetenciesTabComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(RolesCompetenciesTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
