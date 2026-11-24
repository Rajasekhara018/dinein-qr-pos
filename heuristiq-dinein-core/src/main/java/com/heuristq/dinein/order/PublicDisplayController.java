package com.heuristq.dinein.order;

import com.heuristq.dinein.order.dto.OrderDtos.DisplayBoardView;
import com.heuristq.dinein.restaurant.domain.RestaurantRepository;
import com.heuristq.dinein.shared.exception.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing "order ready" screen, meant to run unattended on a display in the dining area -- no login, same
 * as a physical numbers board. {@code restaurantId} is not treated as a secret (any customer display shows only
 * display tokens, never guest names/items/amounts), so it's taken as a plain query parameter with no session.
 */
@RestController
@RequestMapping("/api/v1/public/display")
public class PublicDisplayController {

    private final OrderQueryService queryService;
    private final RestaurantRepository restaurantRepository;

    public PublicDisplayController(OrderQueryService queryService, RestaurantRepository restaurantRepository) {
        this.queryService = queryService;
        this.restaurantRepository = restaurantRepository;
    }

    @GetMapping
    public DisplayBoardView board(@RequestParam Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw ApiException.notFound("Restaurant");
        }
        return queryService.displayBoard(restaurantId);
    }
}
