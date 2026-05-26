import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewRolesCompetenciesComponent } from './view-roles-competencies.component';

describe('ViewRolesCompetenciesComponent', () => {
  let component: ViewRolesCompetenciesComponent;
  let fixture: ComponentFixture<ViewRolesCompetenciesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewRolesCompetenciesComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewRolesCompetenciesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
