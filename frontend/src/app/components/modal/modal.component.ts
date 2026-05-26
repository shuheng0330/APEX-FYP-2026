import {Component, EventEmitter, Input, OnInit, Output, TemplateRef} from '@angular/core';
import { NzModalModule} from 'ng-zorro-antd/modal';
import { NgTemplateOutlet} from '@angular/common';

@Component({
  selector: 'modal-component',
  templateUrl: './modal.component.html',
  styleUrls: ['./modal.component.scss'],
  standalone: true,
  imports: [
    NzModalModule,
    NgTemplateOutlet,
  ]
})
export class ModalComponent implements OnInit {
  @Input() isVisible!: boolean;
  @Input() title: string | undefined = '';
  @Input() footer: any = null;
  @Input() width = 520;
  @Input() maskClosable = false;
  @Input() modalClass = '';  // for your own CSS classes
  @Input() contentTemplate!: TemplateRef<any>;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() okClick = new EventEmitter<void>();
  // @Output() cancelClick = new EventEmitter<void>();

  ngOnInit() {
    console.log('ngOnInit - contentTemplate:', this.contentTemplate);
  }

  close(): void {
    this.isVisible = false;
    this.visibleChange.emit(this.isVisible);
    // this.cancelClick.emit();
  }

  ok(): void {
    this.okClick.emit();
  }
}
