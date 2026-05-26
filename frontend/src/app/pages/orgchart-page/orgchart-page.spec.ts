import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OrgchartPage } from './orgchart-page';

describe('OrgchartPage', () => {
  let component: OrgchartPage;
  let fixture: ComponentFixture<OrgchartPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrgchartPage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(OrgchartPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
