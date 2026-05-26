import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewSelfDeclaredSkillComponent } from './view-self-declared-skill.component';

describe('ViewSelfDeclaredSkillComponent', () => {
  let component: ViewSelfDeclaredSkillComponent;
  let fixture: ComponentFixture<ViewSelfDeclaredSkillComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewSelfDeclaredSkillComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewSelfDeclaredSkillComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
