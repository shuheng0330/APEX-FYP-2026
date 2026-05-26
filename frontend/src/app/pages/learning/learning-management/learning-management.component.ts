import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators,
  FormGroup,
  FormsModule,
  FormControl,
  FormArray
} from '@angular/forms';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { NzUploadFile, NzUploadModule } from 'ng-zorro-antd/upload';
import { LearningMaterialService } from '../../../services/learning-material.service';
import { LearningMaterial } from '../../../models/learning-material.model';
import { NzMessageService } from 'ng-zorro-antd/message';
import { catchError, firstValueFrom, forkJoin, of } from 'rxjs';
import { OrgChartService } from '../../../services/orgChart.service';
import {
  NzTableFilterFn,
  NzTableFilterList,
  NzTableModule, NzTableSortFn, NzTableSortOrder,
} from 'ng-zorro-antd/table';
import {
  NzDropDownModule
} from 'ng-zorro-antd/dropdown';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { AuthService } from '../../../services/auth.service';
import { RouterLink } from '@angular/router';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { NzSkeletonComponent } from 'ng-zorro-antd/skeleton';
import { CompetencyService } from '../../../services/competency.service';
import { environment } from '../../../../environments/environment';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { NzDrawerModule, NzDrawerRef, NzDrawerService } from 'ng-zorro-antd/drawer';
import {
  LearningMaterialDetailComponent
} from '../../../components/learning-material/learning-material-detail/learning-material-detail.component';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { StaffLearningMaterialService } from '../../../services/learning-enrollment.service';
import { StaffLearningMaterial } from '../../../models/staff-learning-material.model';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { Staff } from '../../../models/staff.model';
import {NzTagModule} from 'ng-zorro-antd/tag';

//TODO: refine frontend interface, clean code

interface ItemData {
  name: string;
  staffDto: Staff;
  progress: number;
}

interface Option {
  id: number;
  name: string;
}

interface ColumnItem {
  name: string;
  sortOrder: NzTableSortOrder | null;
  sortFn: NzTableSortFn<ItemData> | null;
  listOfFilter: NzTableFilterList;
  filterFn: NzTableFilterFn<ItemData> | null;
  filterMultiple: boolean;
  isSearchable: boolean;
  sortDirections: NzTableSortOrder[];
}


@Component({
  selector: 'app-learning-management',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NzModalModule,
    NzFormModule,
    NzUploadModule,
    NzInputModule,
    NzButtonModule,
    NzIconModule,
    NzGridModule,
    NzSelectModule,
    TranslatePipe,
    NzTableModule,
    NzButtonModule,
    NzModalModule,
    NzIconModule,
    NzInputModule,
    NzSelectModule,
    FormsModule,
    NzSwitchModule,
    NzDropDownModule,
    RouterLink,
    NzDividerComponent,
    NzSkeletonComponent,
    LearningMaterialDetailComponent,
    NzTabsModule,
    NzDrawerModule,
    NzProgressModule,
    NzEmptyModule,
    NzTagModule
  ],
  templateUrl: './learning-management.component.html',
  styleUrls: ['./learning-management.component.scss']
})
export class LearningManagementComponent implements OnInit {
  @ViewChild('addLearningMaterialModalTpl', { static: true }) addLearningMaterialModalTpl!: TemplateRef<any>;
  @ViewChild('materialDetailsTpl', { static: false }) materialDetailsTpl?: TemplateRef<{
    $implicit: { value: string };
    drawerRef: NzDrawerRef<string>;
  }>;
  listOfColumns: ColumnItem[] = [];
  listOfRole: NzTableFilterList = [];
  listOfDepartment: NzTableFilterList = [];
  isFormModalVisible = false;
  materialTypes: string[] = ['Document', 'Video'];
  titleKey: string = "PAGE.LEARNING_MANAGEMENT.TITLE";
  form!: FormGroup;
  isLoading = true;
  indeterminate = false;
  fetchedLearningMaterialList: LearningMaterial[] = [];
  listOfCurrentPageData: readonly LearningMaterial[] = [];
  learningOutcomesForm!: FormGroup;
  searchValue?: string;
  searchField: number = 0;
  staffSearch: string = '';
  visible = false;
  setOfCheckedId = new Set<number>();
  checked = false;
  listOfDisplayData = [...this.fetchedLearningMaterialList];
  learningOutcomes: Array<{ id: number; controlInstance: string }> = [];
  departments: Array<{ id: number, name: string }> = [];
  competencies: Array<{ id: number, name: string }> = [];
  fileList: NzUploadFile[] = [];
  confirmModal?: NzModalRef;
  selectedMaterial?: LearningMaterial;
  enrolledStaffList: StaffLearningMaterial[] = [];
  isLoadingEnrolledStaff = false;
  filteredEnrolledStaffList: StaffLearningMaterial[] = [];
  optionList: Option[] = []
  selectedDepartmentIds: number[] = [];
  selectedCompetenciesIds: number[] = [];
  courseDetailDrawerRef?: NzDrawerRef;

  private readonly bulkDeleteConfirmTitleKey = 'PAGE.LEARNING_MANAGEMENT.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.LEARNING_MANAGEMENT.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.LEARNING_MANAGEMENT.BULK_DELETE.CONFIRM.OK';
  private readonly searchAnyKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchTitleKey = 'PAGE.LEARNING_MANAGEMENT.SEARCH.FIELD.TITLE';
  private readonly searchLearningOutcomeKey = 'PAGE.LEARNING_MANAGEMENT.SEARCH.FIELD.OUTCOME';
  private readonly searchDescKey = 'PAGE.LEARNING_MANAGEMENT.SEARCH.FIELD.DESC';

  constructor(private formBuilder: FormBuilder,
    private translate: TranslateService,
    private titleService: Title,
    private orgChartService: OrgChartService,
    private learningMaterialService: LearningMaterialService,
    private competencyService: CompetencyService,
    private translateService: TranslateService,
    private authService: AuthService,
    private drawerService: NzDrawerService,
    private message: NzMessageService,
    private modal: NzModalService,
    private http: HttpClient,
    private staffLearningMaterialService: StaffLearningMaterialService) {
  }

  ngOnInit() {
    this.translate.get(this.titleKey).subscribe((translateTitle: string) => {
      this.titleService.setTitle(translateTitle);
    });
    this.form = this.formBuilder.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['', [Validators.required, Validators.maxLength(1000)]],
      materialType: [[], [Validators.required]],
      departmentIds: [[], [Validators.required]],
      competencyIds: [[], [Validators.required]],
      learningOutcomes: this.formBuilder.array<string[]>([]),
      learningDocuments: this.formBuilder.array([])
    });
    this.learningOutcomesForm = this.formBuilder.group({});

    this.loadDropdownOptions();
    this.addField();
    this.loadList();
  }

  loadDropdownOptions(): void {
    forkJoin({
      departments: this.orgChartService.getDepartmentList().pipe(
        catchError(err => {
          console.error('Failed to load departments', err);
          return of([]);
        })
      ),
      competencies: this.competencyService.getAllCompetencies().pipe(
        catchError(err => {
          console.error('Failed to load competencies', err);
          return of([]);
        })
      )
    }).subscribe(({ departments, competencies }) => {
      this.departments = departments.map((d) => ({
        id: d.id,
        name: d.name
      }));

      this.competencies = competencies
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((c) => ({
          id: c.id!,
          name: c.name
        }));

      console.log('drop down options loaded', this.departments, this.competencies);
    });
  }

  onCloseFormModal(): void {
    this.isFormModalVisible = false;
    this.resetForm();
  }

  get learningDocuments(): FormArray {
    return this.form.get('learningDocuments') as FormArray;
  }

  addDocument(): void {
    this.learningDocuments.push(
      this.formBuilder.group({
        title: ['', Validators.required],
        fileUrl: [null],
        fileList: [[]],
        fileType: [null]
      })
    );
  }

  removeDocument(index: number): void {
    this.learningDocuments.removeAt(index);
  }

  addField(e?: MouseEvent): void {
    e?.preventDefault();
    const id = this.learningOutcomes.length > 0 ? this.learningOutcomes[this.learningOutcomes.length - 1].id + 1 : 0;

    const control = {
      id,
      controlInstance: `note${id}`
    };
    const index = this.learningOutcomes.push(control);
    this.learningOutcomesForm.addControl(
      this.learningOutcomes[index - 1].controlInstance,
      this.formBuilder.control('', Validators.required)
    );
  }

  removeField(i: { id: number; controlInstance: string }, e: MouseEvent): void {
    e.preventDefault();

    if (this.learningOutcomes.length > 1) {
      const index = this.learningOutcomes.indexOf(i);
      this.learningOutcomes.splice(index, 1); // remove from UI
      this.learningOutcomesForm.removeControl(i.controlInstance); // remove control
    }
  }

  private normalizeUploadFile(file: any, isExisting = false, index = 0): NzUploadFile {

    let fileUrl: string | undefined;

    if (file?.signedUrl) {
      fileUrl = `${environment.apiBaseUrl}${file.signedUrl}`;
    }

    return {
      uid: isExisting ? `${index}` : `${Date.now()}`,
      name: file?.name || this.extractFileName(file?.fileUrl || ''),
      status: 'done',
      url: fileUrl || undefined, // keep it but won't use directly
      originFileObj: file.originFileObj || file,
      isExisting
    } as NzUploadFile;
  }

  beforeUpload = (index: number) => (file: NzUploadFile): boolean => {
    const isPdf = file.type === 'application/pdf';
    const isVideo = file.type?.startsWith('video/');

    if (!isPdf && !isVideo) {
      this.message.error('Only PDF or video files are allowed!');
      return false; // Stop upload
    }

    const fileType = isPdf ? 'PDF' : 'VIDEO';

    const control = this.learningDocuments.at(index) as FormGroup;
    control.patchValue({
      fileList: [this.normalizeUploadFile(file)],
      fileType: fileType
    });
    return false;
  };

  handlePreview = (file: NzUploadFile) => {
    const token = this.authService.getAccessToken();

    this.http.get(file.url!, {
      headers: new HttpHeaders().set('Authorization', `Bearer ${token}`),
      responseType: 'blob'
    }).subscribe(blob => {
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, '_blank'); // open PDF/image in new tab
    });
  };

  handleDownload = (file: NzUploadFile) => {
    const token = this.authService.getAccessToken();

    this.http.get(file.url!, {
      headers: new HttpHeaders().set('Authorization', `Bearer ${token}`),
      responseType: 'blob'
    }).subscribe(blob => {
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = file.name;
      link.click();
    });
  };

  fetchLearningMaterialList(): void {
    this.isLoading = true;
    this.learningMaterialService.getAllLearningMaterials().subscribe({
      next: (res) => {
        this.fetchedLearningMaterialList = res.sort((a, b) =>
          a.title.localeCompare(b.title)
        );
        this.listOfDisplayData = this.fetchedLearningMaterialList;
        this.isLoading = false;
      }
    })
  }

  sortByTitle = (a: any, b: any) =>
    a.title.localeCompare(b.title);

  sortByDesc = (a: any, b: any) =>
    a.description.localeCompare(b.description);

  updateCheckedSet(id: number, checked: boolean): void {
    if (checked) {
      this.setOfCheckedId.add(id);
    } else {
      this.setOfCheckedId.delete(id);
    }
  }

  refreshCheckedStatus(): void {
    const listOfEnabledData = this.listOfCurrentPageData;

    if (listOfEnabledData.length === 0) {
      this.checked = false;
      this.indeterminate = false;
      return;
    }

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.materialId));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.materialId)) && !this.checked;
  }

  onCurrentPageDataChange(listOfCurrentPageData: readonly LearningMaterial[]): void {
    this.listOfCurrentPageData = listOfCurrentPageData;
    this.refreshCheckedStatus();
  }

  onItemChecked(id: number, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.listOfCurrentPageData.forEach(item => this.updateCheckedSet(item.materialId, value));
    this.refreshCheckedStatus();
  }

  // search(): void {
  //   this.loadingService.show();
  //
  //   const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";
  //
  //   this.listOfDisplayData = [];
  //
  //   if (!searchVal) {
  //     this.listOfDisplayData = [...this.fetchedLearningMaterialList];
  //   } else {
  //     this.listOfDisplayData = this.fetchedLearningMaterialList.filter((item: LearningMaterial) => {
  //       const matchesTitle = item.title?.toLowerCase().includes(searchVal);
  //       const matchesDescription = item.description?.toLowerCase().includes(searchVal);
  //       const matchesLearningOutcomes = item.learningOutcomes?.some(learningOutcome =>
  //         learningOutcome?.toLowerCase().includes(searchVal)
  //       );
  //
  //       if (this.searchField === 0) {
  //         return matchesTitle || matchesDescription || matchesLearningOutcomes;
  //       } else if (this.searchField === 1) {
  //         return matchesTitle;
  //       } else if (this.searchField === 2) {
  //         return matchesDescription;
  //       } else if (this.searchField === 3) {
  //         return matchesLearningOutcomes;
  //       }
  //       return false;
  //     });
  //   }
  //
  //   setTimeout(() => this.loadingService.hide(), 100);
  // }

  search(): void {
    this.applyFilters();
  }

  private applyFilters(): void {
    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || '';

    let filtered = [...this.fetchedLearningMaterialList];

    if (this.selectedDepartmentIds && this.selectedDepartmentIds.length > 0) {
      filtered = filtered.filter(t =>
        t.departments?.some((d: any) => this.selectedDepartmentIds.includes(d.id))
      );
    }

    if (this.selectedCompetenciesIds && this.selectedCompetenciesIds.length > 0) {
      filtered = filtered.filter(t =>
        t.competency?.some((d: any) => this.selectedCompetenciesIds.includes(d.id))
      );
    }

    // Apply text search
    if (searchVal) {
      filtered = filtered.filter((item: LearningMaterial) => {
        const matchesTitle = item.title?.toLowerCase().includes(searchVal);
        const matchesDescription = item.description?.toLowerCase().includes(searchVal);
        const matchesLearningOutcomes = item.learningOutcomes?.some(outcome => outcome.toLowerCase().includes(searchVal) ?? false);

        if (this.searchField === 0) {
          return matchesTitle || matchesDescription || matchesLearningOutcomes;
        } else if (this.searchField === 1) {
          return matchesTitle;
        } else if (this.searchField === 2) {
          return matchesDescription;
        } else if (this.searchField === 3) {
          return matchesLearningOutcomes;
        }
        return false;
      });
    }

    this.listOfDisplayData = filtered;
  }

  resetSearch(): void {
    this.searchValue = '';
    this.search();
  }

  onAddLearningMaterialFormPopup(): void {
    const modalRef = this.modal.create({
      nzTitle: 'Add New Learning Material',
      nzContent: this.addLearningMaterialModalTpl,
      nzWidth: window.innerWidth > 600 ? 600 : '100%',
      nzMaskClosable: false,
      nzWrapClassName: 'my-custom-style',
      nzOkText: 'Submit',
      nzOnOk: async () => {
        return await this.handleSubmitAndClose(); // return the boolean
      },
    });
    modalRef.afterClose.subscribe(() => {
      this.resetForm();
    });
  }

  async handleSubmitAndClose(): Promise<boolean> {
    if (!this.form.valid || !this.learningOutcomesForm.valid) {

      Object.values(this.form.controls).forEach(c => {
        c.markAsDirty();
        c.updateValueAndValidity();
      });

      Object.values(this.learningOutcomesForm.controls).forEach(c => {
        c.markAsDirty();
        c.updateValueAndValidity();
      });

      // Mark documents
      this.learningDocuments.controls.forEach(docGroup => {
        Object.values((docGroup as FormGroup).controls).forEach(c => {
          c.markAsDirty();
          c.updateValueAndValidity();
        });
      });
      return false;
    }

    const learningDocuments: any[] = [];

    for (let i = 0; i < this.learningDocuments.length; i++) {
      const docGroup = this.learningDocuments.at(i) as FormGroup;
      const { title, fileList, fileType } = docGroup.value;

      let fileUrl = docGroup.get('fileUrl')?.value;

      if (fileList && fileList.length > 0) {
        const file = fileList[0]?.originFileObj; // new file
        if (file) {
          // upload new file
          const fileData = new FormData();
          fileData.append('file', file);
          const response = await firstValueFrom(this.learningMaterialService.uploadFile(fileData));
          fileUrl = response.fileUrl;
          docGroup.patchValue({ fileUrl });
        } else {
          // No file selected → maybe user removed it
          fileUrl = null;
          console.log("No file selected → maybe user removed it")
        }
      }

      learningDocuments.push({ title, fileUrl, fileType });
      console.log("document", learningDocuments)
    }

    const outcomes: string[] = this.learningOutcomes
      .map(note => this.learningOutcomesForm.get(note.controlInstance)?.value?.trim())
      .filter(note => note);

    const learningMaterial = {
      ...this.form.value,
      learningOutcomes: outcomes.length > 0 ? outcomes : null,
      learningDocuments
    };

    try {
      await firstValueFrom(this.learningMaterialService.createLearningMaterial(learningMaterial));
      this.fetchLearningMaterialList();
      this.message.success('The learning material has been successfully created.');
      this.onCloseFormModal();
      return true;
    } catch (error) {
      console.error('Failed to create learning material', error);
      this.message.error('Failed to create learning material.');
      return false;
    }
  }

  onEditLearningMaterialFormPopup(learningMaterial: LearningMaterial): void {
    //reset form
    this.form.reset();
    this.learningDocuments.clear();

    this.form.patchValue({
      title: learningMaterial.title,
      description: learningMaterial.description,
      departmentIds: learningMaterial.departments?.map(d => d.id) || [],
      competencyIds: learningMaterial.competency?.map(c => c.id) || [],
      materialType: learningMaterial.materialType,
    });

    //reset outcomes
    this.learningOutcomesForm.reset();
    Object.keys(this.learningOutcomesForm.controls).forEach(key => {
      this.learningOutcomesForm.removeControl(key);
    });
    this.learningOutcomes = [];

    if (learningMaterial.learningOutcomes && learningMaterial.learningOutcomes.length > 0) {
      learningMaterial.learningOutcomes.forEach((note, index) => {
        const controlInstance = `outcomes${index}`;
        this.learningOutcomesForm.addControl(controlInstance, new FormControl(note));
        this.learningOutcomes.push({
          id: index,
          controlInstance
        });
      });
    }

    if (learningMaterial.learningDocuments?.length) {
      learningMaterial.learningDocuments.forEach((doc, index) => {
        this.learningDocuments.push(
          this.formBuilder.group({
            documentId: [doc.documentId],
            title: [doc.title, Validators.required],
            fileUrl: [doc.fileUrl, Validators.required],
            // Convert backend fileUrl → NzUploadFile[] format for <nz-upload>
            fileList: [[this.normalizeUploadFile(doc, true, index)]],
            totalPages: [doc.totalPages],
            totalDuration: [doc.totalDuration],
            fileType: [doc.fileType]
          })
        )
      })
    }

    const modalRef = this.modal.create({
      nzTitle: 'Edit Learning Material',
      nzContent: this.addLearningMaterialModalTpl,
      nzMaskClosable: false,
      nzWidth: window.innerWidth > 600 ? 600 : '100%',
      nzWrapClassName: 'my-custom-style',
      nzOkText: 'Submit',
      nzOnOk: () => this.onUpdateLearningMaterial(learningMaterial.materialId),
    });

    modalRef.afterClose.subscribe(() => {
      this.resetForm();
    });
  }

  async onUpdateLearningMaterial(materialId: number): Promise<boolean> {
    if (!this.form.valid || !this.learningOutcomesForm.valid) {
      Object.values(this.form.controls).forEach(c => {
        c.markAsDirty();
        c.updateValueAndValidity();
      });

      Object.values(this.learningOutcomesForm.controls).forEach(c => {
        c.markAsDirty();
        c.updateValueAndValidity();
      });

      // Mark documents
      this.learningDocuments.controls.forEach(docGroup => {
        Object.values((docGroup as FormGroup).controls).forEach(c => {
          c.markAsDirty();
          c.updateValueAndValidity();
        });
      });
      return false;
    }

    const learningDocuments: any[] = [];

    // Loop through each document form group
    for (let i = 0; i < this.learningDocuments.length; i++) {
      const docGroup = this.learningDocuments.at(i) as FormGroup;
      //payload
      const { documentId, title, fileList, fileType } = docGroup.value;
      console.log("docGroup", docGroup.value);

      let fileUrl = docGroup.get('fileUrl')?.value; // permanent path

      if (fileList && fileList.length > 0) {
        const file = fileList[0]?.originFileObj; // new file
        const isExisting = fileList[0]?.isExisting; // existing file flag
        if (file && !isExisting) {
          // upload new file
          const fileData = new FormData();
          fileData.append('file', file);
          const response = await firstValueFrom(this.learningMaterialService.uploadFile(fileData));
          fileUrl = response.fileUrl;
          docGroup.patchValue({ fileUrl });
        } else if (isExisting) {
          // existing file, keep fileUrl
          fileUrl = docGroup.get('fileUrl')?.value;
        } else {
          // No file selected → maybe user removed it
          fileUrl = null;
        }
      }
      // Push updated/new document info
      learningDocuments.push({ documentId, title, fileUrl, fileType });
    }

    // Process learning outcomes
    const outcomes: string[] = this.learningOutcomes
      .map(note => this.learningOutcomesForm.get(note.controlInstance)?.value?.trim())
      .filter(note => note);

    const learningMaterial = {
      ...this.form.value,
      learningOutcomes: outcomes.length > 0 ? outcomes : null,
      learningDocuments
    };

    try {
      await firstValueFrom(this.learningMaterialService.updateLearningMaterial(materialId, learningMaterial));
      this.fetchLearningMaterialList();
      this.message.success('The learning material has been successfully updated.');
      this.modal.closeAll();
      this.form.reset();
      return true;
    } catch (error) {
      console.error('Failed to update learning material', error);
      this.message.error('Failed to update learning material.');
      return false;
    }
  }

  onDeleteLearningMaterial(materialId: number | undefined): void {
    if (materialId !== undefined) {
      this.modal.confirm({
        nzTitle: 'Are you sure you want to delete this learning material?',
        nzCentered: true,
        nzOnOk: () => this.learningMaterialService.deleteLearningMaterial(materialId).subscribe(() => {

          this.loadList();
          this.modal.closeAll();
          this.message.success('The learning material has been successfully deleted.');
        }),
      });
    } else {
      console.log('Material not found for id:', materialId);
    }
  }

  openDetails(material: LearningMaterial): void {
    this.selectedMaterial = material;
    this.loadEnrolledStaff(material.materialId!);

    if (this.courseDetailDrawerRef) {
      this.courseDetailDrawerRef.close();
    }

    this.courseDetailDrawerRef = this.drawerService.create({
      nzContent: this.materialDetailsTpl,
      nzWidth: window.innerWidth > 768 ? '680px' : '100%',
      nzTitle: undefined,
      nzClosable: false, // Disable default close button
      nzWrapClassName: 'custom-drawer course-detail-drawer'
    });

    this.courseDetailDrawerRef.afterClose.subscribe(() => {
      this.courseDetailDrawerRef = undefined;
    });

  }

  onCloseCourseDetailDrawer(): void {
    this.courseDetailDrawerRef?.close();
  }

  loadEnrolledStaff(materialId: number): void {
    this.isLoadingEnrolledStaff = true;
    this.staffLearningMaterialService.getEnrollmentsByMaterialId(materialId).subscribe({
      next: (enrollments) => {
        this.enrolledStaffList = (enrollments || []).sort((a, b) => (b.progress ?? 0) - (a.progress ?? 0));
        const staffList = this.enrolledStaffList.map(e => e.staffDto).filter(staff => !!staff);
        this.listOfRole = this.buildFilterList(staffList.map(s => s.role?.name ?? 'unknown'));
        this.listOfDepartment = this.buildFilterList(staffList.map(s => s.role?.orgChart.name ?? 'unknown'));
        this.listOfColumns = this.generateColumns();
        this.filteredEnrolledStaffList = [...this.enrolledStaffList];
        this.isLoadingEnrolledStaff = false;
      },
      error: (err) => {
        console.error('Error loading enrolled staff:', err);
        this.message.error('Failed to load enrolled staff');
        this.isLoadingEnrolledStaff = false;
      }
    });
  }

  getStaffProgress(progress: number | undefined): number {
    return progress !== undefined && progress !== null ? Math.round(progress) : 0;
  }

  getStaffDepartment(staff: StaffLearningMaterial): string {
    return staff.staffDto?.role?.orgChart?.name || '-';
  }

  // getStaffCompetency(staff: StaffLearningMaterial): string {
  //   return staff.staffDto?.role?.competency?.name || '-';
  // }


  getStaffRole(staff: StaffLearningMaterial): string {
    return staff.staffDto?.role?.name || '-';
  }

  getStaffName(staff: StaffLearningMaterial): string {
    return staff.staffDto?.name || '-';
  }

  getProgressColor(progress: number): string {
    // Use a vibrant color scheme - magenta/pink for active progress
    if (progress >= 100) {
      return '#52c41a'; // green for completed
    } else {
      return '#eb2f96'; // vibrant magenta/pink for progress (matching the design)
    }
  }


  bulkDeleteLearningMaterial(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedMaterialIds = Array.from(this.setOfCheckedId);

    const selectedMaterials = this.fetchedLearningMaterialList.filter(c => selectedMaterialIds.includes(c.materialId!));

    const confirmCompetency = selectedMaterials
      .map(m => `<li>${m.title}`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmCompetency}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.learningMaterialService.bulkDeleteLearningMaterial(selectedMaterialIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });

  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();
    this.loadOptionList();
    this.fetchLearningMaterialList();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchTitleKey) },
      { id: 2, name: this.translateService.instant(this.searchDescKey) },
      { id: 3, name: this.translateService.instant(this.searchLearningOutcomeKey) },
    ]
  }

  resetForm(): void {
    this.form.reset();
    this.form.patchValue({ fileUrl: '' });
    this.learningOutcomes.forEach(note => {
      this.learningOutcomesForm.removeControl(note.controlInstance);
    });
    this.learningOutcomes = [];
    this.fileList = [];
    this.learningOutcomesForm.reset();

    const documentsArray = this.form.get('learningDocuments') as FormArray;
    while (documentsArray.length !== 0) {
      documentsArray.removeAt(0);
    }

    this.addField();
  }

  generateColumns(): ColumnItem[] {
    return [
      // {
      //   name: 'Staff',
      //   sortOrder: null,
      //   sortFn: (a, b) => a.name.localeCompare(b.name),
      //   sortDirections: ['ascend', 'descend', null],
      //   filterMultiple: true,
      //   isSearchable: true,
      //   listOfFilter: [],
      //   filterFn: null,
      // },
      {
        name: 'Department',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfDepartment,
        filterFn: (dept: string, item: ItemData) => item.staffDto?.role?.orgChart?.name === dept,
      },
      {
        name: 'Role',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        isSearchable: false,
        listOfFilter: this.listOfRole,
        filterFn: (role: string, item: ItemData) => item.staffDto?.role?.name === role,
      },
      {
        name: 'Progress',
        sortOrder: null,
        sortFn: (a, b) => (b.progress ?? 0) - (a.progress ?? 0),
        sortDirections: ['ascend', 'descend'],
        filterMultiple: true,
        isSearchable: true,
        listOfFilter: [],
        filterFn: null,
      },
    ];
  }

  filterStaff(): void {
    const q = this.staffSearch.trim().toLowerCase();
    if (!q) {
      this.filteredEnrolledStaffList = [...this.enrolledStaffList];
      return;
    }
    this.filteredEnrolledStaffList = this.enrolledStaffList.filter((s) => {
      const name = s.staffDto?.name?.toLowerCase() || '';
      return name.includes(q);
    });
  }


  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }

  private extractFileName(fileUrl: string): string {
    // handles both signed Url & plain
    try {
      const url = new URL(fileUrl);
      const pathname = url.pathname;
      return pathname.substring(pathname.lastIndexOf('/' + 1));
    } catch {
      return fileUrl.substring(fileUrl.lastIndexOf('/' + 1));
    }
  }

  private buildFilterList(values: string[]): NzTableFilterList {
    return Array.from(new Set(values)).map(value => ({ text: value, value }));
  }

  // private async extractPdfPageCount(file: NzUploadFile): Promise<number> {
  //   return new Promise((resolve) => {
  //     const reader = new FileReader();
  //     reader.onload = async (e: any) => {
  //       try {
  //         const typedArray = new Uint8Array(e.target.result);
  //         const pdf = await (pdfjsLib as any).getDocument({ data: typedArray }).promise;
  //         resolve(pdf.numPages);
  //       } catch (error) {
  //         console.error('Failed to read PDF page count', error);
  //         resolve(0);
  //       }
  //     };
  //     reader.readAsArrayBuffer(file as any);
  //   });
  // }

  // private async extractPdfPageCount(file: NzUploadFile): Promise<number> {
  //   return new Promise((resolve) => {
  //     const rawFile = file.originFileObj as File;
  //     if (!rawFile) {
  //       console.error('No originFileObj found');
  //       resolve(0);
  //       return;
  //     }
  //
  //     const reader = new FileReader();
  //     reader.onload = async (e: any) => {
  //       try {
  //         const typedArray = new Uint8Array(e.target.result);
  //         const pdf = await (pdfjsLib as any).getDocument({ data: typedArray }).promise;
  //         resolve(pdf.numPages);
  //       } catch (error) {
  //         console.error('Failed to read PDF page count', error);
  //         resolve(0);
  //       }
  //     };
  //     reader.readAsArrayBuffer(rawFile);
  //   });
  // }

  // private async extractPdfPageCount(file: NzUploadFile): Promise<number> {
  //   try {
  //     const arrayBuffer = await (file as any).originFileObj.arrayBuffer();
  //     const pdf = await (pdfjsLib as any).getDocument({ data: arrayBuffer }).promise;
  //     return pdf.numPages;
  //   } catch (error) {
  //     console.error('Failed to read PDF page count', error);
  //     return 0;
  //   }
  // }


  // private extractVideoDuration(file: NzUploadFile): Promise<number> {
  //   return new Promise((resolve) => {
  //     const video = document.createElement('video');
  //     video.preload = 'metadata';
  //     video.onloadedmetadata = () => {
  //       window.URL.revokeObjectURL(video.src);
  //       resolve(Math.round(video.duration));
  //     };
  //     video.onerror = () => resolve(0);
  //     video.src = URL.createObjectURL(file as any);
  //   });
  // }


}




