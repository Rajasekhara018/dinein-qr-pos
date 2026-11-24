package com.heuristq.dinein.menu;

import com.heuristq.dinein.menu.dto.MenuAdminDtos.AvailabilityRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.CategoryRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.CategoryResponse;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.ItemRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.ItemResponse;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.PriceRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.ReorderRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.StatusRequest;
import com.heuristq.dinein.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Menu management for OWNER and MANAGER (enforced by the /api/v1/admin/** rule). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminMenuController {

    private final CategoryService categoryService;
    private final ItemService itemService;

    public AdminMenuController(CategoryService categoryService, ItemService itemService) {
        this.categoryService = categoryService;
        this.itemService = itemService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categoryService.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    // Declared before /categories/{id} so "reorder" is never parsed as an id.
    @PatchMapping("/categories/reorder")
    public List<CategoryResponse> reorder(@Valid @RequestBody ReorderRequest request) {
        return categoryService.reorder(request.ids());
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @PatchMapping("/categories/{id}/status")
    public CategoryResponse categoryStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return categoryService.setActive(id, request.active());
    }

    @GetMapping("/items")
    public PageResponse<ItemResponse> items(@RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean available,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return itemService.search(categoryId, q, available, page, size);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse createItem(@Valid @RequestBody ItemRequest request) {
        return itemService.create(request);
    }

    @GetMapping("/items/{id}")
    public ItemResponse item(@PathVariable Long id) {
        return itemService.get(id);
    }

    @PutMapping("/items/{id}")
    public ItemResponse updateItem(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return itemService.update(id, request);
    }

    @PatchMapping("/items/{id}/availability")
    public ItemResponse availability(@PathVariable Long id, @Valid @RequestBody AvailabilityRequest request) {
        return itemService.setAvailability(id, request.available());
    }

    @PatchMapping("/items/{id}/price")
    public ItemResponse price(@PathVariable Long id, @Valid @RequestBody PriceRequest request) {
        return itemService.updatePrice(id, request);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        itemService.delete(id);
    }
}
