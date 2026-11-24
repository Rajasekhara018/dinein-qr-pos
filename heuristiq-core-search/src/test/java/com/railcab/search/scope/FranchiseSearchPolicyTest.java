package com.heuristiq.search.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.*;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FranchiseSearchPolicyTest {
 HttpServer source;FranchiseSearchPolicy policy;String requested;int status=200;
 @BeforeEach void start() throws Exception {
  source=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  source.createContext("/",exchange->{
   requested=exchange.getRequestURI().toString();assertEquals("test-service-token",exchange.getRequestHeaders().getFirst("X-Rider-Service-Token"));
   byte[] body="{\"success\":true,\"responseObject\":{\"owned\":[\"f1\"],\"agent\":[\"foreign-agent-franchise\"]}}".getBytes(StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
  });source.start();
  policy=new FranchiseSearchPolicy(mock(DiscoveryClient.class),"http://127.0.0.1:"+source.getAddress().getPort(),"test-service-token");
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user-a",null,List.of()));
 }
 @AfterEach void stop(){source.stop(0);SecurityContextHolder.clearContext();}
 @Test void clientOrFiltersCannotWidenLiveOwnership() throws Exception {
  var asked=new SearchQuery(SearchModel.FRANCHISE_MEMBER,new SearchGroup(LogicOp.OR,List.of(new SearchCondition("text14",Operator.EQ,"foreign"))));
  var secured=policy.apply(asked,new CallerScope("org-a",false));
  assertEquals(LogicOp.AND,secured.group().logic());assertEquals(2,secured.group().groups().size());
  assertEquals("f1",secured.group().groups().get(1).conditions().getFirst().value());
  assertTrue(requested.contains("tenantId=org-a"));assertTrue(requested.contains("userId=user-a"));
  assertTrue(requested.startsWith("/api/v1/franchise/internal/search-access?"));
  assertFalse(new ObjectMapper().writeValueAsString(secured).contains("foreign-agent-franchise"));
 }
 @Test void sourceOutageIsAnErrorInsteadOfUnrestrictedResults(){
  status=503;
  assertThrows(SearchIndexUnavailableException.class,()->policy.apply(new SearchQuery(SearchModel.FRANCHISE_DRIVER,null),new CallerScope("org-a",false)));
 }
 @Test void pickerQueriesCannotIncludeUnusablePlaces(){
  var query=policy.apply(new SearchQuery(SearchModel.FRANCHISE_PLACE_OPTION,null),new CallerScope("org-a",false));
  assertEquals(new SearchCondition("text8",Operator.EQ,"true"),query.group().conditions().getFirst());
  assertNull(requested);
 }
 @Test void everyPublishedModelHasAnEnumAndAnExplicitOrganizationScope() throws Exception {
  try(var in=getClass().getResourceAsStream("/franchise-search-models.json")){
   var props=TenantScopingProperties.defaults();
   for(var model:new ObjectMapper().readTree(in)){
    String code=model.path("model").asText();assertNotNull(SearchModel.valueOf(code));
    assertEquals(model.path("global").asBoolean()?TenantScoping.NONE:TenantScoping.OWN,props.scopingOf(code));
   }
  }
 }
}
