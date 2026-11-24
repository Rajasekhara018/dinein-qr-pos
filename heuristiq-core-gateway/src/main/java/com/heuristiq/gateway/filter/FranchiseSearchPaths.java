package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Model GetAll calls require the same live privilege as the corresponding source list API. */
final class FranchiseSearchPaths {
 private static final Map<String,String> PATHS=load();
 private FranchiseSearchPaths(){}
 private static Map<String,String> load(){
  try(var in=FranchiseSearchPaths.class.getResourceAsStream("/franchise-search-models.json")){
   Map<String,String> result=new LinkedHashMap<>();for(JsonNode row:new ObjectMapper().readTree(in))
    result.put(normalize(row.path("model").asText()),row.path("path").asText().replaceAll("\\{[^}]+}","scope"));
   return Map.copyOf(result);
  }catch(IOException e){throw new IllegalStateException("Cannot load franchise search permissions",e);}
 }
 static String source(String path,HttpMethod method){
  if(method==HttpMethod.GET&&path.equals("/api/v1/franchise/plans/options"))return "/api/v1/franchise/plans";
  if(method==HttpMethod.GET&&path.matches("/api/v1/franchise/(admin/)?[^/]+/members/summary"))return path.substring(0,path.length()-"/summary".length());
  if(method!=HttpMethod.POST||!path.startsWith("/api/v1/search/elastic/")||!path.endsWith("/getall"))return null;
  String model=path.substring("/api/v1/search/elastic/".length(),path.length()-"/getall".length());
  return PATHS.get(normalize(model));
 }
 private static String normalize(String model){return model.replaceAll("[-_\\s]","").toUpperCase(Locale.ROOT);}
}
