import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatRadioModule } from '@angular/material/radio';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatStepperModule } from '@angular/material/stepper';

/** Every Angular Material module the apps use, imported and exported together. */
const HQ_MATERIAL_MODULES = [
  MatButtonModule, MatCardModule, MatInputModule, MatFormFieldModule,
  MatSelectModule, MatTableModule, MatPaginatorModule, MatSortModule,
  MatDialogModule, MatSnackBarModule, MatIconModule, MatProgressSpinnerModule,
  MatProgressBarModule, MatTabsModule, MatToolbarModule, MatSidenavModule,
  MatListModule, MatMenuModule, MatBadgeModule, MatChipsModule,
  MatDatepickerModule, MatNativeDateModule, MatCheckboxModule, MatRadioModule,
  MatSlideToggleModule, MatTooltipModule, MatExpansionModule, MatDividerModule,
  MatGridListModule, MatAutocompleteModule, MatStepperModule,
];

@NgModule({
  imports: HQ_MATERIAL_MODULES,
  exports: HQ_MATERIAL_MODULES,
})
export class HqMaterialModule {}
