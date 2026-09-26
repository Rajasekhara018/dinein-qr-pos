import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CategoriesPage } from './categories/categories-page';
import { ItemFormPage } from './item-form/item-form-page';
import { ItemsPage } from './items/items-page';

const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'items' },
  { path: 'categories', component: CategoriesPage, title: 'Categories · DineIn admin' },
  { path: 'items', component: ItemsPage, title: 'Items · DineIn admin' },
  { path: 'items/new', component: ItemFormPage, title: 'New item · DineIn admin' },
  { path: 'items/:id', component: ItemFormPage, title: 'Edit item · DineIn admin' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CatalogRoutingModule {}
