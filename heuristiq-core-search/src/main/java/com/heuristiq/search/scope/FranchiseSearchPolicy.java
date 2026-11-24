package com.heuristiq.search.scope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.search.query.*;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Live owner authorization, so an indexed membership never grants access after ownership is revoked. */
@Component
public class FranchiseSearchPolicy {
 private final DiscoveryClient discovery;
 private final String base,token;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public FranchiseSearchPolicy(DiscoveryClient discovery,
   @Value("${railcab.search.franchise.base-url:}") String base,
   @Value("${rider.internal.service-token:${RIDER_INTERNAL_SERVICE_TOKEN:}}") String token){this.discovery=discovery;this.base=base;this.token=token;}
 public SearchQuery apply(SearchQuery query,CallerScope caller){
  if(query.model().name().startsWith("FRANCHISE_PLACE_OPTION")){
   SearchGroup usable=new SearchGroup(LogicOp.AND,List.of(new SearchCondition("text8",Operator.EQ,"true")));
   return new SearchQuery(query.model(),query.group()==null?usable:new SearchGroup(LogicOp.AND,List.of(),List.of(query.group(),usable)));
  }
  if(!FranchiseSearchCatalog.owned(query.model().name())||caller.internal())return query;
  if(!caller.hasTenant())throw new TenantRequiredException("An organization is required for franchise search");
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth==null||token==null||token.isBlank())throw unavailable(null);
  try{
   URI endpoint;
   if(base!=null&&!base.isBlank())endpoint=URI.create(base);
   else{
    var instances=discovery.getInstances("RealBoom-FranchiseService");
    if(instances.isEmpty())throw unavailable(null);endpoint=instances.getFirst().getUri();
   }
   String path="/api/v1/franchise/internal/search-access?tenantId="+encode(caller.tenantId())+"&userId="+encode(auth.getName());
   var response=http.send(HttpRequest.newBuilder(URI.create(endpoint.toString().replaceAll("/+$","")+path))
    .header("X-Rider-Service-Token",token).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
   JsonNode result=new ObjectMapper().readTree(response.body());
   if(response.statusCode()!=200||!result.path("success").asBoolean()||!result.path("responseObject").path("owned").isArray())throw unavailable(null);
   List<String> ids=new ArrayList<>();for(JsonNode id:result.path("responseObject").path("owned")){
    String value=id.asText();if(value.isBlank()||value.contains(","))throw unavailable(null);ids.add(value);
   }
   // No owned franchises means a deliberately impossible match, never an unrestricted query.
   SearchGroup ownership=new SearchGroup(LogicOp.AND,List.of(new SearchCondition("text14",Operator.IN,
    ids.isEmpty()?"__NO_OWNED_FRANCHISE__":String.join(",",ids))));
   return new SearchQuery(query.model(),query.group()==null?ownership:new SearchGroup(LogicOp.AND,List.of(),List.of(query.group(),ownership)));
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable(e);}
   catch(java.io.IOException|IllegalArgumentException e){throw unavailable(e);}
 }
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
 private static SearchIndexUnavailableException unavailable(Throwable e){return new SearchIndexUnavailableException("Franchise ownership could not be verified",e);}
}
