package com.heuristiq.search.api;

import com.heuristiq.search.api.dto.SearchRequest;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.SearchQuery;
import com.heuristiq.search.scope.TenantScopedSearch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FranchiseGenericSearchDeniedTest {
 @Test void genericRoutesCannotBypassTheModelSpecificGatewayPrivilege(){
  var search=mock(TenantScopedSearch.class);var controller=new SearchController(search);
  assertEquals(403,controller.search(new SearchRequest(new SearchQuery(SearchModel.FRANCHISE_PLAN,null),0,10)).getStatusCode().value());
  assertEquals(403,controller.getOne(SearchModel.FRANCHISE_ADMIN_MEMBER,"m1").getStatusCode().value());
  verifyNoInteractions(search);
 }
}
