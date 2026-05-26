import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StaffTabComponent } from './staff-tab.component';

describe('StaffTabComponent', () => {
  let component: StaffTabComponent;
  let fixture: ComponentFixture<StaffTabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StaffTabComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(StaffTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
