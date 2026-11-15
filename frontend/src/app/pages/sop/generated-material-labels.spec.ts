import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNzIconsTesting } from 'ng-zorro-antd/icon/testing';
import { of } from 'rxjs';
import { SopContentViewComponent } from '../../components/sop-content-view/sop-content-view.component';
import { MaterialReviewEvent, QuizReviewEvent, SopDocument, SopModule, SopQuizQuestion } from '../../models/sop.model';
import { SopService } from '../../services/sop.service';
import { SopQuizReviewComponent } from './sop-quiz-review/sop-quiz-review.component';
import { SopReviewComponent } from './sop-review/sop-review.component';

/** Generated material isn't labelled with how it was made, on the trainer's pages or on staff's. */
describe('Generated material labels', () => {
  /** Content saved before the labels were removed: it still carries the old offline flag. */
  const oldOfflineContent = JSON.stringify({
    summary: 'Count the float.', learningObjectives: ['Count it'], tools: [], sections: [], keyTerms: [], source: 'offline'
  });
  const aiContent = JSON.stringify({ summary: 'Count the float.', learningObjectives: [], tools: [], sections: [], keyTerms: [] });

  const question = (id: number, reviewStatus: SopQuizQuestion['reviewStatus']): SopQuizQuestion => ({
    id, sopModuleId: 5, questionType: 'MULTIPLE_CHOICE', questionText: `Question ${id}?`, options: ['A', 'B', 'C', 'D'], correctAnswer: 'A', reviewStatus
  });
  const module = (content: string, reviewStatus: SopModule['reviewStatus'], quizStatus: SopQuizQuestion['reviewStatus']): SopModule => ({
    id: 5, sopDocumentId: 1, moduleOrder: 1, title: 'Opening the Drawer', content, reviewStatus, questionsPerAttempt: 5,
    quiz: [1, 2, 3, 4, 5].map(id => question(id, quizStatus))
  });
  const document_ = {
    id: 1, title: 'Cash Handling', generationStatus: 'COMPLETED', moduleCount: 1, createdAt: '2026-11-09T00:00:00Z', editable: true, passMark: 70,
    durationMinutes: 60, durationSet: true, courseStatus: 'LIVE', enrolledCount: 0, activeEnrollmentCount: 0, queuePosition: 0, courseId: 1, versionNo: 1
  } as SopDocument;
  const materialHistory: MaterialReviewEvent[] = [
    { id: 3, action: 'REGENERATED', detail: 'Rewritten from the trainer\'s feedback, with a new quiz to match.', actorName: null, createdAt: '2026-11-09T02:00:00Z' },
    { id: 2, action: 'REJECTED', detail: 'Shorter please', actorName: 'Tan Wei Ming', createdAt: '2026-11-09T01:00:00Z' },
    { id: 1, action: 'GENERATED', detail: 'Generated from the uploaded SOP.', actorName: null, createdAt: '2026-11-09T00:00:00Z' }
  ];
  const quizHistory: QuizReviewEvent[] = [
    { id: 2, action: 'REGENERATED', detail: 'Regenerated from the trainer\'s feedback.', actorName: null, createdAt: '2026-11-09T02:00:00Z' },
    { id: 1, action: 'REJECTED', detail: 'Ask about the float', actorName: 'Tan Wei Ming', createdAt: '2026-11-09T01:00:00Z' }
  ];

  const pageText = <T>(fixture: ComponentFixture<T>): string => {
    fixture.detectChanges();
    return `${(fixture.nativeElement as HTMLElement).textContent} ${document.body.textContent}`.replace(/\s+/g, ' ');
  };

  const configure = (m: SopModule) => {
    const sop = jasmine.createSpyObj<SopService>('SopService', ['list', 'detail', 'materialHistory', 'quizHistory']);
    sop.list.and.returnValue(of([document_]));
    sop.detail.and.returnValue(of({ document: document_, modules: [m] }));
    sop.materialHistory.and.returnValue(of(materialHistory));
    sop.quizHistory.and.returnValue(of(quizHistory));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideNoopAnimations(), provideNzIconsTesting(), provideHttpClient(), provideHttpClientTesting(),
        { provide: SopService, useValue: sop },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } } }
      ]
    });
  };

  describe('the module content view', () => {
    const rendered = (content: string): HTMLElement => {
      TestBed.configureTestingModule({ providers: [provideNoopAnimations(), provideNzIconsTesting()] });
      const fixture = TestBed.createComponent(SopContentViewComponent);
      fixture.componentRef.setInput('content', content);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    };

    it('shows old offline content with no note and a plain summary label', () => {
      const view = rendered(oldOfflineContent);
      expect(view.textContent).toContain('SUMMARY');
      expect(view.textContent).toContain('Count the float.');
      expect(view.textContent).not.toMatch(/\bAI\b/);
      expect(view.textContent).not.toMatch(/offline/i);
      expect(view.querySelector('.offline-note')).toBeNull();
    });

    it('labels AI-written content the same way', () => {
      const view = rendered(aiContent);
      expect(view.textContent).toContain('SUMMARY');
      expect(view.textContent).not.toMatch(/\bAI\b/);
      expect(view.querySelector('[nzType="robot"]')).toBeNull();
    });
  });

  describe('Generated Material Review', () => {
    let fixture: ComponentFixture<SopReviewComponent>;

    beforeEach(() => {
      configure(module(oldOfflineContent, 'REGENERATED', 'REGENERATED'));
      fixture = TestBed.createComponent(SopReviewComponent);
    });

    afterEach(() => fixture.destroy());

    it('mentions neither the AI nor offline, in the heading, status or content', () => {
      const text = pageText(fixture);
      expect(text).toContain('Review and approve generated training material before it becomes a course.');
      expect(text).toMatch(/regenerated/i);
      expect(text).toContain('SUMMARY');
      expect(text).not.toMatch(/\bAI\b/);
      expect(text).not.toMatch(/offline/i);
    });

    it('names no AI in the summary field while editing', () => {
      fixture.detectChanges();
      const c = fixture.componentInstance;
      c.toggleEdit(c.selectedModule!);
      const text = pageText(fixture);
      expect(text).toContain('Summary');
      expect(text).not.toMatch(/\bAI\b/);
    });

    it('shows a neutral history', () => {
      fixture.detectChanges();
      const c = fixture.componentInstance;
      c.openHistory(c.selectedModule!);
      const text = pageText(fixture);
      expect(text).toContain('Generated from the uploaded SOP.');
      expect(text).toContain('by the system');
      expect(text).toContain('by Tan Wei Ming');
      expect(text).not.toMatch(/\bAI\b/);
      expect(c.historyBy(materialHistory[2])).toBeNull();
      expect(c.historyBy(materialHistory[0])).toBe('by the system');
      expect(c.historyLabel('REGENERATED')).toBe('Regenerated');
    });
  });

  describe('Review Generated Quizzes', () => {
    let fixture: ComponentFixture<SopQuizReviewComponent>;

    beforeEach(() => {
      configure(module(aiContent, 'APPROVED', 'REGENERATED'));
      fixture = TestBed.createComponent(SopQuizReviewComponent);
    });

    afterEach(() => fixture.destroy());

    it('labels a regenerated quiz and its history without the AI', () => {
      const text = pageText(fixture);
      expect(text).toMatch(/Version \d+ was regenerated and is ready for review\./);
      expect(text).toContain('Regenerated');
      expect(text).toContain('by the system');
      expect(text).not.toContain('by the AI');
      expect(text).not.toContain('AI regenerated');
    });

    it('keeps the AI in the wording about a rewrite the trainer asks for', () => {
      const c = fixture.componentInstance;
      fixture.detectChanges();
      c.showReject = true;
      const text = pageText(fixture);
      expect(text).toContain('so the AI rewrites it');
      expect((fixture.nativeElement as HTMLElement).querySelector('textarea[placeholder="What should the AI change in this quiz?"]')).not.toBeNull();
    });
  });
});
