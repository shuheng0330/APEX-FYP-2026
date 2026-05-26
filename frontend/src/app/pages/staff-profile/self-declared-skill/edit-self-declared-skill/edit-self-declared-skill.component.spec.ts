import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditSelfDeclaredSkillComponent } from './edit-self-declared-skill.component';

describe('EditSelfDeclaredSkillComponent', () => {
  let component: EditSelfDeclaredSkillComponent;
  let fixture: ComponentFixture<EditSelfDeclaredSkillComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditSelfDeclaredSkillComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditSelfDeclaredSkillComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
