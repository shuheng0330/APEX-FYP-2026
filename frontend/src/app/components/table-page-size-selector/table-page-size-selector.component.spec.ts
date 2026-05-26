import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TablePageSizeSelectorComponent } from './table-page-size-selector.component';

describe('TablePageSizeSelectorComponent', () => {
  let component: TablePageSizeSelectorComponent;
  let fixture: ComponentFixture<TablePageSizeSelectorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TablePageSizeSelectorComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TablePageSizeSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
