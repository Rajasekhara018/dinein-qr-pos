package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.*;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FranchiseGetAllPrivilegesTest {
 @Test void sourceListPrivilegeAuthorizesTheCorrespondingModelPost(){check(Set.of(1060),false,true);}
 @Test void anotherModelPrivilegeCannotAuthorizeThePost(){check(Set.of(1054),false,false);}
 @Test void aMissingSourceCatalogEntryFailsClosed(){check(Set.of(1060),true,false);}
 private void check(Set<Integer> granted,boolean missing,boolean allowed){
  var loader=mock(PrivilegeCatalogLoader.class);var cache=mock(RolePrivilegeCacheService.class);var chain=mock(GatewayFilterChain.class);
  when(loader.load()).thenReturn(missing?List.of():List.of(new PrivilegeEntry(1060,"/api/v1/franchise/plans","franchise","LIST")));
  when(cache.resolvePrivilegeIds(anySet())).thenReturn(granted);
  if(allowed)when(chain.filter(any())).thenReturn(Mono.empty());
  var filter=new PrivilegeBasedAccessFilter(new GatewayProperties(),loader,new PathMatchersConfig(),cache,new ObjectMapper(),Schedulers.immediate());filter.loadCatalog();
  var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/search/elastic/FRANCHISE_PLAN/getall")
   .header(GatewayConstants.X_USER_ROLES,"TEST_ADMIN").build());
  filter.filter(exchange,chain).block();
  if(allowed){verify(chain).filter(any());assertNull(exchange.getResponse().getStatusCode());}
  else{verifyNoInteractions(chain);assertEquals(HttpStatus.FORBIDDEN,exchange.getResponse().getStatusCode());}
 }
 @Test void modelAliasesUseTheSameNormalizationAsTheSearchApi(){
  assertEquals("/api/v1/franchise/plans",FranchiseSearchPaths.source("/api/v1/search/elastic/franchise-plan/getall",HttpMethod.POST));
  assertEquals("/api/v1/franchise/scope/members",FranchiseSearchPaths.source("/api/v1/search/elastic/FRANCHISE_MEMBER/getall",HttpMethod.POST));
  assertNull(FranchiseSearchPaths.source("/api/v1/search/elastic/ROLE/getall",HttpMethod.POST));
 }
 @Test void metadataKeepsTheExistingListPrivilege(){
  assertEquals("/api/v1/franchise/plans",FranchiseSearchPaths.source("/api/v1/franchise/plans/options",HttpMethod.GET));
  assertEquals("/api/v1/franchise/f1/members",FranchiseSearchPaths.source("/api/v1/franchise/f1/members/summary",HttpMethod.GET));
  assertEquals("/api/v1/franchise/admin/f1/members",FranchiseSearchPaths.source("/api/v1/franchise/admin/f1/members/summary",HttpMethod.GET));
 }
}
