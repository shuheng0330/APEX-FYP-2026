import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddCompetencyDefinitionCollabComponent } from './add-competency-definition-collab.component';

describe('AddCompetencyDefinitionCollabComponent', () => {
  let component: AddCompetencyDefinitionCollabComponent;
  let fixture: ComponentFixture<AddCompetencyDefinitionCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddCompetencyDefinitionCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddCompetencyDefinitionCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
