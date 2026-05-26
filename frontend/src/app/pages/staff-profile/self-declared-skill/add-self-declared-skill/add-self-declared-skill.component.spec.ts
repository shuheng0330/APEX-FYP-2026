import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddSelfDeclaredSkillComponent } from './add-self-declared-skill.component';

describe('AddSelfDeclaredSkillComponent', () => {
  let component: AddSelfDeclaredSkillComponent;
  let fixture: ComponentFixture<AddSelfDeclaredSkillComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddSelfDeclaredSkillComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddSelfDeclaredSkillComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
