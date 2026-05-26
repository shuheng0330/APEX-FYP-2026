import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCertComponent } from './edit-cert.component';

describe('EditCertComponent', () => {
  let component: EditCertComponent;
  let fixture: ComponentFixture<EditCertComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditCertComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditCertComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
