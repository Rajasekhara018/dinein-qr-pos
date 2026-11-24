package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.elastic.ElasticDocument;
import com.heuristiq.search.indexing.SearchDocumentAssembler;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FranchisePayloadTest {
 @Test void structuredListDataSurvivesAssemblyStorageMappingAndGetAll(){
  var row=Map.of("id","p1","fee",Map.of("code","INR","value",new BigDecimal("1234.56")),"agentLimit",5);
  var document=new SearchDocumentAssembler().assemble("FRANCHISE_PLAN","p1",Map.of("ownerKey","org-a","text1","APP","payload",row));
  var restored=ElasticsearchDocumentMapper.toDocument(ElasticsearchDocumentMapper.toStored(document));
  assertEquals("org-a",restored.getTenantId());assertEquals("APP",restored.getText1());
  assertEquals(row,ElasticDocument.of(restored).slots().get("payload"));
 }
}
