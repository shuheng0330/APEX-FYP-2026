import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCompetencyComponent } from './edit-competency.component';

describe('EditCompetencyComponent', () => {
  let component: EditCompetencyComponent;
  let fixture: ComponentFixture<EditCompetencyComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditCompetencyComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditCompetencyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
