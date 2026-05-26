import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TrainingManagementPageComponent} from './training-management/training-management-page';

const routes: Routes = [
  { path: '', component: TrainingManagementPageComponent}
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TrainingRoutingModule {}
