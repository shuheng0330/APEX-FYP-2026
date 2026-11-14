import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNzIconsTesting } from 'ng-zorro-antd/icon/testing';
import { of } from 'rxjs';
import { SopDocument, SopModule, SopQuizQuestion } from '../../../models/sop.model';
import { SopService } from '../../../services/sop.service';
import { SopQuizReviewComponent } from './sop-quiz-review.component';

/** The question form offers Multiple choice and True/False only; older fill-in questions keep their type (FR-08-01). */
describe('Review Quizzes question types', () => {
  const choice = (id: number): SopQuizQuestion => ({
    id, sopModuleId: 5, questionType: 'MULTIPLE_CHOICE', questionText: `Question ${id}?`, options: ['A', 'B', 'C', 'D'],
    correctAnswer: 'A', reviewStatus: 'PENDING'
  });
  const oldFillIn: SopQuizQuestion = {
    id: 9, sopModuleId: 5, questionType: 'FILL_IN_THE_BLANK', questionText: 'Count the ____ before opening.', options: [],
    correctAnswer: 'float', reviewStatus: 'PENDING'
  };
  const module: SopModule = {
    id: 5, sopDocumentId: 1, moduleOrder: 1, title: 'Opening the Drawer', content: '{"summary":"Count the float."}',
    reviewStatus: 'APPROVED', questionsPerAttempt: 5, quiz: [choice(1), choice(2), choice(3), choice(4), oldFillIn]
  };
  const document_ = {
    id: 1, title: 'Cash Handling', generationStatus: 'COMPLETED', moduleCount: 1, createdAt: '2026-11-10T00:00:00Z', editable: true, passMark: 70,
    durationMinutes: 60, durationSet: true, courseStatus: 'LIVE', enrolledCount: 0, activeEnrollmentCount: 0, queuePosition: 0, courseId: 1, versionNo: 1
  } as SopDocument;

  let fixture: ComponentFixture<SopQuizReviewComponent>;
  let page: SopQuizReviewComponent;
  const offered = () => page.typeChoices.map(t => t.label);

  beforeEach(() => {
    const sop = jasmine.createSpyObj<SopService>('SopService', ['list', 'detail', 'materialHistory', 'quizHistory']);
    sop.list.and.returnValue(of([document_]));
    sop.detail.and.returnValue(of({ document: document_, modules: [module] }));
    sop.materialHistory.and.returnValue(of([]));
    sop.quizHistory.and.returnValue(of([]));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideNoopAnimations(), provideNzIconsTesting(), provideHttpClient(), provideHttpClientTesting(),
        { provide: SopService, useValue: sop },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } } }
      ]
    });
    fixture = TestBed.createComponent(SopQuizReviewComponent);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('offers only Multiple choice and True/False when adding a question', () => {
    page.startAdd();
    expect(offered()).toEqual(['Multiple choice', 'True / False']);
  });

  it('offers the same two when editing a choice question', () => {
    page.startEdit(choice(1));
    expect(offered()).toEqual(['Multiple choice', 'True / False']);
  });

  it('keeps Fill in the blank while editing an older fill-in question, then drops it again', () => {
    page.startEdit(oldFillIn);
    expect(offered()).toEqual(['Multiple choice', 'True / False', 'Fill in the blank']);
    expect(page.draft?.questionType).toBe('FILL_IN_THE_BLANK');
    expect(page.draft?.answerText).toBe('float');

    page.cancelEdit();
    page.startAdd();
    expect(offered()).toEqual(['Multiple choice', 'True / False']);
  });

  it('still labels an older fill-in question in the list', () => {
    expect(page.typeLabel('FILL_IN_THE_BLANK')).toBe('Fill in the blank');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Fill in the blank');
  });
});
