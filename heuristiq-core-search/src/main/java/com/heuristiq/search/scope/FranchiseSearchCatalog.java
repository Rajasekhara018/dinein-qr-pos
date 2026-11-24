package com.heuristiq.search.scope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Source-generated model metadata; ownership restrictions are independent of optional tenant scoping. */
public final class FranchiseSearchCatalog {
 private static final Map<String,JsonNode> MODELS=load();
 private FranchiseSearchCatalog(){}
 private static Map<String,JsonNode> load(){
  try(var in=FranchiseSearchCatalog.class.getResourceAsStream("/franchise-search-models.json")){
   Map<String,JsonNode> result=new LinkedHashMap<>();for(JsonNode row:new ObjectMapper().readTree(in))result.put(row.path("model").asText(),row);return Map.copyOf(result);
  }catch(IOException e){throw new IllegalStateException("Cannot load franchise search catalog",e);}
 }
 public static boolean owned(String model){JsonNode row=MODELS.get(model);return row!=null&&row.path("owned").asBoolean();}
 public static boolean restricted(String model){return owned(model)||model.startsWith("FRANCHISE_PLACE_OPTION");}
}
