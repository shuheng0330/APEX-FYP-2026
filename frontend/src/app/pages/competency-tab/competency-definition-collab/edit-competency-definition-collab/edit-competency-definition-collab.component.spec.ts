import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCompetencyDefinitionCollabComponent } from './edit-competency-definition-collab.component';

describe('EditCompetencyDefinitionCollabComponent', () => {
  let component: EditCompetencyDefinitionCollabComponent;
  let fixture: ComponentFixture<EditCompetencyDefinitionCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditCompetencyDefinitionCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditCompetencyDefinitionCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
