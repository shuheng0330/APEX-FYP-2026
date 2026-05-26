import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccessControlOverviewComponent } from './access-control-overview.component';

describe('AccessControlOverviewComponent', () => {
  let component: AccessControlOverviewComponent;
  let fixture: ComponentFixture<AccessControlOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccessControlOverviewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AccessControlOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
