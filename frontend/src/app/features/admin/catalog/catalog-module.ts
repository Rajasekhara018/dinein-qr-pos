import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { CatalogRoutingModule } from './catalog-routing-module';
import { CategoriesPage } from './categories/categories-page';
import { CategoryDialog } from './categories/category-dialog';
import { ItemFormPage } from './item-form/item-form-page';
import { ItemPreviewCard } from './item-form/item-preview-card';
import { ItemsPage } from './items/items-page';
import { PriceEditor } from './items/price-editor';

/** Menu management (`/admin/menu/**`): categories and items. Lazily loaded. */
@NgModule({
  declarations: [CategoriesPage, CategoryDialog, ItemsPage, PriceEditor, ItemFormPage, ItemPreviewCard],
  imports: [AdminSharedModule, CatalogRoutingModule],
})
export class CatalogModule {}
