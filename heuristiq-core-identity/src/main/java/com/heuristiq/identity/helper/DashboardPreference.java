package com.heuristiq.uam.helper;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardPreference {

    private List<String> favoriteModules;

    private List<String> pinnedMenus;

    private List<String> hiddenWidgets;

    private Integer defaultPageSize;

    private String defaultDashboard;
}
