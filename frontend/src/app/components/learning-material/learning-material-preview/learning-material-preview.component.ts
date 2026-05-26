import { Component, OnDestroy, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LearningDocument, LearningMaterial } from '../../../models/learning-material.model';
import { LearningMaterialService } from '../../../services/learning-material.service';
import { ActivatedRoute } from '@angular/router';
import { PdfViewerModule } from 'ng2-pdf-viewer';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../services/auth.service';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { LearningDocumentProgressService } from '../../../services/learning-document-progress.service';
import { NzTagModule } from 'ng-zorro-antd/tag';

//TODO: adjust video preview layout
@Component({
  selector: 'app-learning-material-preview',
  imports: [CommonModule, PdfViewerModule, NzIconModule, NzTagModule],
  templateUrl: './learning-material-preview.component.html',
  styleUrl: './learning-material-preview.component.scss'
})

export class LearningMaterialPreviewComponent implements OnInit, OnDestroy {


  material?: LearningMaterial;
  materialId?: number;
  isLoading = true;
  viewMode: 'admin' | 'user' = 'user'
  isFullscreen = false;
  userId: string | null = null

  selectedDocumentIndex = 0;

  overallProgress = 0;
  totalPagesPerDoc: number[] = [];
  currentPagePerDoc: number[] = [];

  totalPages = 0;
  currentPage = 1;

  videoDuration = 0;
  videoCurrentTime = 0;

  fileBlobUrl?: string;
  // progressSaveTimer?: any;
  resumeProgressList: any[] = [];

  // Debounce timer
  private progressSaveTimer: ReturnType<typeof setTimeout> | null = null;
  private lastVideoSaveTime = 0;
  private maxVideoTimeReached = 0;

  isMobile = false;

  constructor(
    private route: ActivatedRoute,
    private learningMaterialService: LearningMaterialService,
    private authService: AuthService,
    private http: HttpClient,
    private learningDocumentProgressService: LearningDocumentProgressService,
  ) {
  }

  @HostListener('window:resize', ['$event'])
  onResize(event: any) {
    this.checkScreenSize();
  }

  checkScreenSize() {
    this.isMobile = window.innerWidth <= 768; // Adjust breakpoint as needed
  }

  ngOnInit(): void {
    this.checkScreenSize();
    this.userId = this.authService.userId;
    const id = this.route.snapshot.paramMap.get('id');
    this.viewMode = this.route.snapshot.data['viewMode'] || 'user';

    if (!id || !this.userId) return;
    this.materialId = +id;

    if (this.viewMode == 'user') {
      this.learningDocumentProgressService.getAllProgress(this.userId, this.materialId)
        .subscribe((progressList: any[]) => {
          this.resumeProgressList = progressList;
          this.loadMaterial();
        });
    } else {
      this.loadMaterial();
    }
  }

  ngOnDestroy(): void {
    if (this.progressSaveTimer) {
      clearTimeout(this.progressSaveTimer);
      this.progressSaveTimer = null;
    }

    if (this.fileBlobUrl) {
      URL.revokeObjectURL(this.fileBlobUrl);
      this.fileBlobUrl = undefined;
    }
  }

  // private loadMaterial(): void {
  //   this.isLoading = true;
  //   this.learningMaterialService.getLearningMaterialById(this.materialId).subscribe({
  //     next: (res) => {
  //       this.material = res;
  //       const docs = this.material?.learningDocuments ?? [];
  //
  //       this.totalPagesPerDoc = new Array(docs.length).fill(0);
  //       this.currentPagePerDoc = new Array(docs.length).fill(0);
  //
  //       docs.forEach((doc, index) => {
  //         if (this.isPdfDocument(doc)) {
  //           this.totalPagesPerDoc[index] = doc.totalPages || 0;
  //         } else if (this.isVideoDocument(doc)) {
  //           this.totalPagesPerDoc[index] = 100; // videos normalized to 0..100
  //         }
  //       });
  //
  //       this.resumeProgressList.forEach(saved => {
  //         const docIndex = docs.findIndex(d => d.documentId === saved.documentId);
  //         if (docIndex < 0) return;
  //         const doc = docs[docIndex];
  //
  //         if (this.isPdfDocument(doc)) {
  //           this.currentPagePerDoc[docIndex] = Number(saved.lastPosition) || 1;
  //         } else if (this.isVideoDocument(doc)) {
  //           const pos = saved?.lastPosition ? Number(saved.lastPosition) : 0;
  //           this.currentPagePerDoc[docIndex] = Math.min(Math.round((pos / (doc.totalDuration || 1)) * 100), 100);
  //           if (docIndex === this.selectedDocumentIndex) {
  //             this.videoCurrentTime = pos;
  //           }
  //         }
  //       });
  //
  //       this.calculateOverallProgress();
  //
  //       if (docs.length > 0) {
  //         this.selectDocument(this.selectedDocumentIndex || 0);
  //       }
  //
  //       this.isLoading = false;
  //     },
  //     error: (err) => {
  //       console.error('Failed to load material', err);
  //       this.isLoading = false;
  //     }
  //   });
  // }

  private loadMaterial(): void {
    this.isLoading = true;
    this.learningMaterialService.getLearningMaterialById(this.materialId).subscribe({
      next: (res) => {
        this.material = res;
        const docs = this.material?.learningDocuments ?? [];

        // Initialize arrays based on doc count
        this.totalPagesPerDoc = new Array(docs.length).fill(0);
        this.currentPagePerDoc = new Array(docs.length).fill(0);

        docs.forEach((doc, index) => {
          if (this.isPdfDocument(doc)) {
            this.totalPagesPerDoc[index] = doc.totalPages || 1; // Default to 1 to avoid div by zero
          } else {
            this.totalPagesPerDoc[index] = 100; // Videos always normalized to 100%
          }

          // Check for saved progress
          const saved = this.resumeProgressList.find(p => p.documentId === doc.documentId);
          if (saved) {
            if (this.isPdfDocument(doc)) {
              // Store the page number reached
              this.currentPagePerDoc[index] = Number(saved.lastPosition) || 1;
            } else {
              // Store the percentage (0-100)
              this.currentPagePerDoc[index] = saved.progress || 0;
            }
          }
        });

        this.calculateOverallProgress();
        if (docs.length > 0) this.selectDocument(this.selectedDocumentIndex);
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load material', err);
        this.isLoading = false;
      }
    });
  }

  loadFile(fileUrl: string): void {
    const token = this.authService.getAccessToken();
    this.http.get(fileUrl, {
      headers: new HttpHeaders().set('Authorization', `Bearer ${token}`),
      responseType: 'blob'
    }).subscribe(blob => {
      this.fileBlobUrl = URL.createObjectURL(blob);
    });
  }

  toggleFullscreen(): void {
    this.isFullscreen = !this.isFullscreen;
  }

  get selectedDocument(): LearningDocument | undefined {
    return this.material?.learningDocuments?.[this.selectedDocumentIndex];
  }
  //
  // onPdfLoadComplete(pdf: any) {
  //   this.totalPages = pdf.numPages;
  //   this.totalPagesPerDoc[this.selectedDocumentIndex] = pdf.numPages;
  //
  //   if (this.currentPage > this.totalPages) {
  //     this.currentPage = this.totalPages;
  //   }
  //
  //   this.currentPagePerDoc[this.selectedDocumentIndex] = this.currentPage;
  //   this.calculateOverallProgress();
  // }

  onPdfLoadComplete(pdf: any) {
    this.totalPages = pdf.numPages;
    const index = this.selectedDocumentIndex;

    // 1. Update the total pages for this specific index
    this.totalPagesPerDoc[index] = pdf.numPages;

    // 2. Safety check: ensure current page hasn't exceeded new total
    if (this.currentPage > this.totalPages) {
      this.currentPage = this.totalPages;
    }

    // 3. Update the tracking array for highest page reached
    // We use Math.max to ensure we don't overwrite if they were already further ahead
    this.currentPagePerDoc[index] = Math.max(this.currentPagePerDoc[index] || 1, this.currentPage);

    // 4. Recalculate the overall normalized progress (Average of all %s)
    this.calculateOverallProgress();
  }

  goToPrevPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
    }
  }

  // goToNextPage(): void {
  //   if (this.currentPage < this.totalPages) {
  //     this.currentPage++;
  //
  //     const prevPage = this.currentPagePerDoc[this.selectedDocumentIndex] || 0;
  //
  //     if (this.currentPage > prevPage) {
  //       this.currentPagePerDoc[this.selectedDocumentIndex] = this.currentPage;
  //       this.calculateOverallProgress();
  //       this.saveProgressDebounced();
  //     }
  //   }
  // }

  goToNextPage(): void {
    if (this.currentPage < this.totalPages) {
      this.currentPage++;

      // Track that we reached this page
      if (this.currentPage > (this.currentPagePerDoc[this.selectedDocumentIndex] || 0)) {
        this.currentPagePerDoc[this.selectedDocumentIndex] = this.currentPage;

        // The calculateOverallProgress() called here now uses the new ratio logic
        this.calculateOverallProgress();
        this.saveProgressDebounced();
      }
    }
  }

  isDocumentCompleted(index: number): boolean {
    const total = this.totalPagesPerDoc[index] || 0;
    const current = this.currentPagePerDoc[index] || 0;

    if (total === 0) return false;

    return current >= total;
  }

  isPdfDocument(document: LearningDocument): boolean {
    return document.fileUrl.toLowerCase().includes('.pdf') || document.fileUrl.toLowerCase().includes('pdf');
  }

  isVideoDocument(document: LearningDocument): boolean {
    const videoExtensions = ['.mp4', '.avi', '.mov', '.wmv', '.flv', '.webm'];
    return videoExtensions.some(ext => document.fileUrl.toLowerCase().includes(ext));
  }

  // selectDocument(index: number): void {
  //   this.selectedDocumentIndex = index;
  //   const doc = this.material?.learningDocuments?.[index];
  //   if (!doc) return;
  //
  //   const saved = this.resumeProgressList.find(p => p.documentId === doc.documentId);
  //   console.log("progress list", this.resumeProgressList)
  //
  //   if (this.isPdfDocument(doc)) {
  //     const pos = saved?.lastPosition ? Number(saved.lastPosition) : 1;
  //     this.currentPage = !isNaN(pos) ? pos : 1;
  //     this.currentPagePerDoc[index] = this.currentPage;
  //   } else if (this.isVideoDocument(doc)) {
  //     const pos = saved?.lastPosition ? Number(saved.lastPosition) : 0;
  //     this.videoCurrentTime = !isNaN(pos) ? pos : 0;
  //     this.currentPagePerDoc[index] = Math.min(Math.round((this.videoCurrentTime / (doc.totalDuration || 1)) * 100), 100);
  //   }
  //
  //   const fileUrl = `${environment.apiBaseUrl}${doc.signedUrl}`;
  //   this.loadFile(fileUrl);
  // }

  //replace
  selectDocument(index: number): void {
    this.selectedDocumentIndex = index;
    const doc = this.material?.learningDocuments?.[index];
    if (!doc) return;

    // 1. Find saved progress for this specific document
    const saved = this.resumeProgressList.find(p => p.documentId === doc.documentId);

    if (this.isPdfDocument(doc)) {
      // Determine last page reached
      const savedPage = saved?.lastPosition ? Number(saved.lastPosition) : 1;
      this.currentPage = !isNaN(savedPage) ? savedPage : 1;

      // Update our tracker only if saved progress is further ahead
      this.currentPagePerDoc[index] = Math.max(this.currentPagePerDoc[index] || 0, this.currentPage);

    } else if (this.isVideoDocument(doc)) {
      // Determine last second reached
      const savedSeconds = saved?.lastPosition ? Number(saved.lastPosition) : 0;
      this.videoCurrentTime = !isNaN(savedSeconds) ? savedSeconds : 0;

      this.maxVideoTimeReached = this.videoCurrentTime;

      // Calculate percentage from saved seconds
      const savedPercent = Math.min(Math.round((this.videoCurrentTime / (doc.totalDuration || 1)) * 100), 100);

      // Update our tracker only if saved progress is further ahead
      this.currentPagePerDoc[index] = Math.max(this.currentPagePerDoc[index] || 0, savedPercent);
    }

    // 2. Load the actual file
    const fileUrl = `${environment.apiBaseUrl}${doc.signedUrl}`;
    this.loadFile(fileUrl);

    // 3. Recalculate overall progress in case local state was updated
    this.calculateOverallProgress();
  }


  onVideoLoaded(event: Event): void {
    const video = event.target as HTMLVideoElement;
    this.videoDuration = video.duration || 0;

    const doc = this.selectedDocument;
    if (!doc) return;

    const saved = this.resumeProgressList.find(p => p.documentId === doc.documentId);
    if (saved?.lastPosition) {
      const pos = Number(saved.lastPosition);
      if (!isNaN(pos) && pos >= 0 && pos <= this.videoDuration) {
        video.currentTime = pos;
        this.videoCurrentTime = pos;
      }
    }

  }

  // onVideoTimeUpdate(event: Event): void {
  //   const video = event.target as HTMLVideoElement;
  //   this.videoCurrentTime = video.currentTime;
  //   const duration = video.duration || 1;
  //
  //   const newPercent = Math.round((video.currentTime / duration) * 100);
  //
  //   const prevPercent = this.currentPagePerDoc[this.selectedDocumentIndex] || 0;
  //
  //   if (newPercent > prevPercent) {
  //     this.currentPagePerDoc[this.selectedDocumentIndex] = newPercent;
  //     this.totalPagesPerDoc[this.selectedDocumentIndex] = 100;
  //     const now = Date.now();
  //     if (now - this.lastVideoSaveTime > 3000) {
  //       this.lastVideoSaveTime = now;
  //       this.saveProgress();
  //     }
  //     this.calculateOverallProgress();
  //   }
  //
  //
  // }

  //replace
  onVideoTimeUpdate(event: Event): void {
    const video = event.target as HTMLVideoElement;

    if (video.currentTime > this.maxVideoTimeReached + 1.5) {
      video.currentTime = this.maxVideoTimeReached;
      return;
    }

    // 2. Update the "high-water mark"
    if (video.currentTime > this.maxVideoTimeReached) {
      this.maxVideoTimeReached = video.currentTime;
    }

    this.videoCurrentTime = video.currentTime;

    const duration = video.duration || 1;
    const newPercent = Math.round((video.currentTime / duration) * 100);
    const prevPercent = this.currentPagePerDoc[this.selectedDocumentIndex] || 0;

    // Only update and save if the user has moved forward
    if (newPercent > prevPercent) {
      this.currentPagePerDoc[this.selectedDocumentIndex] = newPercent;
      this.calculateOverallProgress();

      const now = Date.now();
      if (now - this.lastVideoSaveTime > 3000) {
        this.lastVideoSaveTime = now;
        this.saveProgress();
      }
    }
  }

  onVideoEnded(): void {
    this.videoCurrentTime = this.videoDuration;
    this.calculateOverallProgress();
    this.saveProgress();
  }

  //replace
  private calculateOverallProgress(): void {
    const docs = this.material?.learningDocuments ?? [];
    if (docs.length === 0) {
      this.overallProgress = 0;
      return;
    }

    let aggregatePercentage = 0;

    docs.forEach((doc, index) => {
      const total = this.totalPagesPerDoc[index] || 0;
      const current = this.currentPagePerDoc[index] || 0;

      if (total > 0) {
        // (Pages Read / Total Pages) * 100 OR (Video % / 100) * 100
        const docProgress = Math.min((current / total) * 100, 100);
        aggregatePercentage += docProgress;
      }
    });

    this.overallProgress = Math.round(aggregatePercentage / docs.length);
  }

  private saveProgressDebounced(): void {
    if (this.viewMode === 'admin') return;

    if (this.progressSaveTimer) {
      clearTimeout(this.progressSaveTimer);
    }

    this.progressSaveTimer = setTimeout(() => this.saveProgress(), 2000);
  }

  //replace

  saveProgress(): void {
    if (this.viewMode === 'admin' || !this.userId) return;

    const doc = this.selectedDocument;
    const index = this.selectedDocumentIndex;
    if (!doc) return;

    let progress = 0;
    let lastPosition = '';

    if (this.isPdfDocument(doc)) {
      const total = this.totalPagesPerDoc[index] || 1;
      const current = this.currentPagePerDoc[index] || 1;
      progress = Math.round((current / total) * 100);
      lastPosition = this.currentPage.toString();
    } else {
      const duration = this.videoDuration || 1;
      progress = Math.round((this.videoCurrentTime / duration) * 100);
      lastPosition = this.videoCurrentTime.toString();
    }

    const dto: UpdateDocumentProgress = {
      staffId: this.userId,
      materialId: this.materialId!,
      documentId: doc.documentId,
      progress: Math.min(progress, 100),
      lastPosition: lastPosition,
      isCompleted: progress >= 100,
      overallProgress: this.overallProgress
    };

    this.learningDocumentProgressService.saveProgress(dto).subscribe();
  }

}

