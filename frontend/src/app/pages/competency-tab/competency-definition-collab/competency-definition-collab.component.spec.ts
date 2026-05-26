import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyDefinitionCollabComponent } from './competency-definition-collab.component';

describe('CompetencyDefinitionCollabComponent', () => {
  let component: CompetencyDefinitionCollabComponent;
  let fixture: ComponentFixture<CompetencyDefinitionCollabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompetencyDefinitionCollabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CompetencyDefinitionCollabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
