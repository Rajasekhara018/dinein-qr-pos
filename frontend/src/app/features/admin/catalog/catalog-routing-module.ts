import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CategoriesPage } from './categories/categories-page';
import { ItemFormPage } from './item-form/item-form-page';
import { ItemsPage } from './items/items-page';

const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'items' },
  {
    path: 'categories',
    component: CategoriesPage,
    title: 'Categories · DineIn admin',
    data: { breadcrumb: 'Categories' },
  },
  { path: 'items', component: ItemsPage, title: 'Items · DineIn admin', data: { breadcrumb: 'Items' } },
  {
    path: 'items/new',
    component: ItemFormPage,
    title: 'New item · DineIn admin',
    data: { breadcrumb: 'New item' },
  },
  {
    path: 'items/:id',
    component: ItemFormPage,
    title: 'Edit item · DineIn admin',
    data: { breadcrumb: 'Edit item' },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CatalogRoutingModule {}
