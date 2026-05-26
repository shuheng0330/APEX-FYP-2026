import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditAccessControlComponent } from './edit-access-control.component';

describe('EditAccessControlComponent', () => {
  let component: EditAccessControlComponent;
  let fixture: ComponentFixture<EditAccessControlComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditAccessControlComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditAccessControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
