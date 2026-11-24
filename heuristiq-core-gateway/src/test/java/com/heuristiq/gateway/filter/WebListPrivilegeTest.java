package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.*;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebListPrivilegeTest {
    private void check(String model, List<PrivilegeEntry> entries, Set<Integer> granted, boolean allowed) {
        check(model,entries,granted,allowed,List.of("PASUPU_KUMKUMA_MATRIMONY_APP"));
    }
    private void check(String model, List<PrivilegeEntry> entries, Set<Integer> granted, boolean allowed, List<String> apps) {
        var loader=mock(PrivilegeCatalogLoader.class);
        var roles=mock(RolePrivilegeCacheService.class);
        var chain=mock(GatewayFilterChain.class);
        when(loader.load()).thenReturn(entries);
        when(roles.resolvePrivilegeIds(anySet())).thenReturn(granted);
        if(allowed) when(chain.filter(any())).thenReturn(Mono.empty());
        var filter=new PrivilegeBasedAccessFilter(new GatewayProperties(),loader,new PathMatchersConfig(),roles,new ObjectMapper(),Schedulers.immediate());
        filter.loadCatalog();
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/search/elastic/"+model+"/getall")
                .header(GatewayConstants.X_USER_ROLES,"CUSTOMER").header("X-User-Apps","PASUPU_KUMKUMA_MATRIMONY_APP").build());
        exchange.getAttributes().put(com.heuristiq.gateway.auth.provider.CallerDetails.APPLICATIONS_ATTRIBUTE,apps);
        filter.filter(exchange,chain).block();
        if(allowed) { verify(chain).filter(any()); assertThat(exchange.getResponse().getStatusCode()).isNull(); }
        else { verifyNoInteractions(chain); assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); }
    }
    @Test void sourceListPermissionAllowsItsElasticModel() {
        check("WEB_ADMIN_DRIVER",List.of(new PrivilegeEntry(19,"/api/v1/admin/drivers","admin","LIST")),Set.of(19),true);
    }
    @Test void genericSearchPermissionDoesNotGrantAnAdminList() {
        check("WEB_ADMIN_DRIVER",List.of(new PrivilegeEntry(19,"/api/v1/admin/drivers","admin","LIST"),
                new PrivilegeEntry(20,"/api/v1/search/elastic/**","search","CREATE")),Set.of(20),false);
    }
    @Test void walletUsesItsActualSourceServiceSlug() {
        check("WEB_WALLET_ACCOUNT",List.of(new PrivilegeEntry(21,"/api/v1/wallet/admin/accounts","rider-wallet","LIST")),Set.of(21),true);
    }
    @Test void missingStaffCatalogEntryFailsClosed() { check("WEB_ADMIN_DRIVER",List.of(),Set.of(),false); }
    @Test void unknownModelsFailClosed() { check("WEB_FAKE",List.of(),Set.of(),false); }
    @Test void personalCustomerListsPreserveAuthenticatedSourceAccess() { check("WEB_MATRIMONY_SELF_PAYMENT",List.of(),Set.of(),true); }
    @Test void aClientApplicationHeaderCannotGrantMatrimonyAccess() {
        check("WEB_MATRIMONY_SELF_PAYMENT",List.of(),Set.of(),false,List.of());
    }
    @Test void aDifferentApplicationCannotReadMatrimonyLists() {
        check("WEB_MATRIMONY_PUBLIC_STORY",List.of(),Set.of(),false,List.of("BESTRIDE_RIDER_APP"));
    }
    @Test void aCustomerListStillHonorsAnExplicitSourcePrivilege() {
        check("WEB_MATRIMONY_SELF_PAYMENT",List.of(new PrivilegeEntry(22,"/api/v1/matrimony/payments/me","matrimony","READ")),Set.of(),false);
    }
    @Test void imsListsUseTheirExactContextPath() {
        check("WEB_IMS_LOCATION",List.of(new PrivilegeEntry(23,"/api/ims/v1/locations","ims","READ")),Set.of(23),true);
    }
    @Test void foodListsUseTheirOwnServiceSlug() {
        check("WEB_FOOD_ORDER",List.of(new PrivilegeEntry(24,"/api/v1/food/admin/orders","food","READ")),Set.of(24),true);
    }
    /** Every food console list, against the REAL catalogue: its resource's READ opens it, nothing else does. */
    @Test void everyFoodListOpensWithItsResourceReadInTheRealCatalog() {
        var catalog=new PrivilegeCatalogLoader(new ObjectMapper()).load();
        var lists=java.util.Map.ofEntries(
                java.util.Map.entry("WEB_FOOD_RESTAURANT","FOOD_RESTAURANT_READ"),java.util.Map.entry("WEB_FOOD_CATEGORY","FOOD_CATEGORY_READ"),
                java.util.Map.entry("WEB_FOOD_FOOD","FOOD_FOOD_READ"),java.util.Map.entry("WEB_FOOD_CUSTOMER","FOOD_CUSTOMER_READ"),
                java.util.Map.entry("WEB_FOOD_ADMIN_USER","FOOD_ADMIN_USER_READ"),java.util.Map.entry("WEB_FOOD_ORDER","FOOD_ORDER_READ"),
                java.util.Map.entry("WEB_FOOD_PAYMENT","FOOD_PAYMENT_READ"),java.util.Map.entry("WEB_FOOD_COUPON","FOOD_COUPON_READ"),
                java.util.Map.entry("WEB_FOOD_OFFER","FOOD_OFFER_READ"),java.util.Map.entry("WEB_FOOD_REFUND","FOOD_REFUND_READ"),
                java.util.Map.entry("WEB_FOOD_REVIEW","FOOD_REVIEW_READ"),java.util.Map.entry("WEB_FOOD_DELIVERY_PARTNER","FOOD_DELIVERY_PARTNER_READ"),
                java.util.Map.entry("WEB_FOOD_SUPPORT_TICKET","FOOD_SUPPORT_TICKET_READ"),java.util.Map.entry("WEB_FOOD_NOTIFICATION","FOOD_NOTIFICATION_READ"),
                java.util.Map.entry("WEB_FOOD_SETTING","FOOD_SETTING_READ"));
        lists.forEach((model,name)->{
            int id=catalog.stream().filter(e->name.equals(e.name())).findFirst().orElseThrow().privilegeId();
            check(model,catalog,Set.of(id),true,List.of());
            check(model,catalog,Set.of(1),false,List.of());
        });
    }
}
