import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewCompetencyDefinitionCollabComponent } from './view-competency-definition-collab.component';

describe('ViewCompetencyDefinitionCollabComponent', () => {
  let component: ViewCompetencyDefinitionCollabComponent;
  let fixture: ComponentFixture<ViewCompetencyDefinitionCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewCompetencyDefinitionCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewCompetencyDefinitionCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
