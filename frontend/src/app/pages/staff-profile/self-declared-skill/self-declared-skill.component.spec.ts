import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SelfDeclaredSkillComponent } from './self-declared-skill.component';

describe('SelfDeclaredSkillComponent', () => {
  let component: SelfDeclaredSkillComponent;
  let fixture: ComponentFixture<SelfDeclaredSkillComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SelfDeclaredSkillComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SelfDeclaredSkillComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
