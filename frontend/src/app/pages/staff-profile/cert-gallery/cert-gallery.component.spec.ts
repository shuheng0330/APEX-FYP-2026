import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CertGalleryComponent } from './cert-gallery.component';

describe('CertGalleryComponent', () => {
  let component: CertGalleryComponent;
  let fixture: ComponentFixture<CertGalleryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CertGalleryComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CertGalleryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
